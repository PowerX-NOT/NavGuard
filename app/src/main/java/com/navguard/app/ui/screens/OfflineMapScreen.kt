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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import android.content.SharedPreferences
import com.navguard.app.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("offline_map_prefs", Context.MODE_PRIVATE) }
    val MAP_URI_KEY = "offline_map_uri"
    var mapUri by remember { mutableStateOf<Uri?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    // On first launch, try to load the saved URI
    LaunchedEffect(Unit) {
        val uriString = prefs.getString(MAP_URI_KEY, null)
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
                prefs.edit().putString(MAP_URI_KEY, uri.toString()).apply()
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
    var centerLatLong by remember { mutableStateOf<LatLong?>(null) }
    var isLocating by remember { mutableStateOf(true) }
    // On first launch, try to get current location
    LaunchedEffect(Unit) {
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.offline_map)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                        prefs.edit().remove(MAP_URI_KEY).apply()
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
                                mv.setZoomLevel(14)
                                mv.setBuiltInZoomControls(true)
                                mv.mapScaleBar.isVisible = true
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
                            // Optionally update map if needed
                        }
                    )
                }
            }
        }
    }
}