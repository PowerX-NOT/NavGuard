package com.navguard.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.FileInputStream
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.res.stringResource
import com.navguard.app.R
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import com.navguard.app.PersistenceManager
import com.navguard.app.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.core.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.unit.Density
import com.navguard.app.SerialBus
import kotlinx.coroutines.flow.collect
import kotlin.math.*

// Compute Haversine distance in meters between two LatLong points
private fun distanceMeters(a: LatLong, b: LatLong): Double {
    val R = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val h = sinDLat * sinDLat + sinDLon * sinDLon * cos(lat1) * cos(lat2)
    val c = 2 * atan2(sqrt(h), sqrt(1 - h))
    return R * c
}

private fun formatDistance(meters: Double): String {
    return if (meters < 1000) "${meters.roundToInt()} m" else String.format("%.2f km", meters / 1000.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    onNavigateBack: () -> Unit,
    initialCenter: LatLong? = null,
    isLiveLocationFromOther: Boolean = false
) {
    val context = LocalContext.current
    val persistence = remember { PersistenceManager(context) }
    var mapUri by remember { mutableStateOf<Uri?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapKey by remember { mutableStateOf(0) } // Key to force view recreation
    
    // On first launch, try to load the saved URI
    LaunchedEffect(Unit) {
        val uriString = persistence.getOfflineMapUri()
        if (uriString != null) {
            try {
                mapUri = Uri.parse(uriString)
            } catch (_: Exception) {}
        }
    }
    val openMapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                // Clear existing map view to force reload
                mapView?.destroy()
                mapView = null
                mapUri = uri
                mapKey++ // Force view recreation
                // Persist the URI
                persistence.setOfflineMapUri(uri.toString())
                // Persist URI permission
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
        }
    )

    val locationManager = remember { LocationManager(context) }
    var centerLatLong by remember { mutableStateOf<LatLong?>(initialCenter) }
    // Track latest live location from the other party when viewing their location
    var senderLatLong by remember { mutableStateOf<LatLong?>(if (isLiveLocationFromOther) initialCenter else null) }
    var isLocating by remember { mutableStateOf(centerLatLong == null) }
    // On first launch, try to get current location if not provided
    LaunchedEffect(Unit) {
        if (centerLatLong == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.getCurrentLocation(object : LocationManager.LocationCallback {
                    override fun onLocationReceived(latitude: Double, longitude: Double) {
                        centerLatLong = LatLong(latitude, longitude)
                        isLocating = false
                    }
                    override fun onLocationError(error: String) {
                        centerLatLong = null
                        isLocating = false
                    }
                })
            } else {
                centerLatLong = null
                isLocating = false
            }
        } else {
            isLocating = false
        }
    }

    // Use continuous location updates for dynamic marker movement
    DisposableEffect(Unit) {
        var disposed = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Request frequent location updates for smooth real-time tracking on map
            locationManager.requestLocationUpdates(object : LocationManager.LocationCallback {
                override fun onLocationReceived(latitude: Double, longitude: Double) {
                    if (!disposed) {
                        centerLatLong = LatLong(latitude, longitude)
                        isLocating = false
                    }
                }
                override fun onLocationError(error: String) {
                    if (!disposed) {
                        centerLatLong = null
                        isLocating = false
                    }
                }
            }, minTimeMs = 1000L, minDistanceM = 1f) // Update every 1 second or 1 meter for smooth tracking
        } else {
            centerLatLong = null
            isLocating = false
        }
        onDispose {
            disposed = true
            locationManager.stopLocationUpdates()
        }
    }
    
    // Cleanup map view when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            mapView?.destroy()
            mapView = null
        }
    }
    
    // No URI handling; we always load bundled world.map from assets

    var markerRef by remember { mutableStateOf<Marker?>(null) }
    var blueMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var hasCenteredMap by remember { mutableStateOf(false) }

    // When viewing a received live location, listen for SerialBus messages to update sender coordinates
    LaunchedEffect(isLiveLocationFromOther) {
        if (isLiveLocationFromOther) {
            SerialBus.events.collect { raw ->
                val text = raw.trim()
                if (text.startsWith("ACK|")) return@collect
                if (text.startsWith("CTRL|LOC_STOP")) {
                    senderLatLong = null
                    return@collect
                }
                val parts = text.split("|")
                if (parts.size >= 6) {
                    val content = parts[1]
                    val lat = parts[2].toDoubleOrNull()
                    val lon = parts[3].toDoubleOrNull()
                    if (content == "LOC" && lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                        senderLatLong = LatLong(lat, lon)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(id = R.string.offline_map))
                        if (isLiveLocationFromOther && senderLatLong != null) {
                            Row {
                                Text(
                                    text = "Sender: ${String.format("%.6f", senderLatLong!!.latitude)}, ${String.format("%.6f", senderLatLong!!.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (centerLatLong != null) {
                                    val dist = distanceMeters(centerLatLong!!, senderLatLong!!)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Distance: ${formatDistance(dist)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLocating) {
                // Show loading indicator while fetching location
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                var loadError by remember { mutableStateOf(false) }
                if (loadError) {
                    // If error, show simple fallback
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Failed to load world.map")
                    }
                } else {
                    key(mapKey) { // Force recreation when map changes
                        AndroidView(
                            factory = { ctx ->
                            try {
                                AndroidGraphicFactory.createInstance(ctx.applicationContext)
                                val mv = MapView(ctx)
                                mapView = mv
                                
                                // Configure zoom levels and constraints for smooth zooming
                                mv.model.mapViewPosition.setZoomLevelMax(20.toByte())
                                mv.model.mapViewPosition.setZoomLevelMin(5.toByte())
                                
                                // Configure smooth zoom animation settings
                                mv.model.frameBufferModel.setOverdrawFactor(1.2)
                                
                                // Configure gesture detection for better responsiveness
                                mv.setOnTouchListener { view, event ->
                                    // Let the MapView handle the touch events natively for optimal performance
                                    view.onTouchEvent(event)
                                }
                                
                                val cache = AndroidUtil.createTileCache(
                                    ctx,
                                    "mycache",
                                    mv.model.displayModel.tileSize,
                                    1f,
                                    mv.model.frameBufferModel.overdrawFactor
                                )
                                // Load pre-bundled world.map from assets by copying to cache (MapFile needs File or FileInputStream)
                                val cacheFile = File(ctx.cacheDir, "world.map")
                                if (!cacheFile.exists()) {
                                    ctx.assets.open("world.map").use { ins ->
                                        FileOutputStream(cacheFile).use { outs ->
                                            val buf = ByteArray(8 * 1024)
                                            var n: Int
                                            while (true) {
                                                n = ins.read(buf)
                                                if (n <= 0) break
                                                outs.write(buf, 0, n)
                                            }
                                            outs.flush()
                                        }
                                    }
                                }
                                val mapStore = MapFile(FileInputStream(cacheFile))
                                val renderLayer = TileRendererLayer(
                                    cache,
                                    mapStore,
                                    mv.model.mapViewPosition,
                                    AndroidGraphicFactory.INSTANCE
                                )
                                renderLayer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
                                mv.layerManager.layers.add(renderLayer)
                                val center = centerLatLong ?: LatLong(52.5200, 13.4050)
                                mv.setCenter(center)
                                mv.setZoomLevel(5) // Approx ~500 km / 200 mi view on typical phone screens
                                
                                // Configure zoom controls for better gesture handling
                                mv.setBuiltInZoomControls(true)
                                mv.mapScaleBar.isVisible = true
                                
                                // Configure touch gesture sensitivity and smoothing
                                mv.setClickable(true)
                                mv.setLongClickable(true)
                                mv.isFocusable = true
                                mv.isFocusableInTouchMode = true
                                
                                // Configure display settings for better performance during zoom
                                mv.model.displayModel.setUserScaleFactor(1.0f)
                                mv.model.displayModel.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                                
                                // Add pin marker at current location if available
                                if (centerLatLong != null) {
                                    // Load and scale the pin bitmap to 24x42 dp (smaller pin size)
                                    val density = ctx.resources.displayMetrics.density
                                    val widthPx = (24 * density).toInt()
                                    val heightPx = (42 * density).toInt()

                                    // Always add your own location pin (red)
                                    val pinBitmapRaw = BitmapFactory.decodeResource(ctx.resources, com.navguard.app.R.drawable.pin)
                                    val pinBitmapScaled = android.graphics.Bitmap.createScaledBitmap(pinBitmapRaw, widthPx, heightPx, true)
                                    val pinBitmap = AndroidGraphicFactory.convertToBitmap(BitmapDrawable(ctx.resources, pinBitmapScaled))
                                    val marker = Marker(centerLatLong, pinBitmap, 0, -pinBitmap.height / 2)
                                    mv.layerManager.layers.add(marker)
                                    markerRef = marker

                                    // If this is live location from someone else, also add their blue pin
                                    if (isLiveLocationFromOther && senderLatLong != null) {
                                        val bluePinBitmapRaw = BitmapFactory.decodeResource(ctx.resources, com.navguard.app.R.drawable.pinblue)
                                        val bluePinBitmapScaled = android.graphics.Bitmap.createScaledBitmap(bluePinBitmapRaw, widthPx, heightPx, true)
                                        val bluePinBitmap = AndroidGraphicFactory.convertToBitmap(BitmapDrawable(ctx.resources, bluePinBitmapScaled))
                                        val blueMarker = Marker(senderLatLong, bluePinBitmap, 0, -bluePinBitmap.height / 2)
                                        mv.layerManager.layers.add(blueMarker)
                                        blueMarkerRef = blueMarker
                                    }
                                }
                                hasCenteredMap = true
                                mv
                            } catch (e: SecurityException) {
                                // Lost permission, trigger error state
                                loadError = true
                                MapView(ctx) // Return empty view
                            } catch (e: Exception) {
                                // Other error, trigger error state
                                loadError = true
                                MapView(ctx)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mv ->
                            // Update the pin marker position dynamically as user moves
                            if (centerLatLong != null && markerRef != null) {
                                val currentMarker = markerRef!!
                                // Only update if the position actually changed to avoid unnecessary redraws
                                if (currentMarker.latLong != centerLatLong) {
                                    currentMarker.latLong = centerLatLong
                                    // Force map redraw to show updated marker position in real-time
                                    mv.layerManager.redrawLayers()
                                }
                            }
                            // Update or add blue marker for sender's live location
                            if (isLiveLocationFromOther && senderLatLong != null) {
                                if (blueMarkerRef == null) {
                                    val density = mv.context.resources.displayMetrics.density
                                    val widthPx = (24 * density).toInt()
                                    val heightPx = (42 * density).toInt()
                                    val bluePinBitmapRaw = BitmapFactory.decodeResource(mv.context.resources, com.navguard.app.R.drawable.pinblue)
                                    val bluePinBitmapScaled = android.graphics.Bitmap.createScaledBitmap(bluePinBitmapRaw, widthPx, heightPx, true)
                                    val bluePinBitmap = AndroidGraphicFactory.convertToBitmap(BitmapDrawable(mv.context.resources, bluePinBitmapScaled))
                                    val blueMarker = Marker(senderLatLong, bluePinBitmap, 0, -bluePinBitmap.height / 2)
                                    mv.layerManager.layers.add(blueMarker)
                                    blueMarkerRef = blueMarker
                                    mv.layerManager.redrawLayers()
                                } else if (blueMarkerRef!!.latLong != senderLatLong) {
                                    blueMarkerRef!!.latLong = senderLatLong
                                    mv.layerManager.redrawLayers()
                                }
                            }
                        }
                    )
                    }
                }
            }
        }
    }
}