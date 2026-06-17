package com.rosk.remoteosukey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        setContent {
            RemoteOsuKeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
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
