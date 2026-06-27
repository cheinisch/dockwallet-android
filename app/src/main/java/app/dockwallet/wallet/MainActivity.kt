package app.dockwallet.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.dockwallet.wallet.data.AppDatabase
import app.dockwallet.wallet.data.PassEntity
import app.dockwallet.wallet.data.PkpassParser
import app.dockwallet.wallet.data.api.TokenStore
import app.dockwallet.wallet.ui.about.AboutScreen
import app.dockwallet.wallet.ui.detail.DetailScreen
import app.dockwallet.wallet.ui.login.LoginScreen
import app.dockwallet.wallet.ui.onboarding.OnboardingScreen
import app.dockwallet.wallet.ui.passes.ArchiveScreen
import app.dockwallet.wallet.ui.passes.ArchiveScreen
import app.dockwallet.wallet.ui.passes.PassesScreen
import app.dockwallet.wallet.ui.security.BiometricPreference
import app.dockwallet.wallet.ui.security.LockScreen
import app.dockwallet.wallet.ui.settings.SettingsScreen
import app.dockwallet.wallet.ui.theme.DockWalletTheme
import app.dockwallet.wallet.ui.theme.ThemeMode
import app.dockwallet.wallet.ui.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val startDestination = when {
            !TokenStore.isOnboardingDone(this) -> "onboarding"
            TokenStore.getToken(this) != null   -> "passes"
            TokenStore.isLocalMode(this)        -> "passes"
            else                                -> "login"
        }

        setContent {
            val themeMode      by ThemePreference.get(this).collectAsState(initial = ThemeMode.SYSTEM)
            val biometricEnabled by BiometricPreference.isEnabled(this).collectAsState(initial = false)

            // true = still locked, false = user has authenticated this session
            var locked by remember { mutableStateOf(biometricEnabled) }

            // If the user enables biometric mid-session, lock immediately
            LaunchedEffect(biometricEnabled) {
                if (biometricEnabled) locked = true
            }

            DockWalletTheme(themeMode = themeMode) {
                if (locked && biometricEnabled) {
                    LockScreen(
                        activity   = this@MainActivity,
                        onUnlocked = { locked = false }
                    )
                } else {
                    val navController = rememberNavController()
                    var selectedPass by remember { mutableStateOf<PassEntity?>(null) }

                    NavHost(navController = navController, startDestination = startDestination) {

                        composable("onboarding") {
                            OnboardingScreen(
                                onDone = {
                                    val next = if (
                                        TokenStore.getToken(this@MainActivity) != null ||
                                        TokenStore.isLocalMode(this@MainActivity)
                                    ) "passes" else "login"
                                    navController.navigate(next) {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("passes") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("passes") {
                            PassesScreen(
                                onPassClick = { pass ->
                                    selectedPass = pass
                                    navController.navigate("detail")
                                },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("passes") { inclusive = true }
                                    }
                                },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenArchive = { navController.navigate("archive") }
                            )
                        }

                        composable("detail") {
                            selectedPass?.let { pass ->
                                DetailScreen(pass = pass, onBack = { navController.popBackStack() })
                            }
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("passes") { inclusive = true }
                                    }
                                },
                                onConnectServer = {
                                    navController.navigate("onboarding") {
                                        popUpTo("passes") { inclusive = false }
                                    }
                                },
                                onAbout = { navController.navigate("about") }
                            )
                        }

                        composable("archive") {
                            ArchiveScreen(
                                onBack = { navController.popBackStack() },
                                onPassClick = { pass ->
                                    selectedPass = pass
                                    navController.navigate("detail")
                                }
                            )
                        }

                        composable("about") {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScope.launch {
            val pass = withContext(Dispatchers.IO) { PkpassParser.parse(this@MainActivity, uri) }
            if (pass != null) {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@MainActivity).passDao().insert(pass)
                }
                Toast.makeText(this@MainActivity, "Pass importiert!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Ungültige .pkpass Datei", Toast.LENGTH_SHORT).show()
            }
        }
    }
}