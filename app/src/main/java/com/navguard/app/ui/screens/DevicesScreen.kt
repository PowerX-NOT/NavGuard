package com.navguard.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.navguard.app.BluetoothUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onDeviceSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var permissionMissing by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            refreshDevices(bluetoothAdapter, context) { deviceList, missing ->
                devices = deviceList
                permissionMissing = missing
            }
        } else {
            showPermissionDialog = true
        }
    }
    
    LaunchedEffect(Unit) {
        refreshDevices(bluetoothAdapter, context) { deviceList, missing ->
            devices = deviceList
            permissionMissing = missing
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Devices") },
                actions = {
                    if (permissionMissing) {
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    refreshDevices(bluetoothAdapter, context) { deviceList, missing ->
                                        devices = deviceList
                                        permissionMissing = missing
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Bluetooth Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Device List
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            bluetoothAdapter == null -> "Bluetooth not supported"
                            !bluetoothAdapter.isEnabled -> "Bluetooth is disabled"
                            permissionMissing -> "Permission missing, use REFRESH"
                            else -> "No bluetooth devices found"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceSelected(device.address) }
                        )
                    }
                }
            }
        }
    }
    
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Bluetooth Permission") },
            text = { Text("Bluetooth permission was denied. Please enable it in app settings to use emergency devices.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun refreshDevices(
    bluetoothAdapter: BluetoothAdapter?,
    context: Context,
    onResult: (List<BluetoothDevice>, Boolean) -> Unit
) {
    val devices = mutableListOf<BluetoothDevice>()
    var permissionMissing = false
    
    if (bluetoothAdapter != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionMissing = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        }
        
        if (!permissionMissing && bluetoothAdapter.isEnabled) {
            try {
                bluetoothAdapter.bondedDevices?.let { bondedDevices ->
                    for (device in bondedDevices) {
                        if (device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                            devices.add(device)
                        }
                    }
                }
                devices.sortWith { a, b -> BluetoothUtil.compareTo(a, b) }
            } catch (e: SecurityException) {
                permissionMissing = true
            }
        }
    }
    
    onResult(devices, permissionMissing)
}