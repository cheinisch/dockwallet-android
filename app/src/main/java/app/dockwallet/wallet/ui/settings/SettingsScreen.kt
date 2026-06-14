package app.dockwallet.wallet.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.api.TokenStore
import app.dockwallet.wallet.data.repository.DockWalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val isLocalMode: Boolean = false,
    val serverUrl: String = "",
    val isLoggedIn: Boolean = false,
    val showDeleteLocalDialog: Boolean = false,
    val showDisconnectDialog: Boolean = false,
    val isDone: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockWalletRepository(application)
    private val dao = AppDatabase.getInstance(application).passDao()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            isLocalMode = TokenStore.isLocalMode(context),
            serverUrl = TokenStore.getServerUrl(context),
            isLoggedIn = TokenStore.getToken(context) != null
        )
    }

    fun showDeleteLocalDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteLocalDialog = show)
    }

    fun showDisconnectDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDisconnectDialog = show)
    }

    fun deleteLocalData() {
        viewModelScope.launch {
            dao.deleteAllServerPasses()
            _uiState.value = _uiState.value.copy(showDeleteLocalDialog = false)
        }
    }

    fun disconnectServer() {
        val context = getApplication<Application>()
        TokenStore.clearToken(context)
        TokenStore.saveServerUrl(context, "local")
        _uiState.value = _uiState.value.copy(
            showDisconnectDialog = false,
            isDone = true
        )
    }

    fun logout() {
        repository.logout()
        _uiState.value = _uiState.value.copy(isDone = true)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onConnectServer: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onBack()
    }

    if (uiState.showDeleteLocalDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteLocalDialog(false) },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text("Lokale Daten löschen") },
            text = { Text("Alle lokal gespeicherten Pässe werden gelöscht. Fortfahren?") },
            confirmButton = {
                TextButton(onClick = viewModel::deleteLocalData) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteLocalDialog(false) }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (uiState.showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisconnectDialog(false) },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
            title = { Text("Verbindung trennen") },
            text = { Text("Die Serververbindung wird getrennt. Die App wechselt in den lokalen Modus.") },
            confirmButton = {
                TextButton(onClick = viewModel::disconnectServer) {
                    Text("Trennen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDisconnectDialog(false) }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Server ──
            SectionLabel("Server")

            if (uiState.isLocalMode) {
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = "Mit Server verbinden",
                    subtitle = "Aktuell: Nur lokaler Modus",
                    onClick = onConnectServer
                )
            } else {
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = "Server",
                    subtitle = uiState.serverUrl,
                    onClick = {}
                )
                SettingsItem(
                    icon = Icons.Default.CloudOff,
                    title = "Verbindung trennen",
                    subtitle = "Wechselt in den lokalen Modus",
                    onClick = { viewModel.showDisconnectDialog(true) },
                    tintError = true
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Daten ──
            SectionLabel("Daten")

            SettingsItem(
                icon = Icons.Default.Delete,
                title = "Lokale Daten löschen",
                subtitle = "Alle Pässe vom Gerät entfernen",
                onClick = { viewModel.showDeleteLocalDialog(true) },
                tintError = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Konto ──
            SectionLabel("Konto")

            if (uiState.isLoggedIn) {
                SettingsItem(
                    icon = Icons.Default.Logout,
                    title = "Abmelden",
                    subtitle = "Token löschen, zurück zum Login",
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    tintError = true
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Info ──
            SectionLabel("Info")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Über DockWallet",
                subtitle = "Version, Lizenz",
                onClick = onAbout
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tintError: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (tintError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (tintError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}