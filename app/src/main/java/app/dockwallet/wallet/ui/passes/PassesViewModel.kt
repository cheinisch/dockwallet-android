package app.dockwallet.wallet.ui.passes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.data.repository.DockWalletRepository
import app.dockwallet.wallet.data.repository.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class PassesUiState(
    val passes: List<PassEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val syncMessage: String? = null,   // NEU: Sync-Ergebnis anzeigen
    val isLocalMode: Boolean = false
)

class PassesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockWalletRepository(application)

    private val _uiState = MutableStateFlow(PassesUiState())
    val uiState: StateFlow<PassesUiState> = _uiState

    init {
        repository.getAllPasses()
            .onEach { passes ->
                _uiState.value = _uiState.value.copy(passes = passes)
            }
            .launchIn(viewModelScope)

        _uiState.value = _uiState.value.copy(isLocalMode = repository.isLocalMode())

        if (!repository.isLocalMode()) {
            sync()
        }
    }

    fun sync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null, syncMessage = null)
            when (val result = repository.syncFromServer()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isSyncing = false)
                is ApiResult.Error   -> _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    error = result.message
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun logout() = repository.logout()
}