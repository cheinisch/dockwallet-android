package app.dockwallet.wallet.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.dockwallet.wallet.data.api.SyncDevice
import app.dockwallet.wallet.data.api.TokenStore
import app.dockwallet.wallet.data.repository.DockWalletRepository
import app.dockwallet.wallet.data.repository.Result
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
    val isDone: Boolean = false,
    // NEU: Sync-Geräte
    val devices: List<SyncDevice> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val deviceError: String? = null,
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
        val isLocal = TokenStore.isLocalMode(context)
        _uiState.value = _uiState.value.copy(
            isLocalMode = isLocal,
            serverUrl = TokenStore.getServerUrl(context),
            isLoggedIn = TokenStore.getToken(context) != null
        )
        if (!isLocal) loadDevices()
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
        repository.logout()
        _uiState.value = _uiState.value.copy(showDisconnectDialog = false, isDone = true)
    }

    fun logout() {
        repository.logout()
        _uiState.value = _uiState.value.copy(isDone = true)
    }

    // ── Geräte ────────────────────────────────────────────────────────────────

    private fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDevices = true)
            when (val result = repository.getSyncDevices()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    devices = result.data,
                    isLoadingDevices = false
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoadingDevices = false,
                    deviceError = result.message
                )
            }
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            when (val result = repository.removeSyncDevice(deviceId)) {
                is Result.Success -> loadDevices()
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    deviceError = result.message
                )
            }
        }
    }

    fun clearDeviceError() {
        _uiState.value = _uiState.value.copy(deviceError = null)
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
        if (uiState.isDone) onLogout()
    }

    // ── Dialoge ───────────────────────────────────────────────────────────────

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── Server ────────────────────────────────────────────────────────
            item { SectionLabel("Server") }

            if (uiState.isLocalMode) {
                item {
                    SettingsItem(
                        icon = Icons.Default.Cloud,
                        title = "Mit Server verbinden",
                        subtitle = "Aktuell: Nur lokaler Modus",
                        onClick = onConnectServer
                    )
                }
            } else {
                item {
                    SettingsItem(
                        icon = Icons.Default.Cloud,
                        title = "Server",
                        subtitle = uiState.serverUrl,
                        onClick = {}
                    )
                }
                item {
                    SettingsItem(
                        icon = Icons.Default.CloudOff,
                        title = "Verbindung trennen",
                        subtitle = "Wechselt in den lokalen Modus",
                        onClick = { viewModel.showDisconnectDialog(true) },
                        tintError = true
                    )
                }
            }

            // ── Sync-Geräte (nur wenn verbunden) ─────────────────────────────
            if (!uiState.isLocalMode) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionLabel("Verbundene Geräte")
                }

                if (uiState.deviceError != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    uiState.deviceError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = viewModel::clearDeviceError) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoadingDevices) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (uiState.devices.isEmpty()) {
                    item {
                        Text(
                            "Keine registrierten Geräte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    items(uiState.devices) { device ->
                        DeviceItem(
                            device = device,
                            onRemove = { viewModel.removeDevice(device.id) }
                        )
                    }
                }
            }

            // ── Daten ─────────────────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Daten")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Lokale Daten löschen",
                    subtitle = "Alle Pässe vom Gerät entfernen",
                    onClick = { viewModel.showDeleteLocalDialog(true) },
                    tintError = true
                )
            }

            // ── Konto ─────────────────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Konto")
            }
            if (uiState.isLoggedIn) {
                item {
                    SettingsItem(
                        icon = Icons.Default.Logout,
                        title = "Abmelden",
                        subtitle = "Token löschen, zurück zum Login",
                        onClick = viewModel::logout,
                        tintError = true
                    )
                }
            }

            // ── Info ──────────────────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Info")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Über DockWallet",
                    subtitle = "Version, Lizenz",
                    onClick = onAbout
                )
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

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

@Composable
private fun DeviceItem(
    device: SyncDevice,
    onRemove: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Gerät entfernen?") },
            text = { Text("\"${device.device_name}\" wird aus der Sync-Liste entfernt.") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onRemove() }) {
                    Text("Entfernen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(device.device_name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    if (device.last_sync != null) "Letzter Sync: ${device.last_sync.take(10)}"
                    else "Noch nie synchronisiert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Entfernen",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}