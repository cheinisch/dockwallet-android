package app.dockwallet.wallet.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore instance for the whole app
internal val Context.appDataStore by preferencesDataStore(name = "settings")

object ThemePreference {
    private val KEY = stringPreferencesKey("theme_mode")

    fun get(context: Context): Flow<ThemeMode> =
        context.appDataStore.data.map { prefs ->
            when (prefs[KEY]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK"  -> ThemeMode.DARK
                else    -> ThemeMode.SYSTEM
            }
        }

    suspend fun set(context: Context, mode: ThemeMode) {
        context.appDataStore.edit { it[KEY] = mode.name }
    }
}