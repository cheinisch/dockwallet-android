package app.dockwallet.wallet.data.repository

import android.content.Context
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.data.api.ApiClient
import app.dockwallet.wallet.data.api.TokenStore
import kotlinx.coroutines.flow.Flow

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class DockWalletRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).passDao()
    private fun api() = ApiClient.create(TokenStore.getServerUrl(context))
    private fun bearerToken() = "Bearer ${TokenStore.getToken(context)}"

    fun getAllPasses(): Flow<List<PassEntity>> = dao.getAllPasses()

    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val response = api().login(username, password)
            if (response.isSuccessful) {
                val token = response.body()?.access_token
                    ?: return Result.Error("Kein Token erhalten")
                TokenStore.saveToken(context, token)
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

    suspend fun syncFromServer(): Result<Unit> {
        return try {
            val response = api().getPasses(bearerToken())
            if (response.isSuccessful) {
                val passes = response.body() ?: emptyList()
                val entities = passes.map {
                    PassEntity(
                        id = it.id,
                        passType = it.pass_type,
                        passengerName = it.passenger_name,
                        flightNumber = it.flight_number,
                        origin = it.origin,
                        destination = it.destination,
                        departureTime = it.departure_time,
                        arrivalTime = it.arrival_time,
                        eventDate = it.event_date,
                        seat = it.seat,
                        gate = it.gate,
                        bookingReference = it.booking_reference,
                        barcode = it.barcode,
                        subtitle = it.subtitle,
                        logoText = it.logo_text,
                        colorBackground = it.color_background,
                        colorForeground = it.color_foreground,
                        colorLabel = it.color_label,
                        isVoided = it.is_voided,
                        signatureValid = it.signature_valid,
                        isLocal = false
                    )
                }
                dao.deleteAllServerPasses()
                dao.insertAll(entities)
                Result.Success(Unit)
            } else {
                Result.Error("Sync fehlgeschlagen: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Sync fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    suspend fun saveLocalPass(pass: PassEntity): Result<Unit> {
        return try {
            dao.insert(pass)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Speichern fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    suspend fun deletePass(pass: PassEntity): Result<Unit> {
        return try {
            dao.delete(pass)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Löschen fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    fun logout() = TokenStore.clearToken(context)
    fun isLoggedIn() = TokenStore.getToken(context) != null
    fun isLocalMode() = TokenStore.getServerUrl(context) == "local"
}