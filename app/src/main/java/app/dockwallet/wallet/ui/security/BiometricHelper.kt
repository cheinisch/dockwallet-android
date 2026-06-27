package app.dockwallet.wallet.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// Which authenticators we accept: biometrics OR device PIN/pattern/password
private val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Returns true if the device has *any* screen-lock configured. */
fun canAuthenticate(context: Context): Boolean {
    val mgr = BiometricManager.from(context)
    return mgr.canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
}

/**
 * Shows the system biometric / PIN prompt.
 *
 * @param onSuccess called when the user successfully authenticates
 * @param onFailed  called on too many failed attempts (lockout)
 * @param onError   called when the prompt is cancelled or an error occurs
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailed: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationFailed() {
            // Single failed attempt — the prompt stays open, nothing to do here
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Codes 10 (user cancelled) and 13 (negative button) are normal dismissals
            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                onError("cancelled")
            } else if (errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                onFailed()
            } else {
                onError(errString.toString())
            }
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("DockWallet entsperren")
        .setSubtitle("Biometrie oder Geräte-PIN verwenden")
        .setAllowedAuthenticators(ALLOWED)
        .build()

    prompt.authenticate(info)
}