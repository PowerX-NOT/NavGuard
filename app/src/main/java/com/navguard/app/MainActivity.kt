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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile

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
    val context = LocalContext.current
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    var initialRoute = remember { "devices" }
    var connectedDeviceAddress: String? = null
    var autoOpened = remember { false }
    var scanKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val classicConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        val a2dpConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
        val gattConnected = bluetoothAdapter?.getProfileConnectionState(7) == BluetoothProfile.STATE_CONNECTED // 7 = GATT
        val connected = bluetoothAdapter?.bondedDevices?.firstOrNull { device ->
            try {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
        }
        if (connected != null) {
            connectedDeviceAddress = connected.address
            autoOpened = true
            navController.navigate("emergency_terminal/${connected.address}") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute
    ) {
        composable("devices") {
            DevicesScreen(
                scanKey = scanKey,
                onDeviceSelected = { deviceAddress ->
                    autoOpened = false
                    navController.navigate("emergency_terminal/$deviceAddress")
                }
            )
        }
        composable("emergency_terminal/{deviceAddress}") { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            EmergencyTerminalScreen(
                deviceAddress = deviceAddress,
                onNavigateBack = {
                    scanKey++
                    if (autoOpened) {
                        autoOpened = false
                        navController.navigate("devices") {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onOpenMap = { navController.navigate("offline_map") },
                onOpenMapAt = { lat, lon ->
                    navController.navigate("offline_map/$lat/$lon")
                }
            )
        }
        composable("offline_map") {
            com.navguard.app.ui.screens.OfflineMapScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("offline_map/{lat}/{lon}") { backStackEntry ->
            val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull()
            val lon = backStackEntry.arguments?.getString("lon")?.toDoubleOrNull()
            com.navguard.app.ui.screens.OfflineMapScreen(
                onNavigateBack = { navController.popBackStack() },
                initialCenter = if (lat != null && lon != null) org.mapsforge.core.model.LatLong(lat, lon) else null
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