package com.navguard.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import android.bluetooth.BluetoothProfile
import android.app.AlertDialog
import android.content.DialogInterface
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    scanKey: Int = 0,
    onDeviceSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var availableDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var permissionMissing by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connectionStatus by remember { mutableStateOf("No device connected") }
    var deviceToPair by remember { mutableStateOf<BluetoothDevice?>(null) }
    var showPairDialog by remember { mutableStateOf(false) }
    
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    // BroadcastReceiver for device discovery
    val deviceDiscoveryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        device?.let { foundDevice ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    // Only add non-paired, non-LE devices that aren't already in the list
                                    if (!foundDevice.bondState.equals(BluetoothDevice.BOND_BONDED) 
                                        && foundDevice.type != BluetoothDevice.DEVICE_TYPE_LE
                                        && availableDevices.none { it.address == foundDevice.address }
                                    ) {
                                        availableDevices = availableDevices + foundDevice
                                    }
                                }
                            } else {
                                @SuppressLint("MissingPermission")
                                if (!foundDevice.bondState.equals(BluetoothDevice.BOND_BONDED) 
                                    && foundDevice.type != BluetoothDevice.DEVICE_TYPE_LE
                                    && availableDevices.none { it.address == foundDevice.address }
                                ) {
                                    availableDevices = availableDevices + foundDevice
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanning = false
                    }
                }
            }
        }
    }
    
    // --- Bluetooth connection state receiver ---
    val connectionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        connectedDevice = device
                        connectionStatus = device?.let { "Connected to ${it.name ?: it.address}" } ?: run {
                            // Fallback: check system state
                            val classicConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                            val a2dpConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                            val gattConnected = bluetoothAdapter?.getProfileConnectionState(7) == BluetoothProfile.STATE_CONNECTED
                            if (classicConnected || a2dpConnected || gattConnected) "Connected to system Bluetooth device" else "Connected"
                        }
                        Toast.makeText(context, connectionStatus, Toast.LENGTH_SHORT).show()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (connectedDevice?.address == device?.address) {
                            connectedDevice = null
                        }
                        // Fallback: check system state
                        val classicConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                        val a2dpConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                        val gattConnected = bluetoothAdapter?.getProfileConnectionState(7) == BluetoothProfile.STATE_CONNECTED
                        connectionStatus = if (classicConnected || a2dpConnected || gattConnected) {
                            "Connected to system Bluetooth device"
                        } else {
                            "No device connected"
                        }
                        Toast.makeText(context, "Device disconnected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Register/Unregister BroadcastReceiver
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(deviceDiscoveryReceiver, filter)
        
        onDispose {
            try {
                context.unregisterReceiver(deviceDiscoveryReceiver)
                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter.cancelDiscovery()
                }
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
    }

    // Register/Unregister BroadcastReceiver for connection state
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(connectionReceiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(connectionReceiver)
            } catch (e: Exception) {}
        }
    }

    // On launch, check for already connected device (classic and BLE)
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
        connectedDevice = connected
        connectionStatus = when {
            connected != null -> "Connected to ${connected.name ?: connected.address}"
            classicConnected || a2dpConnected || gattConnected -> "Connected to system Bluetooth device"
            else -> "No device connected"
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            refreshPairedDevices(bluetoothAdapter, context) { deviceList, missing ->
                pairedDevices = deviceList
                permissionMissing = missing
                
                if (!missing) {
                    startDeviceDiscovery(bluetoothAdapter, context) {
                        isScanning = it
                        if (it) {
                            availableDevices = emptyList()
                        }
                    }
                }
            }
        } else {
            showPermissionDialog = true
        }
    }
    
    LaunchedEffect(Unit) {
        refreshPairedDevices(bluetoothAdapter, context) { deviceList, missing ->
            pairedDevices = deviceList
            permissionMissing = missing
        }
    }
    
    fun triggerScan() {
                            val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            }
                            permissionLauncher.launch(requiredPermissions)
    }
    
    LaunchedEffect(scanKey) {
        triggerScan()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Devices") },
                actions = {
                    IconButton(
                        onClick = { triggerScan() },
                        enabled = !isScanning
                    ) {
                        Icon(
                            if (isScanning) Icons.Default.Refresh else Icons.Default.Search, 
                            contentDescription = if (isScanning) "Scanning..." else "Scan for devices"
                        )
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
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
            
            // Header Card (REMOVE this block)
            // Card(
            //     modifier = Modifier
            //         .fillMaxWidth()
            //         .padding(bottom = 16.dp),
            //     elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            // ) {
            //     Row(
            //         modifier = Modifier
            //             .fillMaxWidth()
            //             .padding(16.dp),
            //         verticalAlignment = Alignment.CenterVertically,
            //         horizontalArrangement = Arrangement.Center
            //     ) {
            //         Icon(
            //             Icons.Default.Bluetooth,
            //             contentDescription = null,
            //             modifier = Modifier.size(24.dp),
            //             tint = MaterialTheme.colorScheme.primary
            //         )
            //         Spacer(modifier = Modifier.width(8.dp))
            //         Text(
            //             text = connectionStatus,
            //             style = MaterialTheme.typography.titleMedium,
            //             fontWeight = FontWeight.Bold
            //         )
            //     }
            // }
            
            // Device Lists
            if (pairedDevices.isEmpty() && availableDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            bluetoothAdapter == null -> "Bluetooth not supported"
                            !bluetoothAdapter.isEnabled -> "Bluetooth is disabled"
                            permissionMissing -> "Permission missing, tap SCAN"
                            isScanning -> "Scanning for devices..."
                            else -> "No emergency devices found\nTap SCAN to discover devices"
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
                    if (pairedDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "Paired Devices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(pairedDevices) { device ->
                            val isConnectedDevice = device.address == connectedDevice?.address
                            DeviceItem(
                                device = device,
                                isPaired = true,
                                isConnected = isConnectedDevice,
                                onClick = {
                                    onDeviceSelected(device.address)
                                }
                            )
                        }
                    }
                    
                    if (availableDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "Available Devices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    top = if (pairedDevices.isNotEmpty()) 16.dp else 8.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                        
                        items(availableDevices) { device ->
                            DeviceItem(
                                device = device,
                                isPaired = false,
                                onClick = {
                                    deviceToPair = device
                                    showPairDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Bluetooth Permissions") },
            text = { Text("Bluetooth and location permissions are required to scan for emergency devices. Please enable them in app settings.") },
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

    // Compose AlertDialog for pairing
    if (showPairDialog && deviceToPair != null) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Pair Device") },
            text = { Text("This device is not paired. Would you like to pair it now?") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val method = deviceToPair!!.javaClass.getMethod("createBond")
                        method.invoke(deviceToPair)
                        Toast.makeText(context, "Pairing started", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Pairing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    showPairDialog = false
                }) {
                    Text("Pair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) {
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
    isPaired: Boolean,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isPaired)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else
            CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isPaired) {
                    Badge(
                        containerColor = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = if (isConnected) "Connected" else "Paired",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) Color.White else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPaired)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isPaired) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to connect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun refreshPairedDevices(
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

@SuppressLint("MissingPermission")
private fun startDeviceDiscovery(
    bluetoothAdapter: BluetoothAdapter?,
    context: Context,
    onScanningStateChanged: (Boolean) -> Unit
) {
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
        onScanningStateChanged(false)
        return
    }
    
    var permissionMissing = false
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionMissing = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    } else {
        permissionMissing = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    }
    
    if (permissionMissing) {
        onScanningStateChanged(false)
        return
    }
    
    try {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        
        val started = bluetoothAdapter.startDiscovery()
        onScanningStateChanged(started)
    } catch (e: SecurityException) {
        onScanningStateChanged(false)
    }
}