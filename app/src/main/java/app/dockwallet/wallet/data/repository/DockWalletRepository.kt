package app.dockwallet.wallet.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.data.api.*
import app.dockwallet.wallet.data.api.TokenStore
import kotlinx.coroutines.flow.Flow
import java.io.File

private const val TAG = "DockWalletRepository"

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
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

    suspend fun login(username: String, password: String): ApiResult<Unit> {
        return try {
            val response = api().login(mapOf("username" to username, "password" to password))
            if (response.isSuccessful) {
                val token = response.body()?.token ?: return ApiResult.Error("Kein Token erhalten")
                TokenStore.saveToken(context, token)
                registerDevice()
                ApiResult.Success(Unit)
            } else {
                when (response.code()) {
                    401  -> ApiResult.Error("Benutzername oder Passwort falsch")
                    else -> ApiResult.Error("Fehler: ${response.code()}")
                }
            }
        } catch (e: Exception) {
            ApiResult.Error("Verbindung fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    fun logout() {
        TokenStore.clearToken(context)
        TokenStore.saveServerUrl(context, "local")
    }

    // ── Gerät registrieren ────────────────────────────────────────────────────

    suspend fun registerDevice(): ApiResult<Unit> {
        return try {
            val deviceName = TokenStore.getDeviceName(context)
            val response = api().registerDevice(bearer(), RegisterDeviceRequest(deviceName))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Geraeteregistrierung fehlgeschlagen: ${response.code()}")
        } catch (e: Exception) {
            Log.w(TAG, "registerDevice failed (non-critical)", e)
            ApiResult.Success(Unit)
        }
    }

    // ── Bidirektionaler Sync ──────────────────────────────────────────────────

    suspend fun sync(): ApiResult<SyncSummary> {
        if (isLocalMode()) return ApiResult.Error("Kein Server verbunden")

        return try {
            var pushed = 0; var pulled = 0; var deleted = 0

            // ── 1. Favorit-Änderungen zuerst pushen ───────────────────────────
            val pendingFavorites = dao.getPendingFavoriteChanges()
            for (pass in pendingFavorites) {
                val serverId = pass.serverId ?: continue
                try {
                    val response = api().setFavorite(
                        bearer(), serverId, SetFavoriteRequest(pass.isFavorite)
                    )
                    if (response.isSuccessful) {
                        dao.clearFavoriteChanged(serverId)
                        Log.d(TAG, "Favorit gesynct: $serverId = ${pass.isFavorite}")
                    } else {
                        Log.w(TAG, "Favorit-Sync fehlgeschlagen: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Favorit-Sync Fehler für $serverId", e)
                    // Nicht abbrechen — Rest des Syncs weitermachen
                }
            }

            // ── 2. Pull ───────────────────────────────────────────────────────
            val since = TokenStore.getLastSyncTime(context)
            val pullResponse = api().syncPull(bearer(), since = since)
            if (!pullResponse.isSuccessful)
                return ApiResult.Error("Pull fehlgeschlagen: ${pullResponse.code()}")

            val syncData = pullResponse.body()!!
            val serverBarcodeMap = syncData.passes
                .filter { it.barcode != null }
                .associate { it.barcode!! to it.id }
            val knownServerIds = dao.getAllServerIds().toMutableSet()

            // ── 3. Push: lokale Pässe ohne serverId ───────────────────────────
            val localOnly = dao.getLocalOnlyPasses()
            for (local in localOnly) {
                try {
                    val existingServerId = local.barcode?.let { serverBarcodeMap[it] }
                    if (existingServerId != null) {
                        dao.linkToServer(local.id, existingServerId)
                        knownServerIds.add(existingServerId)
                    } else {
                        val file = local.localFilePath?.let { File(it) }
                        if (file != null && file.exists()) {
                            val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            val response = api().syncPush(bearer(), PushPassRequest(file = b64))
                            if (response.isSuccessful) {
                                val serverPass = response.body()!!
                                dao.linkToServer(local.id, serverPass.id)
                                knownServerIds.add(serverPass.id)
                                pushed++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Push error für ${local.id}", e)
                }
            }

            // ── 4. Pull-Ergebnisse in Room einspielen ─────────────────────────
            for (serverPass in syncData.passes) {
                if (serverPass.id in knownServerIds) {
                    dao.updateByServerId(
                        serverId       = serverPass.id,
                        passType       = serverPass.pass_type ?: "generic",
                        passengerName  = serverPass.passenger_name,
                        flightNumber   = serverPass.flight_number,
                        origin         = serverPass.origin,
                        destination    = serverPass.destination,
                        departureTime  = serverPass.departure_time,
                        arrivalTime    = serverPass.arrival_time,
                        eventDate      = serverPass.event_date,
                        seat           = serverPass.seat,
                        bookingReference = serverPass.booking_reference,
                        barcode        = serverPass.barcode,
                        subtitle       = serverPass.subtitle,
                        logoText       = serverPass.logo_text,
                        colorBackground = serverPass.color_background,
                        colorForeground = serverPass.color_foreground,
                        colorLabel     = serverPass.color_label,
                        isVoided       = serverPass.is_voided,
                        signatureValid = serverPass.signature_valid,
                        updatedAt      = serverPass.updated_at,
                    )
                    // Favorit vom Server übernehmen (nur wenn kein lokaler Pending-Change)
                    dao.updateFavoriteFromServer(serverPass.id, serverPass.is_favorite)
                } else {
                    dao.insert(serverPass.toEntity())
                }
                pulled++
            }

            // ── 5. Beim ersten Full-Sync: veraltete Pässe aufräumen ───────────
            if (since == null) {
                val serverIdSet = syncData.passes.map { it.id }.toSet()
                val toDelete = dao.getAllServerIds().filter { it !in serverIdSet }
                for (serverId in toDelete) {
                    dao.deleteByServerId(serverId)
                    deleted++
                }
            }

            // ── 6. Timestamp speichern ────────────────────────────────────────
            TokenStore.saveLastSyncTime(context, syncData.server_time)

            ApiResult.Success(SyncSummary(pulled = pulled, pushed = pushed, deleted = deleted))
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            ApiResult.Error(e.message ?: "Unbekannter Fehler")
        }
    }

    suspend fun syncFromServer(): ApiResult<Unit> =
        when (val r = sync()) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error   -> ApiResult.Error(r.message)
        }

    // ── Pass lokal speichern ──────────────────────────────────────────────────

    suspend fun saveLocalPass(pass: PassEntity): ApiResult<Unit> {
        return try {
            dao.insert(pass)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error("Speichern fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    // ── Pass löschen (lokal + Server) ────────────────────────────────────────

    suspend fun deletePass(pass: PassEntity): ApiResult<Unit> {
        return try {
            pass.serverId?.let { serverId ->
                try { api().syncDelete(bearer(), serverId) }
                catch (e: Exception) { Log.w(TAG, "Server-Delete fehlgeschlagen für $serverId", e) }
            }
            dao.delete(pass)
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error("Löschen fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    // ── Geräte abrufen ────────────────────────────────────────────────────────

    suspend fun getSyncDevices(): ApiResult<List<SyncDevice>> {
        return try {
            val response = api().syncDevices(bearer())
            if (response.isSuccessful) ApiResult.Success(response.body() ?: emptyList())
            else ApiResult.Error("HTTP ${response.code()}")
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Fehler")
        }
    }

    suspend fun removeSyncDevice(deviceId: String): ApiResult<Unit> {
        return try {
            val response = api().removeSyncDevice(bearer(), deviceId)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("HTTP ${response.code()}")
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Fehler")
        }
    }

    // ── Mapping: Pass (API) → PassEntity (Room) ───────────────────────────────

    private fun Pass.toEntity() = PassEntity(
        serverId        = id,
        isLocal         = false,
        passType        = pass_type ?: "generic",
        passengerName   = passenger_name,
        flightNumber    = flight_number,
        origin          = origin,
        destination     = destination,
        departureTime   = departure_time,
        arrivalTime     = arrival_time,
        eventDate       = event_date,
        seat            = seat,
        gate            = gate,
        bookingReference = booking_reference,
        barcode         = barcode,
        subtitle        = subtitle,
        logoText        = logo_text,
        colorBackground = color_background,
        colorForeground = color_foreground,
        colorLabel      = color_label,
        isVoided        = is_voided,
        signatureValid  = signature_valid,
        updatedAt       = updated_at,
        createdAt       = created_at,
        isFavorite      = is_favorite,
        favoriteChangedLocally = false,
    )
}