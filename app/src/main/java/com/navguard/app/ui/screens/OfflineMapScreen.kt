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
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.FileInputStream
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    onNavigateBack: () -> Unit,
    initialCenter: LatLong? = null
) {
    val context = LocalContext.current
    val persistence = remember { PersistenceManager(context) }
    var mapUri by remember { mutableStateOf<Uri?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
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
                mapUri = uri
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

    var markerRef by remember { mutableStateOf<Marker?>(null) }
    var hasCenteredMap by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.offline_map)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { openMapLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(id = R.string.open_map))
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
            if (mapUri == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { openMapLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(id = R.string.open_map))
                    }
                }
            } else if (isLocating) {
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
                    // If error, clear URI and prompt user
                    LaunchedEffect(Unit) {
                        persistence.clearOfflineMapUri()
                        mapUri = null
                        loadError = false
                    }
                } else {
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
                                val stream = ctx.contentResolver.openInputStream(mapUri!!) as FileInputStream
                                val mapStore = MapFile(stream)
                                val renderLayer = TileRendererLayer(
                                    cache,
                                    mapStore,
                                    mv.model.mapViewPosition,
                                    AndroidGraphicFactory.INSTANCE
                                )
                                renderLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT)
                                mv.layerManager.layers.add(renderLayer)
                                val center = centerLatLong ?: LatLong(52.5200, 13.4050)
                                mv.setCenter(center)
                                mv.setZoomLevel(16) // Show ~200m/500ft area by default
                                
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
                                    val pinBitmapRaw = BitmapFactory.decodeResource(ctx.resources, com.navguard.app.R.drawable.pin)
                                    val pinBitmapScaled = android.graphics.Bitmap.createScaledBitmap(pinBitmapRaw, widthPx, heightPx, true)
                                    val pinBitmap = AndroidGraphicFactory.convertToBitmap(BitmapDrawable(ctx.resources, pinBitmapScaled))
                                    val marker = Marker(centerLatLong, pinBitmap, 0, -pinBitmap.height / 2)
                                    mv.layerManager.layers.add(marker)
                                    markerRef = marker
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
                        }
                    )
                }
            }
        }
    }
}