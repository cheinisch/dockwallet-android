package app.dockwallet.wallet.ui.passes

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dockwallet.wallet.ui.theme.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class PassLayout { LIST, GRID, STACK }

object LayoutPreference {
    private val KEY = stringPreferencesKey("pass_layout")

    fun get(context: Context): Flow<PassLayout> =
        context.appDataStore.data.map { prefs ->
            when (prefs[KEY]) {
                "GRID"  -> PassLayout.GRID
                "STACK" -> PassLayout.STACK
                else    -> PassLayout.LIST
            }
        }

    suspend fun set(context: Context, layout: PassLayout) {
        context.appDataStore.edit { it[KEY] = layout.name }
    }
}