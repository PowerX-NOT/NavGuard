package com.navguard.app.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.IBinder
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.navguard.app.EmergencyMessage
import com.navguard.app.LocationManager
import com.navguard.app.SerialSocket
import com.navguard.app.SerialListener
import com.navguard.app.SerialService
import com.navguard.app.ui.theme.*
import kotlinx.coroutines.delay
import java.util.*
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyTerminalScreen(
    deviceAddress: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<MessageDisplay>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Initializing...") }
    var locationText by remember { mutableStateOf("GPS: Not available") }
    var isConnected by remember { mutableStateOf(false) }
    var showDeviceStatus by remember { mutableStateOf(false) }
    var showEmergencyContacts by remember { mutableStateOf(false) }
    
    // Bluetooth connection state
    var service: SerialService? by remember { mutableStateOf(null) }
    var socket: SerialSocket? by remember { mutableStateOf(null) }
    
    val listState = rememberLazyListState()
    val locationManager = remember { LocationManager(context) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            connectionStatus = "Location permission denied"
        }
    }
    
    // Serial listener for handling Bluetooth communication
    val serialListener = remember {
        object : SerialListener {
            override fun onSerialConnect() {
                isConnected = true
                connectionStatus = "Connected to device"
            }
            
            override fun onSerialConnectError(e: Exception) {
                isConnected = false
                connectionStatus = "Connection failed: ${e.message}"
            }
            
            override fun onSerialRead(data: ByteArray) {
                val receivedText = String(data)
                // Parse received emergency message
                parseReceivedMessage(receivedText)?.let { msg ->
                    messages = messages + MessageDisplay(msg, false)
                }
            }
            
            override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
                val sb = StringBuilder()
                for (data in datas) {
                    sb.append(String(data))
                }
                parseReceivedMessage(sb.toString())?.let { msg ->
                    messages = messages + MessageDisplay(msg, false)
                }
            }
            
            override fun onSerialIoError(e: Exception) {
                isConnected = false
                connectionStatus = "Connection lost: ${e.message}"
            }
        }
    }
    
    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SerialService.SerialBinder).getService()
                service?.attach(serialListener)
                connectionStatus = "Service connected"
            }
            
            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                connectionStatus = "Service disconnected"
            }
        }
    }
    
    // Connect to Bluetooth device on screen load
    LaunchedEffect(deviceAddress) {
        try {
            // Bind to service
            val intent = Intent(context, SerialService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            // Get Bluetooth device and create socket
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                socket = SerialSocket(context.applicationContext, device)
                
                // Wait for service to be connected before attempting connection
                delay(1000)
                
                service?.let { svc ->
                    socket?.let { sock ->
                        try {
                            svc.connect(sock)
                        } catch (e: Exception) {
                            connectionStatus = "Connection failed: ${e.message}"
                        }
                        connectionStatus = "Connecting to ${device.name ?: deviceAddress}..."
                    }
                } ?: run {
                    connectionStatus = "Service not ready"
                }
            } else {
                connectionStatus = "Bluetooth not available"
            }
        } catch (e: Exception) {
            connectionStatus = "Connection error: ${e.message}"
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            service?.disconnect()
            service?.detach()
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // Service might already be unbound
            }
        }
    }
    
    // Note: SOS countdown effect removed
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Terminal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { messages = emptyList() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                    IconButton(onClick = { showDeviceStatus = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Device Status")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Compact Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection status indicator
                val isConnectedState = isConnected
                val statusColor = if (isConnectedState) Color(0xFF4CAF50) else Color(0xFFFF5722)
                
                Icon(
                    imageVector = if (isConnectedState) Icons.Default.Send else Icons.Default.Clear,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 8.dp)
                )
                
                // Divider
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                )
                
                // GPS status
                val hasGps = locationText != "GPS: Not available"
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (hasGps) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier
                        .size(14.dp)
                        .padding(start = 8.dp)
                )
                
                Text(
                    text = if (hasGps) locationText.replace("GPS: ", "") else "Awaiting location...",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            
            // Messages Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages) { messageDisplay ->
                    MessageItem(messageDisplay)
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            
            // Input Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Enhanced Text Input Row
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Text field with send and emergency buttons inside
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "Type emergency message...",
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            ),
                            singleLine = false,
                            maxLines = 3,
                            trailingIcon = {
                                Row {
                                    // Regular send button
                                    IconButton(
                                        onClick = {
                                            if (messageText.isNotBlank()) {
                                                sendRegularMessage(
                                                    message = messageText,
                                                    service = service,
                                                    onMessageSent = { msg ->
                                                        messages = messages + MessageDisplay(msg, true)
                                                        messageText = ""
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    // Emergency button with enhanced visibility
                                    IconButton(
                                        onClick = {
                                            sendEmergencyMessage(
                                                message = messageText.ifBlank { "EMERGENCY: Need immediate assistance!" },
                                                service = service,
                                                locationManager = locationManager,
                                                onLocationUpdate = { lat, lon ->
                                                    locationText = "GPS: ${String.format("%.6f°N", lat)}, ${String.format("%.6f°E", lon)}"
                                                },
                                                onMessageSent = { msg ->
                                                    messages = messages + MessageDisplay(msg, true)
                                                    messageText = ""
                                                },
                                                onStatusUpdate = { status ->
                                                    connectionStatus = status
                                                }
                                            )
                                            // Vibrate briefly to confirm emergency button press
                                            vibrator.vibrate(longArrayOf(0, 150), -1)
                                        },
                                        modifier = Modifier
                                            .background(
                                                color = Color(0xFFFF4500).copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Emergency",
                                            tint = Color(0xFFFF4500),
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        )
                        
                        // Removed redundant action buttons row
                    }
                }
            }
        }
    }
    
    // Device Status Dialog
    if (showDeviceStatus) {
        AlertDialog(
            onDismissRequest = { showDeviceStatus = false },
            title = { Text("Device Status") },
            text = {
                Column {
                    Text("Connection: ${if (isConnected) "Connected" else "Disconnected"}")
                    Text("GPS: ${if (hasLocationPermission(context)) "Available" else "Permission needed"}")
                    Text("Bluetooth: ${if (isBluetoothEnabled()) "Enabled" else "Disabled"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceStatus = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Emergency Contacts Dialog
    if (showEmergencyContacts) {
        AlertDialog(
            onDismissRequest = { showEmergencyContacts = false },
            title = { Text("Emergency Contacts") },
            text = { Text("Configure emergency contacts and rescue team frequencies in device settings.") },
            confirmButton = {
                TextButton(onClick = { showEmergencyContacts = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun MessageItem(messageDisplay: MessageDisplay) {
    val message = messageDisplay.message
    val isSent = messageDisplay.isSent
    
    val textColor = when {
        message.isEmergency() -> SOSRed
        isSent -> SendTextBlue
        else -> ReceiveTextGreen
    }
    
    Text(
        text = "${if (isSent) "SENT" else "RECEIVED"}: ${message}",
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

data class MessageDisplay(
    val message: EmergencyMessage,
    val isSent: Boolean
)

private fun sendRegularMessage(
    message: String,
    service: SerialService?,
    onMessageSent: (EmergencyMessage) -> Unit
) {
    val emergencyMessage = EmergencyMessage(
        content = message,
        type = EmergencyMessage.MessageType.REGULAR
    )
    
    // Send via Bluetooth
    try {
        val messageData = formatMessageForTransmission(emergencyMessage)
        service?.write(messageData.toByteArray())
    } catch (e: IOException) {
        // Handle send error
    }
    
    onMessageSent(emergencyMessage)
}

private fun sendEmergencyMessage(
    message: String,
    service: SerialService?,
    locationManager: LocationManager,
    onLocationUpdate: (Double, Double) -> Unit,
    onMessageSent: (EmergencyMessage) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    locationManager.getCurrentLocation(object : LocationManager.LocationCallback {
        override fun onLocationReceived(latitude: Double, longitude: Double) {
            val emergencyMessage = EmergencyMessage(
                content = message,
                type = EmergencyMessage.MessageType.EMERGENCY,
                latitude = latitude,
                longitude = longitude
            )
            
            // Send via Bluetooth
            try {
                val messageData = formatMessageForTransmission(emergencyMessage)
                service?.write(messageData.toByteArray())
            } catch (e: IOException) {
                // Handle send error
            }
            
            onMessageSent(emergencyMessage)
            onLocationUpdate(latitude, longitude)
        }
        
        override fun onLocationError(error: String) {
            val emergencyMessage = EmergencyMessage(
                content = message,
                type = EmergencyMessage.MessageType.EMERGENCY
            )
            
            // Send via Bluetooth
            try {
                val messageData = formatMessageForTransmission(emergencyMessage)
                service?.write(messageData.toByteArray())
            } catch (e: IOException) {
                // Handle send error
            }
            
            onMessageSent(emergencyMessage)
            onStatusUpdate("Emergency sent without GPS: $error")
        }
    })
}

// SOS alert functionality removed - incorporated into emergency button

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isBluetoothEnabled(): Boolean {
    return android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
}

private fun formatMessageForTransmission(message: EmergencyMessage): String {
    return "${message.type.name}|${message.content}|${message.latitude}|${message.longitude}|${message.timestamp}"
}

private fun parseReceivedMessage(data: String): EmergencyMessage? {
    return try {
        val parts = data.trim().split("|")
        if (parts.size >= 5) {
            EmergencyMessage(
                content = parts[1],
                type = EmergencyMessage.MessageType.valueOf(parts[0]),
                latitude = parts[2].toDoubleOrNull() ?: 0.0,
                longitude = parts[3].toDoubleOrNull() ?: 0.0,
                timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
            )
        } else {
            // Fallback for simple text messages
            EmergencyMessage(
                content = data,
                type = EmergencyMessage.MessageType.REGULAR
            )
        }
    } catch (e: Exception) {
        null
    }
}