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
import app.dockwallet.wallet.ui.passes.PassesScreen
import app.dockwallet.wallet.ui.theme.DockWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (TokenStore.getToken(this) != null) "passes" else "login"

        setContent {
            DockWalletTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = startDestination) {
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
                                // kommt später: navController.navigate("pass/${pass.id}")
                            },
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