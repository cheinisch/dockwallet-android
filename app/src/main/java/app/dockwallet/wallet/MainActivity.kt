package app.dockwallet.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.dockwallet.wallet.data.api.TokenStore
import app.dockwallet.wallet.ui.login.LoginScreen
import app.dockwallet.wallet.ui.onboarding.OnboardingScreen
import app.dockwallet.wallet.ui.passes.PassesScreen
import app.dockwallet.wallet.ui.theme.DockWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = when {
            !TokenStore.isOnboardingDone(this) -> "onboarding"
            TokenStore.getToken(this) != null -> "passes"
            TokenStore.isLocalMode(this) -> "passes"
            else -> "login"
        }

        setContent {
            DockWalletTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onDone = {
                                val next = if (TokenStore.getToken(this@MainActivity) != null
                                    || TokenStore.isLocalMode(this@MainActivity)) "passes"
                                else "login"
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
                            onPassClick = { },
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo("passes") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}