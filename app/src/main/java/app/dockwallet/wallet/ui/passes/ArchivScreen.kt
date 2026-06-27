package app.dockwallet.wallet.ui.passes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.ui.detail.parseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onPassClick: (PassEntity) -> Unit,
    viewModel: PassesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val archived = uiState.passes.filter { it.isArchived() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archiv", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (archived.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Kein Archiv",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Abgelaufene oder ungültige Pässe erscheinen hier automatisch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        Text(
                            "${archived.size} archivierte ${if (archived.size == 1) "Pass" else "Pässe"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(archived, key = { it.id }) { pass ->
                        ArchivedPassRow(
                            pass = pass,
                            onClick = { onPassClick(pass) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedPassRow(pass: PassEntity, onClick: () -> Unit) {
    val bgColor = parseColor(pass.colorBackground) ?: MaterialTheme.colorScheme.primaryContainer
    val fgColor = parseColor(pass.colorForeground) ?: MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Logo circle — desaturated/dimmed to signal archived state
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (pass.logoText ?: pass.passengerName ?: "?")
                    .trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = fgColor.copy(alpha = 0.5f)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pass.logoText ?: pass.passengerName ?: "Pass",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            // Reason badge
            val reason = when {
                pass.isVoided -> "Ungültig"
                pass.isExpired() -> "Abgelaufen"
                else -> "Archiviert"
            }
            val expiryInfo = pass.expiryDate()?.take(10)
            Text(
                text = if (expiryInfo != null) "$reason · $expiryInfo" else reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Helper extensions (shared with PassesScreen filter) ───────────────────────

fun PassEntity.isExpired(): Boolean {
    val dateStr = arrivalTime ?: eventDate ?: departureTime ?: return false
    return try {
        // ISO-8601: compare as string (lexicographic works for yyyy-MM-dd / yyyy-MM-ddTHH:mm)
        val now = java.time.Instant.now().toString().take(16)   // "yyyy-MM-ddTHH:mm"
        val passDate = dateStr.replace(" ", "T").take(16)
        passDate < now
    } catch (e: Exception) { false }
}

fun PassEntity.expiryDate(): String? =
    arrivalTime ?: eventDate ?: departureTime

fun PassEntity.isArchived(): Boolean = isVoided || isExpired()