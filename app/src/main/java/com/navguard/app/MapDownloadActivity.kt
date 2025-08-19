package com.navguard.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.io.File
import java.net.URL

class MapDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapDownloadScreen(onBack = { finish() })
        }
    }
}

private const val ROOT_URL = "https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/"

data class Entry(
    val name: String,
    val isDirectory: Boolean,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDownloadScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var currentUrl by remember { mutableStateOf(ROOT_URL) }
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) } // bump to refresh existence checks
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var pendingDeleteName by remember { mutableStateOf<String?>(null) }

    // Suggested section based on coarse continent guess
    val locationManager = remember { LocationManager(ctx) }
    var suggestedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Guess continent by lat/lon
        locationManager.getCurrentLocation(object : LocationManager.LocationCallback {
            override fun onLocationReceived(latitude: Double, longitude: Double) {
                suggestedKey = guessContinent(latitude, longitude)
            }
            override fun onLocationError(error: String) {
                suggestedKey = null
            }
        })
    }

    LaunchedEffect(currentUrl) {
        loading = true
        error = null
        scope.launch(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(currentUrl).get()
                val links = doc.select("a[href]")
                val list = links
                    .map { it.attr("href") }
                    .filter { it != "../" }
                    .map { href ->
                        val isDir = href.endsWith("/")
                        val clean = href.trimEnd('/')
                        Entry(
                            name = clean,
                            isDirectory = isDir,
                            url = URL(URL(currentUrl), href).toString()
                        )
                    }
                    .sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
                entries = list
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Maps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Confirm delete dialog
            if (pendingDelete != null) {
                AlertDialog(
                    onDismissRequest = { pendingDelete = null; pendingDeleteName = null },
                    title = { Text("Delete map?") },
                    text = { Text("Are you sure you want to delete ${pendingDeleteName}? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            try {
                                pendingDelete?.let { if (it.exists()) it.delete() }
                                version++
                            } catch (_: Exception) {}
                            pendingDelete = null
                            pendingDeleteName = null
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null; pendingDeleteName = null }) { Text("Cancel") }
                    }
                )
            }
            // Search box
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search maps and folders") }
            )
            if (suggestedKey != null) {
                AssistChipRow(suggestedKey!!, onOpen = { key ->
                    // Try to open the continent folder if present
                    val target = entries.firstOrNull { it.isDirectory && it.name.contains(key, ignoreCase = true) }
                    if (target != null) currentUrl = target.url
                })
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${error}")
                }
            } else {
                val filtered = remember(entries, query) {
                    if (query.isBlank()) entries
                    else entries.filter { it.name.contains(query, ignoreCase = true) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (currentUrl != ROOT_URL) {
                        item {
                            Text(
                                text = ".. (Up)",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentUrl = currentUrl.trimEnd('/').substringBeforeLast('/') + "/"
                                    }
                                    .padding(12.dp)
                            )
                        }
                    }
                    items(filtered) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDirectory) {
                                        currentUrl = entry.url
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name)
                                Text(
                                    if (entry.isDirectory) "Directory" else "Map file",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!entry.isDirectory && entry.name.endsWith(".map", true)) {
                                val (targetDir, targetFile) = remember(currentUrl, entry, version) {
                                    val rel = currentUrl.removePrefix(ROOT_URL).trim('/')
                                    val tDir = if (rel.isBlank()) "offline-maps" else "offline-maps/$rel"
                                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), tDir)
                                    val file = File(dir, entry.name)
                                    tDir to file
                                }
                                val exists = targetFile.exists()
                                if (exists) {
                                    IconButton(onClick = {
                                        // Ask for confirmation before deleting
                                        pendingDelete = targetFile
                                        pendingDeleteName = entry.name
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        enqueueDownload(ctx, entry, currentUrl)
                                        version++
                                    }) {
                                        Icon(Icons.Default.Download, contentDescription = "Download")
                                    }
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistChipRow(suggestedKey: String, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        AssistChip(onClick = { onOpen(suggestedKey) }, label = { Text("Suggested: ${suggestedKey}") })
    }
}

private fun enqueueDownload(context: Context, entry: Entry, currentUrl: String) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = Uri.parse(entry.url)
    val fileName = entry.name
    // Compute relative path from ROOT_URL to preserve folder structure
    val rel = currentUrl.removePrefix(ROOT_URL).trim('/')
    val targetDir = if (rel.isBlank()) "offline-maps" else "offline-maps/$rel"
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), targetDir)
    if (!dir.exists()) dir.mkdirs()
    val request = DownloadManager.Request(uri)
        .setTitle("Downloading ${fileName}")
        .setDescription("Mapsforge map")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$targetDir/${fileName}")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    dm.enqueue(request)
}

private fun guessContinent(lat: Double, lon: Double): String? {
    return when {
        lat in -56.0..84.0 && lon in -31.0..60.0 -> "europe"
        lat in -35.0..37.0 && lon in -18.0..52.0 -> "africa"
        lat in 7.0..81.0 && lon in -168.0..-52.0 -> "north-america"
        lat in -56.0..13.0 && lon in -81.0..-34.0 -> "south-america"
        lat in -47.0..-10.0 && lon in 112.0..154.0 -> "australia-oceania"
        lat in -10.0..80.0 && lon in 60.0..180.0 -> "asia"
        else -> null
    }
}
