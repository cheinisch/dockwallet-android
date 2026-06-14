package app.dockwallet.wallet.data.repository

import android.content.Context
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.BoardingPassEntity
import app.dockwallet.wallet.data.api.ApiClient
import app.dockwallet.wallet.data.api.TokenStore
import kotlinx.coroutines.flow.Flow

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class DockWalletRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).boardingPassDao()
    private fun api() = ApiClient.create(TokenStore.getServerUrl(context))
    private fun bearerToken() = "Bearer ${TokenStore.getToken(context)}"

    // Lokaler Stream — UI beobachtet diesen
    fun getAllPasses(): Flow<List<BoardingPassEntity>> = dao.getAllPasses()

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
                    BoardingPassEntity(
                        id = it.id,
                        passengerName = it.passenger_name,
                        flightNumber = it.flight_number,
                        origin = it.origin,
                        destination = it.destination,
                        departureTime = it.departure_time,
                        seat = it.seat,
                        gate = it.gate,
                        bookingReference = it.booking_reference,
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

    suspend fun saveLocalPass(pass: BoardingPassEntity): Result<Unit> {
        return try {
            dao.insert(pass)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Speichern fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    suspend fun deletePass(pass: BoardingPassEntity): Result<Unit> {
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