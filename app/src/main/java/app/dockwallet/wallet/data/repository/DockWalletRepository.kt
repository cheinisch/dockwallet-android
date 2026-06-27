package app.dockwallet.wallet.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.data.api.ApiClient
import app.dockwallet.wallet.data.api.DockWalletApi
import app.dockwallet.wallet.data.api.Pass
import app.dockwallet.wallet.data.api.PushPassRequest
import app.dockwallet.wallet.data.api.RegisterDeviceRequest
import app.dockwallet.wallet.data.api.SyncDevice
import app.dockwallet.wallet.data.api.TokenStore
import kotlinx.coroutines.flow.Flow
import java.io.File

private const val TAG = "DockWalletRepository"

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

data class SyncSummary(val pulled: Int, val pushed: Int, val deleted: Int)

class DockWalletRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).passDao()
    private fun api(): DockWalletApi = ApiClient.create(TokenStore.getServerUrl(context))
    private fun bearer() = "Bearer ${TokenStore.getToken(context)}"

    fun getAllPasses(): Flow<List<PassEntity>> = dao.getAllPasses()
    fun isLocalMode() = TokenStore.isLocalMode(context)
    fun isLoggedIn() = TokenStore.getToken(context) != null

    // ── Login ─────────────────────────────────────────────────────────────────

    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val response = api().login(mapOf("username" to username, "password" to password))
            if (response.isSuccessful) {
                val token = response.body()?.access_token
                    ?: return Result.Error("Kein Token erhalten")
                TokenStore.saveToken(context, token)
                // Gerät direkt nach Login registrieren
                registerDevice()
                Result.Success(Unit)
            } else {
                when (response.code()) {
                    401 -> Result.Error("Benutzername oder Passwort falsch")
                    else -> Result.Error("Fehler: ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Result.Error("Verbindung fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    fun logout() {
        TokenStore.clearToken(context)
        TokenStore.saveServerUrl(context, "local")
    }

    // ── Gerät registrieren ────────────────────────────────────────────────────

    suspend fun registerDevice(): Result<Unit> {
        return try {
            val deviceName = TokenStore.getDeviceName(context)
            val response = api().registerDevice(bearer(), RegisterDeviceRequest(deviceName))
            if (response.isSuccessful) Result.Success(Unit)
            else Result.Error("Geräteregistrierung fehlgeschlagen: ${response.code()}")
        } catch (e: Exception) {
            Log.w(TAG, "registerDevice failed (non-critical)", e)
            Result.Success(Unit) // Non-critical — Sync läuft auch ohne
        }
    }

    // ── Bidirektionaler Sync ──────────────────────────────────────────────────

    suspend fun sync(): Result<SyncSummary> {
        if (isLocalMode()) return Result.Error("Kein Server verbunden")

        return try {
            var pushed = 0
            var pulled = 0
            var deleted = 0

            // ── 1. Pull zuerst holen (brauchen wir für Barcode-Abgleich) ─────
            val since = TokenStore.getLastSyncTime(context)
            val pullResponse = api().syncPull(bearer(), since = since)

            if (!pullResponse.isSuccessful) {
                return Result.Error("Pull fehlgeschlagen: ${pullResponse.code()}")
            }

            val syncData = pullResponse.body()!!

            // Barcode → Server-ID Map für Duplikat-Erkennung
            val serverBarcodeMap = syncData.passes
                .filter { it.barcode != null }
                .associate { it.barcode!! to it.id }

            // ── 2. Push: lokale Pässe ohne serverId ───────────────────────────
            val localOnly = dao.getLocalOnlyPasses()
            for (local in localOnly) {
                try {
                    // Prüfen ob Pass mit gleichem Barcode bereits auf Server existiert
                    val existingServerId = local.barcode?.let { serverBarcodeMap[it] }
                    if (existingServerId != null) {
                        // Duplikat: nur verknüpfen, nicht hochladen
                        dao.linkToServer(local.id, existingServerId)
                        Log.d(TAG, "Duplikat erkannt via Barcode, verknüpft: ${local.id} → $existingServerId")
                    } else {
                        // Neu: auf Server hochladen
                        val file = local.localFilePath?.let { File(it) }
                        if (file != null && file.exists()) {
                            val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            val response = api().syncPush(bearer(), PushPassRequest(file = b64))
                            if (response.isSuccessful) {
                                val serverPass = response.body()!!
                                dao.linkToServer(local.id, serverPass.id)
                                pushed++
                            } else {
                                Log.w(TAG, "Push fehlgeschlagen für ${local.id}: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Push error für ${local.id}", e)
                }
            }
            // ── 3. Pull-Ergebnisse in Room einspielen ─────────────────────────
            val knownServerIds = dao.getAllServerIds().toSet()

            for (serverPass in syncData.passes) {
                if (serverPass.id in knownServerIds) {
                    // Update bestehenden Eintrag
                    dao.updateByServerId(
                        serverId = serverPass.id,
                        passType = serverPass.pass_type,
                        passengerName = serverPass.passenger_name,
                        flightNumber = serverPass.flight_number,
                        origin = serverPass.origin,
                        destination = serverPass.destination,
                        departureTime = serverPass.departure_time,
                        arrivalTime = serverPass.arrival_time,
                        eventDate = serverPass.event_date,
                        seat = serverPass.seat,
                        bookingReference = serverPass.booking_reference,
                        barcode = serverPass.barcode,
                        subtitle = serverPass.subtitle,
                        logoText = serverPass.logo_text,
                        colorBackground = serverPass.color_background,
                        colorForeground = serverPass.color_foreground,
                        colorLabel = serverPass.color_label,
                        isVoided = serverPass.is_voided,
                        signatureValid = serverPass.signature_valid,
                        updatedAt = serverPass.updated_at,
                    )
                } else {
                    // Neu einfügen
                    dao.insert(serverPass.toEntity())
                }
                pulled++
            }

            // ── 3. Beim ersten Full-Sync: gelöschte Server-Pässe aufräumen ────
            if (since == null) {
                val serverIdSet = syncData.passes.map { it.id }.toSet()
                val toDelete = dao.getAllServerIds().filter { it !in serverIdSet }
                for (serverId in toDelete) {
                    dao.deleteByServerId(serverId)
                    deleted++
                }
            }

            // ── 4. Timestamp speichern ────────────────────────────────────────
            TokenStore.saveLastSyncTime(context, syncData.server_time)

            Result.Success(SyncSummary(pulled = pulled, pushed = pushed, deleted = deleted))
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.Error(e.message ?: "Unbekannter Fehler")
        }
    }

    // Alter Name, leitet jetzt auf sync() weiter (für Kompatibilität mit PassesViewModel)
    suspend fun syncFromServer(): Result<Unit> {
        return when (val r = sync()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> Result.Error(r.message)
        }
    }

    // ── Pass lokal speichern ──────────────────────────────────────────────────

    suspend fun saveLocalPass(pass: PassEntity): Result<Unit> {
        return try {
            dao.insert(pass)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Speichern fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    // ── Pass löschen (lokal + Server) ────────────────────────────────────────

    suspend fun deletePass(pass: PassEntity): Result<Unit> {
        return try {
            // Wenn der Pass auf dem Server liegt → dort auch löschen
            pass.serverId?.let { serverId ->
                try {
                    api().syncDelete(bearer(), serverId)
                } catch (e: Exception) {
                    Log.w(TAG, "Server-Delete fehlgeschlagen für $serverId", e)
                    // Lokal trotzdem löschen
                }
            }
            dao.delete(pass)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Löschen fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    // ── Geräte abrufen ────────────────────────────────────────────────────────

    suspend fun getSyncDevices(): Result<List<SyncDevice>> {
        return try {
            val response = api().syncDevices(bearer())
            if (response.isSuccessful) Result.Success(response.body() ?: emptyList())
            else Result.Error("HTTP ${response.code()}")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Fehler")
        }
    }

    suspend fun removeSyncDevice(deviceId: String): Result<Unit> {
        return try {
            val response = api().removeSyncDevice(bearer(), deviceId)
            if (response.isSuccessful) Result.Success(Unit)
            else Result.Error("HTTP ${response.code()}")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Fehler")
        }
    }

    // ── Mapping: Pass (API) → PassEntity (Room) ───────────────────────────────

    private fun Pass.toEntity() = PassEntity(
        serverId = id,
        isLocal = false,
        passType = pass_type,
        passengerName = passenger_name,
        flightNumber = flight_number,
        origin = origin,
        destination = destination,
        departureTime = departure_time,
        arrivalTime = arrival_time,
        eventDate = event_date,
        seat = seat,
        gate = gate,
        bookingReference = booking_reference,
        barcode = barcode,
        subtitle = subtitle,
        logoText = logo_text,
        colorBackground = color_background,
        colorForeground = color_foreground,
        colorLabel = color_label,
        isVoided = is_voided,
        signatureValid = signature_valid,
        updatedAt = updated_at,
        createdAt = created_at,
    )
}