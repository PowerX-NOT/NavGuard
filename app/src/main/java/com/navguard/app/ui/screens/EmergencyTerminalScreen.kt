package com.navguard.app.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Vibrator
import android.util.Log
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.core.content.ContextCompat
import com.navguard.app.EmergencyMessage
import com.navguard.app.PersistenceManager
import com.navguard.app.MessageDisplay
import com.navguard.app.LocationManager
import com.navguard.app.LocationService
import com.navguard.app.SerialSocket
import com.navguard.app.SerialListener
import com.navguard.app.SerialService
import com.navguard.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.*
import java.io.IOException
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.compose.material.icons.filled.Bluetooth
import android.bluetooth.BluetoothProfile
import android.os.Build
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyTerminalScreen(
    deviceAddress: String,
    onNavigateBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenMapAt: (Double, Double, Boolean) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<MessageDisplay>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("No device connected") }
    var locationText by remember { mutableStateOf("GPS: Not available") }
    var isConnected by remember { mutableStateOf(false) }
    var showDeviceStatus by remember { mutableStateOf(false) }
    var showEmergencyContacts by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var isDisconnecting by remember { mutableStateOf(false) }
    var isLiveLocationSharing by remember { mutableStateOf(false) }
    var isReceivingLiveLocation by remember { mutableStateOf(false) }
    // No time limit; share until the user stops
    var lastLiveLat by remember { mutableStateOf<Double?>(null) }
    var lastLiveLon by remember { mutableStateOf<Double?>(null) }
    var lastLiveSentAtMs by remember { mutableStateOf(0L) }
    
    // Bluetooth connection state
    var service: SerialService? by remember { mutableStateOf(null) }
    var socket: SerialSocket? by remember { mutableStateOf(null) }
    
    val listState = rememberLazyListState()
    val locationManager = remember { LocationManager(context) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var isInputFocused by remember { mutableStateOf(false) }
    var liveLoopJob: Job? by remember { mutableStateOf(null) }
    
    // Chat persistence manager
    val chatManager = remember { PersistenceManager(context) }
    
    // Function to handle acknowledgment messages
    fun handleAcknowledgment(ackText: String) {
        try {
            val parts = ackText.trim().split("|")
            if (parts.size >= 3) {
                val ackIdPrefix = parts[1]
                val statusCode = parts[2].toIntOrNull()
                
                if (statusCode != null) {
                    val newStatus = EmergencyMessage.MessageStatus.values().find { it.code == statusCode }
                    if (newStatus != null) {
                        // Update status by matching messageId with prefix to support 6-char ACK IDs
                        messages = updateMessageStatusByPrefix(ackIdPrefix, newStatus, messages)
                        // Save updated messages
                        chatManager.saveMessages(deviceAddress, messages)
                    }
                }
            }
        } catch (e: Exception) {
            // Handle parsing error
        }
    }
    
    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            connectionStatus = "Storage permission needed for chat history"
        }
    }
    
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
                Toast.makeText(context, "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
            override fun onSerialRead(data: ByteArray) {
                val receivedText = String(data)
                // Check if this is an acknowledgment message
                if (receivedText.startsWith("ACK|")) {
                    handleAcknowledgment(receivedText)
                } else if (receivedText.startsWith("CTRL|LOC_STOP")) {
                    // Remote signaled to stop live location
                    isReceivingLiveLocation = false
                    chatManager.setLiveReceivingEnabled(deviceAddress, false)
                } else {
                    // Parse received emergency message
                    parseReceivedMessage(receivedText)?.let { msg ->
                        if (msg.content == "LOC" && msg.hasLocation()) {
                            // Receiver-side live location: show banner and center map link; do not add to chat
                            isReceivingLiveLocation = true
                            chatManager.setLiveReceivingEnabled(deviceAddress, true)
                            lastLiveLat = msg.latitude
                            lastLiveLon = msg.longitude
                            // Ack delivery
                            sendAcknowledgment(msg.messageId, EmergencyMessage.MessageStatus.DELIVERED, service)
                        } else {
                            val newMessages = messages + MessageDisplay(msg, false)
                            messages = newMessages
                            // Save messages immediately
                            chatManager.saveMessages(deviceAddress, newMessages)
                            // Ack delivery
                            sendAcknowledgment(msg.messageId, EmergencyMessage.MessageStatus.DELIVERED, service)
                        }
                    }
                }
            }
            
            override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
                val sb = StringBuilder()
                for (data in datas) {
                    sb.append(String(data))
                }
                val receivedText = sb.toString()
                
                // Check if this is an acknowledgment message
                if (receivedText.startsWith("ACK|")) {
                    handleAcknowledgment(receivedText)
                } else if (receivedText.startsWith("CTRL|LOC_STOP")) {
                    isReceivingLiveLocation = false
                    chatManager.setLiveReceivingEnabled(deviceAddress, false)
                } else {
                    // Parse received emergency message
                    parseReceivedMessage(receivedText)?.let { msg ->
                        if (msg.content == "LOC" && msg.hasLocation()) {
                            // Receiver-side live location: show banner and center map link; do not add to chat
                            isReceivingLiveLocation = true
                            chatManager.setLiveReceivingEnabled(deviceAddress, true)
                            lastLiveLat = msg.latitude
                            lastLiveLon = msg.longitude
                            // Ack delivery
                            sendAcknowledgment(msg.messageId, EmergencyMessage.MessageStatus.DELIVERED, service)
                        } else {
                            val newMessages = messages + MessageDisplay(msg, false)
                            messages = newMessages
                            // Save messages immediately
                            chatManager.saveMessages(deviceAddress, newMessages)
                            // Ack delivery
                            sendAcknowledgment(msg.messageId, EmergencyMessage.MessageStatus.DELIVERED, service)
                        }
                    }
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
    
    // Add disconnect logic
    fun disconnectFromDevice() {
        isDisconnecting = true
        service?.disconnect()
        socket = null
        isConnected = false
        connectionStatus = "No device connected"
        Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
        context.stopService(Intent(context, SerialService::class.java))
    }
    
    // Connect to Bluetooth device on screen load
    LaunchedEffect(deviceAddress, service) {
        // Check storage permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // Restore persisted live flags
        isLiveLocationSharing = chatManager.isLiveSharingEnabled(deviceAddress)
        isReceivingLiveLocation = chatManager.isLiveReceivingEnabled(deviceAddress)
        // Ensure service state matches persisted flag
        if (isLiveLocationSharing) {
            LocationService.startLocationSharing(context)
        }
        // Load existing messages
        messages = chatManager.loadMessages(deviceAddress)
        
        // Get initial location and start updates for GPS status
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            locationManager.getCurrentLocation(object : LocationManager.LocationCallback {
                override fun onLocationReceived(latitude: Double, longitude: Double) {
                    locationText = "GPS: ${String.format("%.6f°N", latitude)}, ${String.format("%.6f°E", longitude)}"
                }
                override fun onLocationError(error: String) {
                    locationText = "GPS: Error - $error"
                }
            })
            
            // Start continuous location updates for GPS status
            locationManager.requestLocationUpdates(
                object : LocationManager.LocationCallback {
                    override fun onLocationReceived(latitude: Double, longitude: Double) {
                        locationText = "GPS: ${String.format("%.6f°N", latitude)}, ${String.format("%.6f°E", longitude)}"
                    }
                    override fun onLocationError(error: String) {
                        locationText = "GPS: Error - $error"
                    }
                },
                minTimeMs = 5000L,
                minDistanceM = 5f
            )
        }
        
        try {
            // Start service in foreground mode before binding
            val intent = Intent(context, SerialService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            // Wait for service to be bound
            delay(500)
            service?.let { svc ->
                if (svc.connectedDeviceAddress == deviceAddress && svc.isConnected) {
                    // Already connected to the correct device, just attach listener
                    svc.attach(serialListener)
                    connectionStatus = "Connected to device"
                    isConnected = true // Ensure UI icon is green
                } else {
                    // If connected to a different device, disconnect first
                    if (svc.connectedDeviceAddress != null) {
                        svc.disconnect()
                        delay(300) // Give time for disconnect
                    }
                    // Get Bluetooth device and create socket
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                        socket = SerialSocket(context.applicationContext, device)
                        // Optimistically update UI
                        connectionStatus = "Connecting to ${device.name ?: deviceAddress}..."
                        // Wait for service to be ready
                        delay(500)
                        try {
                            svc.connect(socket!!)
                        } catch (e: Exception) {
                            connectionStatus = "Connection failed: ${e.message}"
                        }
                    } else {
                        connectionStatus = "Bluetooth not available"
                    }
                }
            } ?: run {
                connectionStatus = "Service not ready"
            }
        } catch (e: Exception) {
            connectionStatus = "Connection error: ${e.message}"
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            // Only detach listener and unbind service, do NOT disconnect
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
            delay(50) // Small delay for smoother animation
            listState.animateScrollToItem(messages.size - 1)
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
                            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
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
                        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
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
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
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
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat History")
                    }
                    IconButton(onClick = { showDeviceStatus = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Device Status")
                    }
                    IconButton(onClick = onOpenMap) {
                        Icon(Icons.Default.Map, contentDescription = "Open Map")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // Live location banner (non-chat UI)
            if (isLiveLocationSharing || isReceivingLiveLocation) {
                // Animated live indicator
                val pulse = rememberInfiniteTransition(label = "live-indicator").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF4CAF50).copy(alpha = pulse.value), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isReceivingLiveLocation && !isLiveLocationSharing) "Live location receiving" else "Live location sharing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lastLiveLat != null && lastLiveLon != null) {
                            TextButton(onClick = { onOpenMapAt(lastLiveLat!!, lastLiveLon!!, isReceivingLiveLocation) }) {
                                Text("Map")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        TextButton(onClick = {
                            isLiveLocationSharing = false
                            chatManager.setLiveSharingEnabled(deviceAddress, false)
                            isReceivingLiveLocation = false
                            chatManager.setLiveReceivingEnabled(deviceAddress, false)
                            lastLiveLat = null
                            lastLiveLon = null
                            locationManager.stopLocationUpdates()
                            // Notify peer to stop receiving
                            try {
                                service?.write("CTRL|LOC_STOP".toByteArray())
                            } catch (_: Exception) {}
                            // Stop foreground service sending
                            LocationService.stopLocationSharing(context)
                        }) {
                            Text("Stop", color = Color(0xFFD32F2F))
                        }
                    }
                }
            }
            // Compact Status Bar - Fixed to prevent flickering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp) // Fixed height to prevent layout shifts
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
                    imageVector = if (isConnectedState) Icons.Default.Send else Icons.Default.Delete,
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
                    .padding(vertical = 8.dp)
                    .animateContentSize(
                        animationSpec = tween(200)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Dismiss keyboard when tapping on messages area
                                focusManager.clearFocus()
                            }
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        // Empty state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "No messages",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No messages yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Send a message to start communicating",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Group messages by date and show date separators
                    val groupedMessages = messages.groupBy { getDateOnly(it.message.timestamp) }
                    groupedMessages.forEach { (date, dayMessages) ->
                        // Date separator
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = formatDateForDisplay(date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        
                        // Messages for this date
                        items(dayMessages) { messageDisplay ->
                            MessageItem(
                                messageDisplay = messageDisplay,
                                onMessageRead = { messageId ->
                                    // Send read acknowledgment
                                    sendAcknowledgment(messageId, EmergencyMessage.MessageStatus.READ, service)
                                }
                            )
                        }
                    }
                }
            }
            
            // Status indicator at bottom right (like modern chat apps)
            if (messages.isNotEmpty()) {
                val lastMessage = messages.last()
                if (lastMessage.isSent) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = getTimeOnly(lastMessage.message.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lastMessage.message.getStatusSymbol(),
                                style = MaterialTheme.typography.bodySmall,
                                color = when (lastMessage.message.status) {
                                    EmergencyMessage.MessageStatus.READ -> Color(0xFF2196F3)
                                    EmergencyMessage.MessageStatus.DELIVERED -> Color.Gray
                                    EmergencyMessage.MessageStatus.SENT -> Color.Gray
                                    EmergencyMessage.MessageStatus.SENDING -> Color.Gray.copy(alpha = 0.5f)
                                },
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🔒", // Encryption indicator
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            
            Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
            
            // Input Area with proper keyboard handling
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .imePadding()
                    .navigationBarsPadding()
                    .animateContentSize(
                        animationSpec = tween(300)
                    ),
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
                            onValueChange = { newText ->
                                val wasEmpty = messageText.isEmpty()
                                messageText = newText
                                // If user just started typing and there are messages, scroll to bottom
                                if (wasEmpty && newText.isNotEmpty() && messages.isNotEmpty()) {
                                    coroutineScope.launch {
                                        delay(100) // Small delay for smooth animation
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            },
                            interactionSource = remember { MutableInteractionSource() }.also { source ->
                                LaunchedEffect(source) {
                                    source.interactions.collect { interaction ->
                                        when (interaction) {
                                            is FocusInteraction.Focus -> {
                                                isInputFocused = true
                                                // Smooth scroll when input gets focus
                                                if (messages.isNotEmpty()) {
                                                    delay(150) // Delay for keyboard animation
                                                    listState.animateScrollToItem(messages.size - 1)
                                                }
                                            }
                                            is FocusInteraction.Unfocus -> {
                                                isInputFocused = false
                                            }
                                        }
                                    }
                                }
                            },
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
                            leadingIcon = {
                                // Live location toggle at the left edge of the text bar (no chat spam)
                                IconButton(
                                    onClick = {
                                        isLiveLocationSharing = !isLiveLocationSharing
                                        chatManager.setLiveSharingEnabled(deviceAddress, isLiveLocationSharing)
                                        if (isLiveLocationSharing) {
                                            lastLiveSentAtMs = 0L
                                            // Prime location locally for UI
                                            locationManager.getCurrentLocation(object : LocationManager.LocationCallback {
                                                override fun onLocationReceived(latitude: Double, longitude: Double) {
                                                    lastLiveLat = latitude
                                                    lastLiveLon = longitude
                                                    locationText = "GPS: ${String.format("%.6f°N", latitude)}, ${String.format("%.6f°E", longitude)}"
                                                }
                                                override fun onLocationError(error: String) {
                                                    locationText = "GPS: Error - $error"
                                                }
                                            })
                                            // Start foreground service responsible for continuous sending
                                            LocationService.startLocationSharing(context)
                                        } else {
                                            // Notify peer to stop receiving
                                            try { service?.write("CTRL|LOC_STOP".toByteArray()) } catch (_: Exception) {}
                                            LocationService.stopLocationSharing(context)
                                            locationManager.stopLocationUpdates()
                                            liveLoopJob?.cancel()
                                            liveLoopJob = null
                                        }
                                    }
                                ) {
                                    val tint = if (isLiveLocationSharing) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                    Icon(Icons.Default.LocationOn, contentDescription = "Share Live Location", tint = tint)
                                }
                            },
                            trailingIcon = {
                                // Regular send button on the right
                                IconButton(
                                    onClick = {
                                        if (messageText.isNotBlank()) {
                                            sendRegularMessage(
                                                message = messageText,
                                                service = service,
                                                chatManager = chatManager,
                                                deviceAddress = deviceAddress,
                                                onMessageSent = { msg ->
                                                    val newMessages = messages + MessageDisplay(msg, true)
                                                    messages = newMessages
                                                    chatManager.saveMessages(deviceAddress, newMessages)
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
                            }
                        )
                        
                        // Removed redundant action buttons row
                    }
                }
            }
        }
    }
    
    // Ensure GPS updates are stopped when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            locationManager.stopLocationUpdates()
        }
    }

    // Clear Chat Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat History") },
            text = { 
                Text("Are you sure you want to delete the chat history? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        messages = emptyList()
                        chatManager.clearMessages(deviceAddress)
                        showClearDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

// Helper function to get time only (HH:mm format)
private fun getTimeOnly(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// Helper function to get date only (yyyy-MM-dd format)
private fun getDateOnly(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// Helper function to format date for display
private fun formatDateForDisplay(dateString: String): String {
    val today = getDateOnly(System.currentTimeMillis())
    val yesterday = getDateOnly(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    
    return when (dateString) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.parse(dateString)
            val displaySdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            displaySdf.format(date!!)
        }
    }
}


private fun sendRegularMessage(
    message: String,
    service: SerialService?,
    chatManager: PersistenceManager,
    deviceAddress: String,
    onMessageSent: (EmergencyMessage) -> Unit
) {
    val emergencyMessage = EmergencyMessage(
        content = message,
        type = EmergencyMessage.MessageType.REGULAR,
        status = EmergencyMessage.MessageStatus.SENDING
    )
    
    // Send via Bluetooth
    try {
        val messageData = formatMessageForTransmission(emergencyMessage)
        service?.write(messageData.toByteArray())
        
        // Update status to sent after successful transmission
        emergencyMessage.updateStatus(EmergencyMessage.MessageStatus.SENT)
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
                longitude = longitude,
                status = EmergencyMessage.MessageStatus.SENDING
            )
            
            // Send via Bluetooth
            try {
                val messageData = formatMessageForTransmission(emergencyMessage)
                service?.write(messageData.toByteArray())
                
                // Update status to sent after successful transmission
                emergencyMessage.updateStatus(EmergencyMessage.MessageStatus.SENT)
            } catch (e: IOException) {
                // Handle send error
            }
            
            onMessageSent(emergencyMessage)
            onLocationUpdate(latitude, longitude)
        }
        
        override fun onLocationError(error: String) {
            val emergencyMessage = EmergencyMessage(
                content = message,
                type = EmergencyMessage.MessageType.EMERGENCY,
                status = EmergencyMessage.MessageStatus.SENDING
            )
            
            // Send via Bluetooth
            try {
                val messageData = formatMessageForTransmission(emergencyMessage)
                service?.write(messageData.toByteArray())
                
                // Update status to sent after successful transmission
                emergencyMessage.updateStatus(EmergencyMessage.MessageStatus.SENT)
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
    // Remove timestamp from transmission to reduce payload size
    // New format (6 parts): TYPE|CONTENT|LAT|LON|ID|STATUS
    return "${message.type.name}|${message.content}|${message.latitude}|${message.longitude}|${message.messageId}|${message.status.code}"
}

private fun parseReceivedMessage(data: String): EmergencyMessage? {
    return try {
        val parts = data.trim().split("|")
        if (parts.size >= 7) {
            // Legacy full format with timestamp and status: TYPE|CONTENT|LAT|LON|TS|ID|STATUS
            val content = parts[1]
            if (content.isBlank()) return null
            EmergencyMessage(
                content = content,
                type = EmergencyMessage.MessageType.valueOf(parts[0]),
                latitude = parts[2].toDoubleOrNull() ?: 0.0,
                longitude = parts[3].toDoubleOrNull() ?: 0.0,
                timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                messageId = parts[5],
                status = EmergencyMessage.MessageStatus.values().find { it.code == parts[6].toIntOrNull() } ?: EmergencyMessage.MessageStatus.SENDING
            )
        } else if (parts.size == 6) {
            // New compact format with status: TYPE|CONTENT|LAT|LON|ID|STATUS
            val content = parts[1]
            if (content.isBlank()) return null
            EmergencyMessage(
                content = content,
                type = EmergencyMessage.MessageType.valueOf(parts[0]),
                latitude = parts[2].toDoubleOrNull() ?: 0.0,
                longitude = parts[3].toDoubleOrNull() ?: 0.0,
                // No timestamp transmitted; use now
                timestamp = System.currentTimeMillis(),
                messageId = parts[4],
                status = EmergencyMessage.MessageStatus.values().find { it.code == parts[5].toIntOrNull() } ?: EmergencyMessage.MessageStatus.SENDING
            )
        } else if (parts.size == 5) {
            // Ambiguous 5-part legacy: could be TYPE|CONTENT|LAT|LON|ID or TYPE|CONTENT|LAT|LON|TS
            val content = parts[1]
            if (content.isBlank()) return null
            val lastPart = parts[4]
            val isAllDigits = lastPart.all { it.isDigit() }
            val looksLikeTimestamp = isAllDigits && lastPart.length >= 12
            if (looksLikeTimestamp) {
                EmergencyMessage(
                    content = content,
                    type = EmergencyMessage.MessageType.valueOf(parts[0]),
                    latitude = parts[2].toDoubleOrNull() ?: 0.0,
                    longitude = parts[3].toDoubleOrNull() ?: 0.0,
                    timestamp = lastPart.toLongOrNull() ?: System.currentTimeMillis(),
                    status = EmergencyMessage.MessageStatus.SENDING
                )
            } else {
                EmergencyMessage(
                    content = content,
                    type = EmergencyMessage.MessageType.valueOf(parts[0]),
                    latitude = parts[2].toDoubleOrNull() ?: 0.0,
                    longitude = parts[3].toDoubleOrNull() ?: 0.0,
                    timestamp = System.currentTimeMillis(),
                    messageId = lastPart,
                    status = EmergencyMessage.MessageStatus.SENDING
                )
            }
        } else {
            // Simple text message
            val trimmedData = data.trim()
            if (trimmedData.isBlank()) {
                return null
            }
            EmergencyMessage(
                content = trimmedData,
                type = EmergencyMessage.MessageType.REGULAR,
                status = EmergencyMessage.MessageStatus.SENDING
            )
        }
    } catch (e: Exception) {
        null
    }
}

// Send a lightweight location update using existing message format
private fun sendLocationUpdate(
    latitude: Double,
    longitude: Double,
    service: SerialService?,
    chatManager: PersistenceManager,
    deviceAddress: String,
    onMessageSent: (EmergencyMessage) -> Unit
) {
    val msg = EmergencyMessage(
        content = "LOC",
        type = EmergencyMessage.MessageType.REGULAR,
        latitude = latitude,
        longitude = longitude,
        status = EmergencyMessage.MessageStatus.SENDING
    )
    try {
        val data = formatMessageForTransmission(msg)
        service?.write(data.toByteArray())
        msg.updateStatus(EmergencyMessage.MessageStatus.SENT)
    } catch (_: IOException) {
    }
    onMessageSent(msg)
}

// Throttled location message (no chat persistence, no UI spam)
private fun sendLocationUpdateThrottled(
    latitude: Double,
    longitude: Double,
    service: SerialService?
) {
    // Limit messages to reduce radio usage (handled at caller by timing)
    val msg = EmergencyMessage(
        content = "LOC",
        type = EmergencyMessage.MessageType.REGULAR,
        latitude = latitude,
        longitude = longitude,
        status = EmergencyMessage.MessageStatus.SENDING
    )
    try {
        val data = "${msg.type.name}|${msg.content}|${msg.latitude}|${msg.longitude}|${msg.messageId}|${msg.status.code}"
        service?.write(data.toByteArray())
    } catch (_: IOException) {
    }
}

// Function to send acknowledgment messages
private fun sendAcknowledgment(
    messageId: String,
    status: EmergencyMessage.MessageStatus,
    service: SerialService?
) {
    try {
        val ackMessage = "ACK|$messageId|${status.code}"
        service?.write(ackMessage.toByteArray())
    } catch (e: IOException) {
        // Handle send error
    }
}

// Function to update message status in the list
private fun updateMessageStatus(
    messageId: String,
    newStatus: EmergencyMessage.MessageStatus,
    messages: List<MessageDisplay>
): List<MessageDisplay> {
    return updateMessageStatusByPrefix(messageId, newStatus, messages)
}

private fun updateMessageStatusByPrefix(
    messageIdPrefix: String,
    newStatus: EmergencyMessage.MessageStatus,
    messages: List<MessageDisplay>
): List<MessageDisplay> {
    return messages.map { messageDisplay ->
        val mid = messageDisplay.message.messageId
        if (mid.startsWith(messageIdPrefix)) {
            messageDisplay.copy(
                message = messageDisplay.message.copy(status = newStatus)
            )
        } else {
            messageDisplay
        }
    }
}

// Helper function to detect URLs in text
private fun extractUrls(text: String): List<Pair<IntRange, String>> {
    val urlPattern = Regex("""https?://[^\s]+""")
    return urlPattern.findAll(text).map { match ->
        match.range to match.value
    }.toList()
}

// Helper function to create clickable text with links
@Composable
private fun ClickableTextWithLinks(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val urls = extractUrls(text)
    
    if (urls.isEmpty()) {
        // No URLs found, display as regular text
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = modifier
        )
    } else {
        // URLs found, create clickable text
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            urls.forEach { (range, url) ->
                // Add text before the URL
                if (range.first > lastIndex) {
                    append(text.substring(lastIndex, range.first))
                }
                // Add the URL with clickable annotation
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF2196F3), // Blue color for links
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
                lastIndex = range.last + 1
            }
            // Add remaining text after the last URL
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
        
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall.copy(color = color),
            modifier = modifier,
            onClick = { offset ->
                urls.forEach { (range, url) ->
                    if (offset in range) {
                        uriHandler.openUri(url)
                    }
                }
            }
        )
    }
}

@Composable
fun MessageItem(
    messageDisplay: MessageDisplay,
    onMessageRead: (String) -> Unit
) {
    val message = messageDisplay.message
    val isSent = messageDisplay.isSent
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    // State to track if timestamp is visible for this message
    var showTimestamp by remember { mutableStateOf(false) }
    
    // Don't display message if content is empty
    if (message.content.isBlank()) {
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        // Modern chat bubble design
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .widthIn(max = 280.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clickable(
                    onClick = {
                        // Toggle timestamp visibility
                        showTimestamp = !showTimestamp
                        // Mark received message as read if needed
                        if (!isSent && !message.isRead()) {
                            message.updateStatus(EmergencyMessage.MessageStatus.READ)
                            // Send read acknowledgment
                            onMessageRead(message.messageId)
                        }
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.isEmergency() -> Color(0xFFFF5252) // Red for emergency
                    isSent -> Color(0xFF2B2B2B) // Dark gray for sent messages
                    else -> Color(0xFF424242) // Lighter gray for received messages
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .wrapContentWidth()
            ) {
                // Message content with clickable links
                ClickableTextWithLinks(
                    text = message.content,
                    color = Color.White, // White text for all messages
                    modifier = Modifier.wrapContentWidth()
                )
                
                // Location info if available (modern style)
                if (message.hasLocation()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .wrapContentWidth()
                            .clickable {
                                val url = message.getGoogleMapsUrl()
                                if (url.isNotEmpty()) {
                                    uriHandler.openUri(url)
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📍 Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Emergency indicator (modern style)
                if (message.isEmergency()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "🚨 EMERGENCY",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                
                // Remove status indicators from inside bubbles - they'll be shown at chat level
            }
        }
        
        // Show timestamp below bubble when clicked
        if (showTimestamp) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = getTimeOnly(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(if (isSent) Alignment.End else Alignment.Start)
                    .padding(horizontal = 12.dp)
            )
        }
    }
}