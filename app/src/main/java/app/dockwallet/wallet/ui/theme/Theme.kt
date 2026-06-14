package app.dockwallet.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF0288D1),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFE),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFD32F2F)
)

@Composable
fun DockWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}