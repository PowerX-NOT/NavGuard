package com.navguard.app.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.navguard.app.EmergencyMessage
import com.navguard.app.LocationManager
import com.navguard.app.SerialListener
import com.navguard.app.SerialService
import com.navguard.app.ui.theme.*
import kotlinx.coroutines.delay
import java.util.*

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
    var sosPressed by remember { mutableStateOf(false) }
    var sosCountdown by remember { mutableStateOf(0) }
    
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
    
    // SOS countdown effect
    LaunchedEffect(sosPressed) {
        if (sosPressed) {
            sosCountdown = 5
            while (sosCountdown > 0 && sosPressed) {
                delay(1000)
                sosCountdown--
            }
            if (sosPressed && sosCountdown == 0) {
                triggerSosAlert(
                    locationManager = locationManager,
                    vibrator = vibrator,
                    onLocationUpdate = { lat, lon ->
                        locationText = "GPS: ${"%.6f".format(lat)}, ${"%.6f".format(lon)}"
                    },
                    onMessageSent = { message ->
                        messages = messages + MessageDisplay(message, true)
                    },
                    onStatusUpdate = { status ->
                        connectionStatus = status
                    }
                )
            }
        }
    }
    
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
            // Status Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Status: $connectionStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Text Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type emergency message...") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    sendRegularMessage(
                                        message = messageText,
                                        onMessageSent = { msg ->
                                            messages = messages + MessageDisplay(msg, true)
                                            messageText = ""
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Emergency Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                sendEmergencyMessage(
                                    message = messageText.ifBlank { "EMERGENCY: Need immediate assistance!" },
                                    locationManager = locationManager,
                                    onLocationUpdate = { lat, lon ->
                                        locationText = "GPS: ${"%.6f".format(lat)}, ${"%.6f".format(lon)}"
                                    },
                                    onMessageSent = { msg ->
                                        messages = messages + MessageDisplay(msg, true)
                                        messageText = ""
                                    },
                                    onStatusUpdate = { status ->
                                        connectionStatus = status
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF8C00)
                            )
                        ) {
                            Text("🚨 EMERGENCY", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = { /* SOS handled by gesture */ },
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            sosPressed = true
                                            tryAwaitRelease()
                                            sosPressed = false
                                            sosCountdown = 0
                                        }
                                    )
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sosPressed) SOSRed else EmergencyOrange
                            )
                        ) {
                            Text(
                                text = if (sosPressed) "Hold ($sosCountdown)" else "SOS",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Instructions
                    Text(
                        text = "Hold SOS button for 5 seconds to send GPS emergency alert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
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
    onMessageSent: (EmergencyMessage) -> Unit
) {
    val emergencyMessage = EmergencyMessage(
        content = message,
        type = EmergencyMessage.MessageType.REGULAR
    )
    onMessageSent(emergencyMessage)
}

private fun sendEmergencyMessage(
    message: String,
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
            onMessageSent(emergencyMessage)
            onLocationUpdate(latitude, longitude)
        }
        
        override fun onLocationError(error: String) {
            val emergencyMessage = EmergencyMessage(
                content = message,
                type = EmergencyMessage.MessageType.EMERGENCY
            )
            onMessageSent(emergencyMessage)
            onStatusUpdate("Emergency sent without GPS: $error")
        }
    })
}

private fun triggerSosAlert(
    locationManager: LocationManager,
    vibrator: Vibrator,
    onLocationUpdate: (Double, Double) -> Unit,
    onMessageSent: (EmergencyMessage) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    // Vibrate to indicate SOS activation
    vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
    
    onStatusUpdate("🚨 SOS ALERT ACTIVATED 🚨")
    
    locationManager.getCurrentLocation(object : LocationManager.LocationCallback {
        override fun onLocationReceived(latitude: Double, longitude: Double) {
            val sosMessage = "🚨 SOS ALERT 🚨 Emergency assistance needed immediately!"
            val sosMsg = EmergencyMessage(
                content = sosMessage,
                type = EmergencyMessage.MessageType.SOS,
                latitude = latitude,
                longitude = longitude
            )
            onMessageSent(sosMsg)
            onLocationUpdate(latitude, longitude)
            onStatusUpdate("SOS alert sent with GPS coordinates")
        }
        
        override fun onLocationError(error: String) {
            val sosMessage = "🚨 SOS ALERT 🚨 Emergency assistance needed immediately!"
            val sosMsg = EmergencyMessage(
                content = sosMessage,
                type = EmergencyMessage.MessageType.SOS
            )
            onMessageSent(sosMsg)
            onStatusUpdate("SOS alert sent without GPS: $error")
        }
    })
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isBluetoothEnabled(): Boolean {
    return android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
}