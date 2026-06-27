package app.dockwallet.wallet.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

/**
 * Full-screen lock gate.
 *
 * Automatically triggers the biometric/PIN prompt on first composition.
 * Shows a manual "Entsperren" button in case the prompt was dismissed.
 *
 * @param activity  needed by BiometricPrompt (must be a FragmentActivity / ComponentActivity)
 * @param onUnlocked called once authentication succeeds — caller should hide this screen
 */
@Composable
fun LockScreen(
    activity: FragmentActivity,
    onUnlocked: () -> Unit
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var promptShown  by remember { mutableStateOf(false) }

    // Auto-trigger on first composition
    LaunchedEffect(Unit) {
        if (!promptShown) {
            promptShown = true
            showBiometricPrompt(
                activity  = activity,
                onSuccess = onUnlocked,
                onFailed  = { errorMessage = "Zu viele Fehlversuche. Versuche es später erneut." },
                onError   = { msg -> if (msg != "cancelled") errorMessage = msg }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            // Lock icon in circle
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "DockWallet",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Entsperre die App mit\nFingerabdruck, Gesicht oder PIN.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            errorMessage?.let { msg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // Manual trigger button
            Button(
                onClick = {
                    errorMessage = null
                    showBiometricPrompt(
                        activity  = activity,
                        onSuccess = onUnlocked,
                        onFailed  = { errorMessage = "Zu viele Fehlversuche. Versuche es später erneut." },
                        onError   = { msg -> if (msg != "cancelled") errorMessage = msg }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Entsperren")
            }
        }
    }
}