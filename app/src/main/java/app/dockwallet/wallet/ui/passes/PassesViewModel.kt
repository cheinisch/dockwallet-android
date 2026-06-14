package app.dockwallet.wallet.ui.passes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dockwallet.wallet.data.api.BoardingPass
import app.dockwallet.wallet.data.repository.DockWalletRepository
import app.dockwallet.wallet.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PassesUiState(
    val passes: List<BoardingPass> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSyncing: Boolean = false
)

class PassesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockWalletRepository(application)

    private val _uiState = MutableStateFlow(PassesUiState())
    val uiState: StateFlow<PassesUiState> = _uiState

    init {
        loadPasses()
    }

    fun loadPasses() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getPasses()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    passes = result.data,
                    isLoading = false
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.message
                )
            }
        }
    }

    fun logout() = repository.logout()
}