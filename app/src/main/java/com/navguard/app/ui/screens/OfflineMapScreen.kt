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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var mapUri by remember { mutableStateOf<Uri?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val openMapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                mapUri = uri
            }
        }
    )

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
            } else {
                AndroidView(
                    factory = { ctx ->
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
                        mv.setCenter(LatLong(52.5200, 13.4050)) // Default to Berlin
                        mv.setZoomLevel(10)
                        mv.setBuiltInZoomControls(true)
                        mv.mapScaleBar.isVisible = true
                        mv
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