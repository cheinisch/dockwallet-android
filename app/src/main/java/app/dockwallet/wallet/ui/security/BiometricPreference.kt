package app.dockwallet.wallet.ui.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.dockwallet.wallet.ui.theme.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object BiometricPreference {
    private val KEY = booleanPreferencesKey("biometric_lock")

    fun isEnabled(context: Context): Flow<Boolean> =
        context.appDataStore.data.map { it[KEY] ?: false }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.appDataStore.edit { it[KEY] = enabled }
    }
}