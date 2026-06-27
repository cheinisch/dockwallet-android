package app.dockwallet.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Color schemes ─────────────────────────────────────────────────────────────

private val LightColors = lightColorScheme(
    primary            = Color(0xFF1565C0),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary          = Color(0xFF0288D1),
    onSecondary        = Color.White,
    background         = Color(0xFFF8FAFE),
    surface            = Color.White,
    onSurface          = Color(0xFF1A1A2E),
    onSurfaceVariant   = Color(0xFF64748B),
    error              = Color(0xFFD32F2F)
)

private val DarkColors = darkColorScheme(
    primary            = Color(0xFF90CAF9),
    onPrimary          = Color(0xFF0D2B5E),
    primaryContainer   = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary          = Color(0xFF4FC3F7),
    onSecondary        = Color(0xFF003A57),
    background         = Color(0xFF0F1117),
    surface            = Color(0xFF1A1D27),
    onSurface          = Color(0xFFE2E8F0),
    onSurfaceVariant   = Color(0xFF94A3B8),
    error              = Color(0xFFEF9A9A)
)

// ── Theme mode ────────────────────────────────────────────────────────────────

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun DockWalletTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}