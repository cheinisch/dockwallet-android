package app.dockwallet.wallet.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.api.SyncDevice
import app.dockwallet.wallet.data.api.TokenStore
import app.dockwallet.wallet.data.repository.DockWalletRepository
import app.dockwallet.wallet.data.repository.ApiResult
import app.dockwallet.wallet.ui.passes.LayoutPreference
import app.dockwallet.wallet.ui.passes.PassLayout
import app.dockwallet.wallet.ui.security.BiometricPreference
import app.dockwallet.wallet.ui.security.canAuthenticate
import app.dockwallet.wallet.ui.theme.ThemeMode
import app.dockwallet.wallet.ui.theme.ThemePreference
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
    val devices: List<SyncDevice> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val deviceError: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockWalletRepository(application)
    private val dao = AppDatabase.getInstance(application).passDao()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init { refresh() }

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

    fun showDeleteLocalDialog(show: Boolean) { _uiState.value = _uiState.value.copy(showDeleteLocalDialog = show) }
    fun showDisconnectDialog(show: Boolean)  { _uiState.value = _uiState.value.copy(showDisconnectDialog = show) }

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

    private fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDevices = true)
            when (val result = repository.getSyncDevices()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    devices = result.data, isLoadingDevices = false)
                is ApiResult.Error   -> _uiState.value = _uiState.value.copy(
                    isLoadingDevices = false, deviceError = result.message)
            }
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            when (val result = repository.removeSyncDevice(deviceId)) {
                is ApiResult.Success -> loadDevices()
                is ApiResult.Error   -> _uiState.value = _uiState.value.copy(deviceError = result.message)
            }
        }
    }

    fun clearDeviceError() { _uiState.value = _uiState.value.copy(deviceError = null) }
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
    val context = LocalContext.current

    // Theme preference (read from DataStore)
    val themeMode      by ThemePreference.get(context).collectAsState(initial = ThemeMode.SYSTEM)
    val passLayout     by LayoutPreference.get(context).collectAsState(initial = PassLayout.LIST)
    val biometricOn    by BiometricPreference.isEnabled(context).collectAsState(initial = false)
    val canUseBiometric = canAuthenticate(context)

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
                TextButton(onClick = { viewModel.showDeleteLocalDialog(false) }) { Text("Abbrechen") }
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
                TextButton(onClick = { viewModel.showDisconnectDialog(false) }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
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

            // ── Darstellung ───────────────────────────────────────────────────
            item { SectionLabel("Darstellung") }
            item {
                ThemePicker(
                    current = themeMode,
                    onChange = { mode ->
                        viewModelScope(viewModel) { ThemePreference.set(context, mode) }
                    }
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                LayoutPicker(
                    current = passLayout,
                    onChange = { layout ->
                        viewModelScope(viewModel) { LayoutPreference.set(context, layout) }
                    }
                )
            }

            // ── Server ────────────────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Sicherheit")
            }
            item {
                BiometricToggle(
                    enabled = biometricOn,
                    available = canUseBiometric,
                    onChange = { on ->
                        viewModelScope(viewModel) { BiometricPreference.setEnabled(context, on) }
                    }
                )
            }

            // ── Server ────────────────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Server")
            }
            if (uiState.isLocalMode) {
                item {
                    SettingsItem(
                        icon = Icons.Default.Cloud,
                        title = "Mit Server verbinden",
                        subtitle = "Pässe über mehrere Geräte synchronisieren",
                        onClick = onConnectServer
                    )
                }
            } else {
                item {
                    SettingsItem(
                        icon = Icons.Default.CloudOff,
                        title = "Verbindung trennen",
                        subtitle = uiState.serverUrl.ifBlank { "Verbunden" },
                        onClick = { viewModel.showDisconnectDialog(true) },
                        tintError = true
                    )
                }
            }

            // ── Verbundene Geräte ─────────────────────────────────────────────
            if (!uiState.isLocalMode) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionLabel("Verbundene Geräte")
                }
                if (uiState.deviceError != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(uiState.deviceError!!, color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f))
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
                        Text("Keine registrierten Geräte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                } else {
                    items(uiState.devices) { device ->
                        DeviceItem(device = device, onRemove = { viewModel.removeDevice(device.id) })
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

// Helper to launch coroutine from composable callback
private fun viewModelScope(vm: SettingsViewModel, block: suspend () -> Unit) {
    vm.viewModelScope.launch { block() }
}

// ── Theme picker ──────────────────────────────────────────────────────────────

@Composable
private fun ThemePicker(
    current: ThemeMode,
    onChange: (ThemeMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Brightness6,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Erscheinungsbild",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeModeOption(
                    label = "System",
                    icon = Icons.Default.SettingsBrightness,
                    selected = current == ThemeMode.SYSTEM,
                    onClick = { onChange(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                ThemeModeOption(
                    label = "Hell",
                    icon = Icons.Default.LightMode,
                    selected = current == ThemeMode.LIGHT,
                    onClick = { onChange(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeModeOption(
                    label = "Dunkel",
                    icon = Icons.Default.DarkMode,
                    selected = current == ThemeMode.DARK,
                    onClick = { onChange(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surface

    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        onClick = onClick,
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

// ── Biometric toggle ──────────────────────────────────────────────────────────

@Composable
private fun BiometricToggle(
    enabled: Boolean,
    available: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = if (available) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "App-Sperre",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (available) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (available)
                        "Fingerabdruck, Gesicht oder Geräte-PIN"
                    else
                        "Kein Bildschirmschloss eingerichtet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onChange(it) },
                enabled = available
            )
        }
    }
}

// ── Layout picker ─────────────────────────────────────────────────────────────

@Composable
private fun LayoutPicker(
    current: PassLayout,
    onChange: (PassLayout) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Kartenansicht",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LayoutOption(
                    label = "Liste",
                    icon = Icons.Default.ViewStream,
                    selected = current == PassLayout.LIST,
                    onClick = { onChange(PassLayout.LIST) },
                    modifier = Modifier.weight(1f)
                )
                LayoutOption(
                    label = "Grid",
                    icon = Icons.Default.GridView,
                    selected = current == PassLayout.GRID,
                    onClick = { onChange(PassLayout.GRID) },
                    modifier = Modifier.weight(1f)
                )
                LayoutOption(
                    label = "Stapel",
                    icon = Icons.Default.ViewAgenda,
                    selected = current == PassLayout.STACK,
                    onClick = { onChange(PassLayout.STACK) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LayoutOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface
    val contentColor   = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        onClick = onClick,
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

// ── Composables (unverändert) ─────────────────────────────────────────────────

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
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (tintError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (tintError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeviceItem(device: SyncDevice, onRemove: () -> Unit) {
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

    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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