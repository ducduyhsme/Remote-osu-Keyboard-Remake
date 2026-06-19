package com.rosk.remoteosukey

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rosk.remoteosukey.ui.HomeScreen
import com.rosk.remoteosukey.ui.PlayScreen
import com.rosk.remoteosukey.ui.SettingsScreen
import com.rosk.remoteosukey.ui.theme.RemoteOsuKeyboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide system bars (Immersive mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            RemoteOsuKeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            HomeScreen(
                                onNavigateToPlay = { connectionType, serverAddress ->
                                    navController.navigate("play/$connectionType/$serverAddress")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable("play/{connectionType}/{serverAddress}") { backStackEntry ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            val connectionType = backStackEntry.arguments?.getString("connectionType") ?: "wifi"
                            val serverAddress = backStackEntry.arguments?.getString("serverAddress") ?: ""
                            PlayScreen(
                                connectionType = connectionType,
                                serverAddress = serverAddress,
                                onBack = { navController.popBackStack() },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("settings") {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
