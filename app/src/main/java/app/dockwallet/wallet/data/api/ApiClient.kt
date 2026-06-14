package app.dockwallet.wallet.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TokenStore {
    private const val PREF_FILE = "dockwallet_secure"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_SERVER_URL = "server_url"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_TOKEN, token).apply()

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun clearToken(context: Context) =
        prefs(context).edit().remove(KEY_TOKEN).apply()

    fun getServerUrl(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, "https://dockwallet.app/api/v1/")
            ?: "https://dockwallet.app/api/v1/"

    fun saveServerUrl(context: Context, url: String) =
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()
}

object ApiClient {
    fun create(baseUrl: String): DockWalletApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DockWalletApi::class.java)
    }
}