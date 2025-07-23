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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.core.content.ContextCompat
import com.navguard.app.EmergencyMessage
import com.navguard.app.ChatPersistenceManager
import com.navguard.app.MessageDisplay
import com.navguard.app.LocationManager
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyTerminalScreen(
    deviceAddress: String,
    onNavigateBack: () -> Unit
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
    
    // Bluetooth connection state
    var service: SerialService? by remember { mutableStateOf(null) }
    var socket: SerialSocket? by remember { mutableStateOf(null) }
    
    val listState = rememberLazyListState()
    val locationManager = remember { LocationManager(context) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var isInputFocused by remember { mutableStateOf(false) }
    
    // Chat persistence manager
    val chatManager = remember { ChatPersistenceManager(context) }
    
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
            }
            
            override fun onSerialRead(data: ByteArray) {
                val receivedText = String(data)
                // Parse received emergency message
                parseReceivedMessage(receivedText)?.let { msg ->
                    val newMessages = messages + MessageDisplay(msg, false)
                    messages = newMessages
                    // Save messages immediately
                    chatManager.saveMessages(deviceAddress, newMessages)
                }
            }
            
            override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
                val sb = StringBuilder()
                for (data in datas) {
                    sb.append(String(data))
                }
                parseReceivedMessage(sb.toString())?.let { msg ->
                    val newMessages = messages + MessageDisplay(msg, false)
                    messages = newMessages
                    // Save messages immediately
                    chatManager.saveMessages(deviceAddress, newMessages)
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
        // Check storage permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        // Load existing messages
        messages = chatManager.loadMessages(deviceAddress)
        
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
                        connectionStatus = device?.let { "Connected to ${it.name ?: it.address}" } ?: "Connected"
                        Toast.makeText(context, connectionStatus, Toast.LENGTH_SHORT).show()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (connectedDevice?.address == device?.address) {
                            connectedDevice = null
                        }
                        connectionStatus = "No device connected"
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
    // On launch, check for already connected device
    LaunchedEffect(Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val connected = bluetoothAdapter?.bondedDevices?.firstOrNull { device ->
            try {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
        }
        connectedDevice = connected
        connectionStatus = connected?.let { "Connected to ${it.name ?: it.address}" } ?: "No device connected"
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
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
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
                            MessageItem(messageDisplay)
                        }
                    }
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            
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
                            trailingIcon = {
                                Row {
                                    // Regular send button
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
                                                    val newMessages = messages + MessageDisplay(msg, true)
                                                    messages = newMessages
                                                    chatManager.saveMessages(deviceAddress, newMessages)
                                                    messages = newMessages
                                                    chatManager.saveMessages(deviceAddress, newMessages)
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

@Composable
fun MessageItem(messageDisplay: MessageDisplay) {
    val message = messageDisplay.message
    val isSent = messageDisplay.isSent
    val uriHandler = LocalUriHandler.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        // Message bubble with dynamic width based on text length
        Card(
            modifier = Modifier
                .widthIn(
                    min = 60.dp,
                    max = when {
                        message.content.length > 150 -> 350.dp
                        message.content.length > 100 -> 300.dp
                        message.content.length > 50 -> 250.dp
                        message.content.length > 20 -> 180.dp
                        message.content.length > 10 -> 120.dp
                        else -> 80.dp
                    }
                )
                .padding(horizontal = 6.dp),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isSent) 12.dp else 4.dp,
                bottomEnd = if (isSent) 4.dp else 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.isEmergency() -> Color(0xFFFFEBEE) // Light red for emergency
                    isSent -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // Message content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        message.isEmergency() -> Color(0xFFD32F2F) // Dark red for emergency text
                        isSent -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Location info if available (compact)
                if (message.hasLocation()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Location",
                            tint = Color(0xFF2196F3).copy(alpha = 0.8f), // Blue color
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val url = message.getGoogleMapsUrl()
                                    if (url.isNotEmpty()) {
                                        uriHandler.openUri(url)
                                    }
                                }
                        ) {
                            Text(
                                text = message.getLocationDisplayText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2196F3).copy(alpha = 0.9f), // Blue hyperlink color
                                fontSize = 10.sp,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in Maps",
                                tint = Color(0xFF2196F3).copy(alpha = 0.7f), // Blue color
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
                
                // Emergency indicator (compact)
                if (message.isEmergency()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "EMERGENCY",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Timestamp inside the bubble
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = getTimeOnly(message.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            message.isEmergency() -> Color(0xFFD32F2F).copy(alpha = 0.9f) // Dark red for emergency
                            isSent -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
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
    chatManager: ChatPersistenceManager,
    deviceAddress: String,
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