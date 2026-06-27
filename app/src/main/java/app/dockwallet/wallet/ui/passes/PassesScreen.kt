package app.dockwallet.wallet.ui.passes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.ui.detail.parseColor
import kotlinx.coroutines.launch

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassesScreen(
    onPassClick: (PassEntity) -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    viewModel: PassesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val layout   by LayoutPreference.get(context).collectAsState(initial = PassLayout.LIST)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DockWallet", fontWeight = FontWeight.Bold) },
                actions = {
                    if (!uiState.isLocalMode) {
                        IconButton(onClick = viewModel::sync, enabled = !uiState.isSyncing) {
                            if (uiState.isSyncing)
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Icon(Icons.Default.Sync, contentDescription = "Synchronisieren")
                        }
                    }
                    // Layout toggle
                    IconButton(onClick = {
                        val next = when (layout) {
                            PassLayout.LIST  -> PassLayout.GRID
                            PassLayout.GRID  -> PassLayout.STACK
                            PassLayout.STACK -> PassLayout.LIST
                        }
                        scope.launch { LayoutPreference.set(context, next) }
                    }) {
                        Icon(
                            imageVector = when (layout) {
                                PassLayout.LIST  -> Icons.Default.GridView
                                PassLayout.GRID  -> Icons.Default.ViewAgenda
                                PassLayout.STACK -> Icons.Default.ViewStream
                            },
                            contentDescription = "Layout wechseln"
                        )
                    }
                    // ⋮ Dropdown Menü
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Archiv") },
                                leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                                onClick = { menuExpanded = false; onOpenArchive() }
                            )
                            DropdownMenuItem(
                                text = { Text("Einstellungen") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuExpanded = false; onOpenSettings() }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                uiState.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CloudOff, null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::sync) { Text("Erneut versuchen") }
                }

                uiState.passes.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Wallet, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("Keine Pässe vorhanden", fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Füge Pässe im Web-Interface hinzu", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> {
                    val activePasses = uiState.passes.filter { !it.isArchived() }
                    val archivedCount = uiState.passes.size - activePasses.size
                    when (layout) {
                        PassLayout.LIST  -> ListLayout(activePasses, onPassClick, viewModel::toggleFavorite, archivedCount, onOpenArchive)
                        PassLayout.GRID  -> GridLayout(activePasses, onPassClick, viewModel::toggleFavorite)
                        PassLayout.STACK -> StackLayout(activePasses, onPassClick, viewModel::toggleFavorite)
                    }
                }
            }
        }
    }
}

// ── LIST layout ───────────────────────────────────────────────────────────────

@Composable
private fun ListLayout(
    passes: List<PassEntity>,
    onPassClick: (PassEntity) -> Unit,
    onToggleFavorite: (PassEntity) -> Unit,
    archivedCount: Int = 0,
    onOpenArchive: () -> Unit = {}
) {
    val favorites = passes.filter { it.isFavorite }
    val rest      = passes.filter { !it.isFavorite }
    var showAll   by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

        // ── Favoriten als Kreditkarten ────────────────────────────────────────
        if (favorites.isNotEmpty()) {
            item {
                SectionHeader("Favoriten",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
            items(favorites, key = { it.id }) { pass ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PassCard(pass = pass,
                        onClick = { onPassClick(pass) },
                        onToggleFavorite = { onToggleFavorite(pass) })
                }
            }
        }

        // ── Keine Favoriten: Hinweis ──────────────────────────────────────────
        if (favorites.isEmpty() && rest.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.StarOutline, null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Noch keine Favoriten",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tippe auf \u2605 um einen Pass anzuheften",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── "Alle anzeigen" Button ────────────────────────────────────────────
        if (rest.isNotEmpty()) {
            item {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp,
                            top = if (favorites.isEmpty()) 0.dp else 16.dp,
                            bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showAll) "Weniger anzeigen"
                        else "Alle ${rest.size + favorites.size} P\u00e4sse anzeigen",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // ── Restliche Pässe (ausgeklappt) ─────────────────────────────────────
        if (showAll && rest.isNotEmpty()) {
            items(rest, key = { it.id }) { pass ->
                CompactPassRow(
                    pass = pass,
                    onClick = { onPassClick(pass) },
                    onToggleFavorite = { onToggleFavorite(pass) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }

        // ── Archiv-Hinweis ────────────────────────────────────────────────────
        if (archivedCount > 0) {
            item {
                TextButton(
                    onClick = onOpenArchive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$archivedCount abgelaufene ${if (archivedCount == 1) "Pass" else "P\u00e4sse"} im Archiv",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ── GRID layout ───────────────────────────────────────────────────────────────

@Composable
private fun GridLayout(
    passes: List<PassEntity>,
    onPassClick: (PassEntity) -> Unit,
    onToggleFavorite: (PassEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(passes, key = { it.id }) { pass ->
            PassCardCompact(
                pass = pass,
                onClick = { onPassClick(pass) },
                onToggleFavorite = { onToggleFavorite(pass) }
            )
        }
    }
}

// ── STACK layout ──────────────────────────────────────────────────────────────

@Composable
private fun StackLayout(
    passes: List<PassEntity>,
    onPassClick: (PassEntity) -> Unit,
    onToggleFavorite: (PassEntity) -> Unit
) {
    val favorites  = passes.filter { it.isFavorite }
    val rest       = passes.filter { !it.isFavorite }
    var showAll    by remember { mutableStateOf(false) }
    val visible    = if (showAll || favorites.isEmpty()) passes else favorites
    val peekHeight = 64.dp

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {

        // Gestapelte Karten (nur Favoriten oder alle)
        itemsIndexed(visible, key = { _: Int, p: PassEntity -> p.id }) { index, pass ->
            val isLast = index == visible.lastIndex && (showAll || rest.isEmpty())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isLast) Modifier.height(peekHeight) else Modifier.wrapContentHeight())
                    .padding(bottom = if (!isLast) 0.dp else 12.dp)
            ) {
                PassCard(
                    pass = pass,
                    onClick = { onPassClick(pass) },
                    onToggleFavorite = { onToggleFavorite(pass) }
                )
            }
        }

        // "Alle anzeigen" Button
        if (rest.isNotEmpty()) {
            item {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showAll) "Weniger anzeigen"
                        else "Alle ${passes.size} Pässe anzeigen",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Keine Favoriten: Hinweis
        if (favorites.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.StarOutline, null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tippe auf ★ um Favoriten anzuheften",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Compact row (Google Wallet style) ─────────────────────────────────────────

@Composable
private fun CompactPassRow(
    pass: PassEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val bgColor = parseColor(pass.colorBackground) ?: MaterialTheme.colorScheme.primaryContainer
    val fgColor = parseColor(pass.colorForeground) ?: MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Logo circle — pass background color with initials / type icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            val initial = (pass.logoText ?: pass.passengerName ?: "?")
                .trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = initial,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pass.logoText ?: pass.passengerName ?: "Pass",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = listOfNotNull(
                pass.subtitle,
                pass.passengerName?.takeIf { pass.subtitle == null },
                passTypeLabel(pass.passType)
            ).firstOrNull { it.isNotBlank() }
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Favorite star
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (pass.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                contentDescription = "Favorit",
                tint = if (pass.isFavorite) Color(0xFF2979FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Credit-card tile (full) ───────────────────────────────────────────────────

@Composable
fun PassCard(
    pass: PassEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    val bgColor  = parseColor(pass.colorBackground) ?: MaterialTheme.colorScheme.primaryContainer
    val fgColor  = parseColor(pass.colorForeground) ?: MaterialTheme.colorScheme.onPrimaryContainer
    val lblColor = parseColor(pass.colorLabel)      ?: fgColor.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))))

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: logoText + star
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Text(
                    text = pass.logoText ?: "",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fgColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp).offset(x = 8.dp, y = (-4).dp)
                ) {
                    Icon(
                        imageVector = if (pass.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = "Favorit",
                        tint = if (pass.isFavorite) Color(0xFFFFD600)
                        else fgColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Middle
            when (pass.passType) {
                "boardingPass" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(pass.origin ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = fgColor)
                    Icon(Icons.Default.Flight, null, tint = fgColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp))
                    Text(pass.destination ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = fgColor)
                }
                else -> pass.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(sub, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = fgColor,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // Bottom
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom) {
                Column {
                    if (!pass.passengerName.isNullOrBlank()) {
                        Text("NAME", fontSize = 9.sp, color = lblColor, letterSpacing = 1.sp)
                        Text(pass.passengerName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = fgColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val (secLabel, secValue) = when (pass.passType) {
                        "boardingPass" -> "FLUG" to pass.flightNumber
                        else -> (if (pass.eventDate != null) "DATUM" else null) to
                                pass.eventDate?.replace("T", " ")?.take(10)
                    }
                    if (secLabel != null && secValue != null) {
                        Text(secLabel, fontSize = 9.sp, color = lblColor, letterSpacing = 1.sp)
                        Text(secValue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fgColor)
                    }
                }
            }
        }

        if (pass.isVoided) {
            Box(modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Red.copy(alpha = 0.85f)) {
                    Text("UNGÜLTIG",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, letterSpacing = 2.sp)
                }
            }
        }
    }
}

// ── Compact tile for GRID ─────────────────────────────────────────────────────

@Composable
private fun PassCardCompact(
    pass: PassEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val bgColor  = parseColor(pass.colorBackground) ?: MaterialTheme.colorScheme.primaryContainer
    val fgColor  = parseColor(pass.colorForeground) ?: MaterialTheme.colorScheme.onPrimaryContainer
    val lblColor = parseColor(pass.colorLabel)      ?: fgColor.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f)))))

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pass.logoText ?: "", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = fgColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (pass.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = null,
                    tint = if (pass.isFavorite) Color(0xFFFFD600) else fgColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp).clickable { onToggleFavorite() }
                )
            }

            when (pass.passType) {
                "boardingPass" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(pass.origin ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fgColor)
                    Icon(Icons.Default.Flight, null, tint = fgColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp))
                    Text(pass.destination ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fgColor)
                }
                else -> pass.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fgColor,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (!pass.passengerName.isNullOrBlank()) {
                Column {
                    Text("NAME", fontSize = 7.sp, color = lblColor, letterSpacing = 1.sp)
                    Text(pass.passengerName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = fgColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (pass.isVoided) {
            Box(modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) {
                Text("UNGÜLTIG", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

private fun passTypeLabel(type: String?) = when (type) {
    "boardingPass" -> "Boarding Pass"
    "eventTicket"  -> "Veranstaltungsticket"
    "coupon"       -> "Coupon"
    "storeCard"    -> "Kundenkarte"
    else           -> "Pass"
}

private fun passTypeIcon(passType: String?) = when (passType) {
    "boardingPass" -> Icons.Default.Flight
    "eventTicket"  -> Icons.Default.ConfirmationNumber
    "coupon"       -> Icons.Default.LocalOffer
    "storeCard"    -> Icons.Default.CreditCard
    else           -> Icons.Default.Badge
}