package app.dockwallet.wallet.data.api

import android.content.Context
import android.os.Build
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
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_LAST_SYNC = "last_sync_time"      // NEU: für Delta-Sync

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
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_LAST_SYNC).apply()

    fun getServerUrl(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, "local") ?: "local"

    fun saveServerUrl(context: Context, url: String) =
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()

    fun saveOnboardingDone(context: Context) =
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    fun isOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun isLocalMode(context: Context): Boolean =
        getToken(context) == null || getServerUrl(context) == "local"

    // ── Delta-Sync Timestamp ──────────────────────────────────────────────────

    fun getLastSyncTime(context: Context): String? =
        prefs(context).getString(KEY_LAST_SYNC, null)

    fun saveLastSyncTime(context: Context, time: String) =
        prefs(context).edit().putString(KEY_LAST_SYNC, time).apply()

    // ── Gerätename ────────────────────────────────────────────────────────────

    fun getDeviceName(context: Context): String =
        Build.MODEL ?: "Android"
}

object ApiClient {
    fun create(baseUrl: String): DockWalletApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DockWalletApi::class.java)
    }
}