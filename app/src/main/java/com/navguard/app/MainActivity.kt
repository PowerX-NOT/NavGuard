package com.navguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.navguard.app.ui.screens.DevicesScreen
import com.navguard.app.ui.screens.EmergencyTerminalScreen
import com.navguard.app.ui.theme.NavGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removed enableEdgeToEdge() to prevent header flickering
        // This allows for smoother keyboard transitions
        
        setContent {
            NavGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGuardApp()
                }
            }
        }
    }
}

@Composable
fun NavGuardApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "devices"
    ) {
        composable("devices") {
            DevicesScreen(
                onDeviceSelected = { deviceAddress ->
                    navController.navigate("emergency_terminal/$deviceAddress")
                }
            )
        }
        composable("emergency_terminal/{deviceAddress}") { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            EmergencyTerminalScreen(
                deviceAddress = deviceAddress,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NavGuardAppPreview() {
    NavGuardTheme {
        NavGuardApp()
    }
}