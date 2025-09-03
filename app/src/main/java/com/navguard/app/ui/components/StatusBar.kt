package com.navguard.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Common status bar component for NavGuard app
 * Shows connection status, GPS status, and live location information
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    // Connection status
    isConnected: Boolean = false,
    connectionStatus: String = "No device connected",
    // GPS status
    locationText: String = "GPS: Not available",
    // Live location sharing
    isLiveLocationSharing: Boolean = false,
    isReceivingLiveLocation: Boolean = false,
    isNavICSource: Boolean = false,
    lastLiveLat: Double? = null,
    lastLiveLon: Double? = null,
    // Signal tracking
    isReceivingSignal: Boolean = false,
    lastSignalRssi: String? = null,
    lastSignalSnr: String? = null,
    // Actions
    onMapClick: (() -> Unit)? = null,
    onStopLiveLocation: (() -> Unit)? = null,
    onTrackSignal: (() -> Unit)? = null,
    // Display options
    showCompactStatus: Boolean = true,
    showLiveBanner: Boolean = true,
    showSignalBanner: Boolean = true
) {
    Column(modifier = modifier) {
        // Signal receiving banner
        if (showSignalBanner && isReceivingSignal) {
            SignalReceivingBanner(
                lastSignalRssi = lastSignalRssi,
                lastSignalSnr = lastSignalSnr,
                onTrackSignal = onTrackSignal
            )
        }
        
        // Live location banner
        if (showLiveBanner && (isLiveLocationSharing || isReceivingLiveLocation)) {
            LiveLocationBanner(
                isLiveLocationSharing = isLiveLocationSharing,
                isReceivingLiveLocation = isReceivingLiveLocation,
                isNavICSource = isNavICSource,
                lastLiveLat = lastLiveLat,
                lastLiveLon = lastLiveLon,
                onMapClick = onMapClick,
                onStopLiveLocation = onStopLiveLocation
            )
        }
        
        // Compact status bar
        if (showCompactStatus) {
            CompactStatusBar(
                isConnected = isConnected,
                connectionStatus = connectionStatus,
                locationText = locationText
            )
        }
    }
}

@Composable
private fun SignalReceivingBanner(
    lastSignalRssi: String?,
    lastSignalSnr: String?,
    onTrackSignal: (() -> Unit)?
) {
    // Animated signal indicator
    val pulse = rememberInfiniteTransition(label = "signal-indicator").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(
                color = Color(0xFFFF5722).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(0xFFFF5722).copy(alpha = pulse.value), shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.SignalWifi4Bar,
                contentDescription = null,
                tint = Color(0xFFFF5722)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Signal is receiving",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (lastSignalRssi != null && lastSignalSnr != null) {
                    Text(
                        text = "RSSI: ${lastSignalRssi}dBm • SNR: ${lastSignalSnr}dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        if (onTrackSignal != null) {
            TextButton(
                onClick = onTrackSignal,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFFF5722)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Track")
            }
        }
    }
}

@Composable
private fun LiveLocationBanner(
    isLiveLocationSharing: Boolean,
    isReceivingLiveLocation: Boolean,
    isNavICSource: Boolean,
    lastLiveLat: Double?,
    lastLiveLon: Double?,
    onMapClick: (() -> Unit)?,
    onStopLiveLocation: (() -> Unit)?
) {
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
            Column {
                Text(
                    text = when {
                        isReceivingLiveLocation && !isLiveLocationSharing -> {
                            if (isNavICSource) "Live location receiving (NavIC)" else "Live location receiving"
                        }
                        else -> "Live location sharing"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Display sender's coordinates when receiving live location
                if (isReceivingLiveLocation && lastLiveLat != null && lastLiveLon != null) {
                    Text(
                        text = "Sender: ${String.format("%.6f", lastLiveLat)}, ${String.format("%.6f", lastLiveLon)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (lastLiveLat != null && lastLiveLon != null && onMapClick != null) {
                TextButton(onClick = onMapClick) {
                    Text("Map")
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (onStopLiveLocation != null) {
                TextButton(onClick = onStopLiveLocation) {
                    Text("Stop", color = Color(0xFFD32F2F))
                }
            }
        }
    }
}

@Composable
private fun CompactStatusBar(
    isConnected: Boolean,
    connectionStatus: String,
    locationText: String
) {
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
        val statusColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722)
        
        Icon(
            imageVector = if (isConnected) Icons.Default.Send else Icons.Default.Delete,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(14.dp)
        )
        
        Text(
            text = connectionStatus,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
                .padding(start = 8.dp, end = 4.dp)
        )
        
        Text(
            text = locationText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Utility functions for coordinate formatting
 */
object StatusBarUtils {
    fun formatCoordShort(value: Double): String = String.format("%.3f", value)
    
    fun formatDistanceShort(meters: Double): String {
        return if (meters < 1000) "${meters.toInt()}m" else String.format("%.1fkm", meters / 1000.0)
    }
    
    fun buildLocationInfo(
        senderLat: Double?,
        senderLon: Double?,
        currentLat: Double?,
        currentLon: Double?,
        distanceMeters: Double?
    ): String {
        return buildString {
            if (senderLat != null && senderLon != null) {
                append("S:")
                append(formatCoordShort(senderLat))
                append(',')
                append(formatCoordShort(senderLon))
                if (distanceMeters != null) {
                    append(" • D:")
                    append(formatDistanceShort(distanceMeters))
                }
            }
        }
    }
    
    fun getSignalStrength(rssi: String?): String {
        return when {
            rssi == null -> "Unknown"
            rssi.toIntOrNull() == null -> "Invalid"
            rssi.toInt() >= -50 -> "Excellent"
            rssi.toInt() >= -70 -> "Good"
            rssi.toInt() >= -85 -> "Fair"
            rssi.toInt() >= -100 -> "Poor"
            else -> "Very Poor"
        }
    }
    
    fun getSignalQuality(snr: String?): String {
        return when {
            snr == null -> "Unknown"
            snr.toIntOrNull() == null -> "Invalid"
            snr.toInt() >= 10 -> "Excellent"
            snr.toInt() >= 5 -> "Good"
            snr.toInt() >= 0 -> "Fair"
            snr.toInt() >= -5 -> "Poor"
            else -> "Very Poor"
        }
    }
}
