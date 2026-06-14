package app.dockwallet.wallet.data.repository

import android.content.Context
import app.dockwallet.wallet.data.api.ApiClient
import app.dockwallet.wallet.data.api.BoardingPass
import app.dockwallet.wallet.data.api.TokenStore

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class DockWalletRepository(private val context: Context) {

    private fun api() = ApiClient.create(TokenStore.getServerUrl(context))
    private fun bearerToken() = "Bearer ${TokenStore.getToken(context)}"

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

    suspend fun getPasses(): Result<List<BoardingPass>> {
        return try {
            val response = api().getPasses(bearerToken())
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Fehler beim Laden: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Verbindung fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    fun logout() = TokenStore.clearToken(context)
    fun isLoggedIn() = TokenStore.getToken(context) != null
}