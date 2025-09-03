package com.navguard.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navguard.app.SerialBus
import com.navguard.app.ui.components.StatusBarUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrecisionFindingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var currentRssi by remember { mutableStateOf<String?>(null) }
    var currentSnr by remember { mutableStateOf<String?>(null) }
    var signalHistory by remember { mutableStateOf<List<SignalReading>>(emptyList()) }
    var isReceivingSignal by remember { mutableStateOf(false) }
    var lastSignalTime by remember { mutableStateOf(0L) }
    
    // Animation states
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )
    
    val rotationAnimation = rememberInfiniteTransition(label = "rotation")
    val rotation by rotationAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Listen for signal data from SerialBus
    LaunchedEffect(Unit) {
        SerialBus.events.collect { message ->
            if (message.startsWith("SIGNAL|")) {
                val parts = message.split("|")
                if (parts.size >= 4) {
                    val rssi = parts[2]
                    val snr = parts[3]
                    currentRssi = rssi
                    currentSnr = snr
                    isReceivingSignal = true
                    lastSignalTime = System.currentTimeMillis()
                    
                    // Add to history (keep last 20 readings)
                    val newReading = SignalReading(
                        timestamp = System.currentTimeMillis(),
                        rssi = rssi.toIntOrNull() ?: -999,
                        snr = snr.toIntOrNull() ?: -999
                    )
                    signalHistory = (signalHistory + newReading).takeLast(20)
                }
            }
        }
    }
    
    // Check if signal is still active
    LaunchedEffect(lastSignalTime) {
        if (lastSignalTime > 0) {
            delay(5000) // 5 seconds timeout
            if (System.currentTimeMillis() - lastSignalTime > 5000) {
                isReceivingSignal = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Precision Finding") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Signal Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (isReceivingSignal) Color(0xFF4CAF50).copy(alpha = pulseAlpha)
                                    else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReceivingSignal) "Signal Active" else "No Signal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceivingSignal) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                    
                    if (currentRssi != null && currentSnr != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SignalMetric("RSSI", currentRssi!!, "dBm")
                            SignalMetric("SNR", currentSnr!!, "dB")
                        }
                    }
                }
            }

            // Radar Display
            Card(
                modifier = Modifier
                    .size(280.dp)
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Radar background
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawRadarBackground(this, isReceivingSignal, rotation)
                    }
                    
                    // Signal strength indicator
                    if (isReceivingSignal && currentRssi != null) {
                        val rssiValue = currentRssi!!.toIntOrNull() ?: -999
                        val signalStrength = getSignalStrengthLevel(rssiValue)
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalWifi4Bar,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = getSignalColor(signalStrength)
                            )
                            Text(
                                text = StatusBarUtils.getSignalStrength(currentRssi),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = getSignalColor(signalStrength)
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalWifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Text(
                                text = "Searching...",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Signal Quality Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QualityIndicator(
                    title = "Signal Strength",
                    value = StatusBarUtils.getSignalStrength(currentRssi),
                    color = if (currentRssi != null) getSignalColor(getSignalStrengthLevel(currentRssi!!.toIntOrNull() ?: -999)) else Color.Gray
                )
                QualityIndicator(
                    title = "Signal Quality",
                    value = StatusBarUtils.getSignalQuality(currentSnr),
                    color = if (currentSnr != null) getQualityColor(currentSnr!!.toIntOrNull() ?: -999) else Color.Gray
                )
            }

            // Direction Guidance
            if (isReceivingSignal && currentRssi != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val rssiValue = currentRssi!!.toIntOrNull() ?: -999
                        val guidance = getDirectionGuidance(rssiValue, signalHistory)
                        
                        Icon(
                            imageVector = guidance.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = guidance.color
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = guidance.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        if (guidance.distance.isNotEmpty()) {
                            Text(
                                text = guidance.distance,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalMetric(
    label: String,
    value: String,
    unit: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$value$unit",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QualityIndicator(
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier.width(120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun drawRadarBackground(
    drawScope: DrawScope,
    isActive: Boolean,
    rotation: Float
) {
    val center = Offset(drawScope.size.width / 2, drawScope.size.height / 2)
    val radius = minOf(drawScope.size.width, drawScope.size.height) / 2f - 60f
    
    // Draw concentric circles
    val circles = 4
    for (i in 1..circles) {
        val circleRadius = radius * i / circles
        drawScope.drawCircle(
            color = if (isActive) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f),
            radius = circleRadius,
            center = center,
            style = Stroke(width = 6f)
        )
    }
    
    // Draw cross lines
    drawScope.drawLine(
        color = if (isActive) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = 6f
    )
    drawScope.drawLine(
        color = if (isActive) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = 6f
    )
    
    // Draw sweeping line (radar sweep)
    if (isActive) {
        val sweepAngle = Math.toRadians(rotation.toDouble())
        val sweepEnd = Offset(
            center.x + (radius * cos(sweepAngle)).toFloat(),
            center.y + (radius * sin(sweepAngle)).toFloat()
        )
        drawScope.drawLine(
            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
            start = center,
            end = sweepEnd,
            strokeWidth = 9f
        )
    }
}

private fun getSignalStrengthLevel(rssi: Int): Int {
    return when {
        rssi >= -50 -> 5 // Excellent
        rssi >= -70 -> 4 // Good
        rssi >= -85 -> 3 // Fair
        rssi >= -100 -> 2 // Poor
        else -> 1 // Very Poor
    }
}

private fun getSignalColor(level: Int): Color {
    return when (level) {
        5 -> Color(0xFF4CAF50) // Green
        4 -> Color(0xFF8BC34A) // Light Green
        3 -> Color(0xFFFFC107) // Amber
        2 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFFF5722) // Red
    }
}

private fun getQualityColor(snr: Int): Color {
    return when {
        snr >= 10 -> Color(0xFF4CAF50) // Green
        snr >= 5 -> Color(0xFF8BC34A) // Light Green
        snr >= 0 -> Color(0xFFFFC107) // Amber
        snr >= -5 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFFF5722) // Red
    }
}

private fun getDirectionGuidance(
    currentRssi: Int,
    history: List<SignalReading>
): DirectionGuidance {
    if (history.size < 3) {
        return DirectionGuidance(
            icon = Icons.Default.Search,
            message = "Gathering signal data...",
            distance = "",
            color = Color.Gray
        )
    }
    
    val recentReadings = history.takeLast(5)
    val trend = recentReadings.last().rssi - recentReadings.first().rssi
    
    return when {
        currentRssi >= -50 -> DirectionGuidance(
            icon = Icons.Default.CheckCircle,
            message = "Very close! Device should be nearby.",
            distance = "< 5 meters",
            color = Color(0xFF4CAF50)
        )
        currentRssi >= -70 -> DirectionGuidance(
            icon = Icons.Default.NearMe,
            message = if (trend > 0) "Getting closer! Keep moving in this direction." 
                     else "Signal weakening. Try a different direction.",
            distance = "5-20 meters",
            color = Color(0xFF8BC34A)
        )
        currentRssi >= -85 -> DirectionGuidance(
            icon = Icons.Default.Explore,
            message = if (trend > 0) "On the right track. Continue this way."
                     else "Signal getting weaker. Turn around.",
            distance = "20-50 meters",
            color = Color(0xFFFFC107)
        )
        currentRssi >= -100 -> DirectionGuidance(
            icon = Icons.Default.TravelExplore,
            message = "Device is far. Move around to find stronger signal.",
            distance = "50-100 meters",
            color = Color(0xFFFF9800)
        )
        else -> DirectionGuidance(
            icon = Icons.Default.SearchOff,
            message = "Signal very weak. Device may be very far or blocked.",
            distance = "> 100 meters",
            color = Color(0xFFFF5722)
        )
    }
}

data class SignalReading(
    val timestamp: Long,
    val rssi: Int,
    val snr: Int
)

data class DirectionGuidance(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val message: String,
    val distance: String,
    val color: Color
)
