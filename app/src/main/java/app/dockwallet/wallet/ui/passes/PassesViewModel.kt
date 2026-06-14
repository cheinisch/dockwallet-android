package app.dockwallet.wallet.ui.passes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dockwallet.wallet.data.BoardingPassEntity
import app.dockwallet.wallet.data.repository.DockWalletRepository
import app.dockwallet.wallet.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class PassesUiState(
    val passes: List<BoardingPassEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val isLocalMode: Boolean = false
)

class PassesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockWalletRepository(application)

    private val _uiState = MutableStateFlow(PassesUiState())
    val uiState: StateFlow<PassesUiState> = _uiState

    init {
        // Datenbank beobachten — UI aktualisiert sich automatisch
        repository.getAllPasses()
            .onEach { passes ->
                _uiState.value = _uiState.value.copy(passes = passes)
            }
            .launchIn(viewModelScope)

        _uiState.value = _uiState.value.copy(
            isLocalMode = repository.isLocalMode()
        )

        // Beim Start einmal vom Server laden
        if (!repository.isLocalMode()) {
            sync()
        }
    }

    fun sync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
            when (val result = repository.syncFromServer()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isSyncing = false
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    error = result.message
                )
            }
        }
    }

    fun logout() = repository.logout()
}