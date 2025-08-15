package com.navguard.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.content.ComponentName
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import java.util.concurrent.ConcurrentHashMap

class LocationService : Service(), LocationListener {
    
    inner class LocationBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }
    
    private val binder = LocationBinder()
    private lateinit var locationManager: android.location.LocationManager
    private val locationCallbacks = ConcurrentHashMap<String, LocationCallback>()
    private var isLocationSharing = false
    private var lastLocation: Location? = null
    private var locationUpdateJob: android.os.Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sendHandler = Handler(Looper.getMainLooper())
    private var serialService: SerialService? = null
    private var persistenceManager: PersistenceManager? = null
    private var isBoundToSerial = false
    
    interface LocationCallback {
        fun onLocationReceived(latitude: Double, longitude: Double)
        fun onLocationError(error: String)
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val LOCATION_CHANNEL_ID = "location_service_channel"
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val LOCATION_MIN_DISTANCE = 0f // Update even if no movement
        
        fun startLocationSharing(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = "START_LOCATION_SHARING"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopLocationSharing(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = "STOP_LOCATION_SHARING"
            }
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        persistenceManager = PersistenceManager(applicationContext)
        createNotificationChannel()
    }

    private val serialConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serialService = (binder as? SerialService.SerialBinder)?.getService()
            isBoundToSerial = true
            Log.d("LocationService", "Bound to SerialService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBoundToSerial = false
            serialService = null
            Log.d("LocationService", "Unbound from SerialService")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LOCATION_SHARING" -> {
                startLocationSharing()
                return START_STICKY
            }
            "STOP_LOCATION_SHARING" -> {
                stopLocationSharing()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }
    
    fun addLocationCallback(id: String, callback: LocationCallback) {
        locationCallbacks[id] = callback
        // Send current location if available
        lastLocation?.let { location ->
            callback.onLocationReceived(location.latitude, location.longitude)
        }
    }
    
    fun removeLocationCallback(id: String) {
        locationCallbacks.remove(id)
    }
    
    private fun startLocationSharing() {
        if (isLocationSharing) return
        
        Log.d("LocationService", "Starting location sharing")
        
        if (!hasLocationPermission()) {
            Log.e("LocationService", "Location permission not granted")
            notifyLocationError("Location permission not granted")
            return
        }
        
        if (!isLocationEnabled()) {
            Log.e("LocationService", "GPS is disabled")
            notifyLocationError("GPS is disabled")
            return
        }
        
        try {
            isLocationSharing = true
            startLocationUpdates()
            createNotification()
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d("LocationService", "Location sharing started successfully")

            // Bind to SerialService to send data
            try {
                val serialIntent = Intent(this, SerialService::class.java)
                bindService(serialIntent, serialConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e("LocationService", "Failed to bind SerialService: ${e.message}")
            }

            // Start periodic sender every 5 seconds
            startPeriodicSender()
        } catch (e: SecurityException) {
            Log.e("LocationService", "Security exception starting foreground service: ${e.message}")
            isLocationSharing = false
            notifyLocationError("Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e("LocationService", "Error starting location sharing: ${e.message}")
            isLocationSharing = false
            notifyLocationError("Error starting location service: ${e.message}")
        }
    }
    
    private fun stopLocationSharing() {
        if (!isLocationSharing) return
        
        Log.d("LocationService", "Stopping location sharing")
        isLocationSharing = false
        stopLocationUpdates()
        stopForeground(true)
        stopSelf()
        stopPeriodicSender()
        if (isBoundToSerial) {
            try {
                unbindService(serialConnection)
            } catch (_: Exception) {}
            isBoundToSerial = false
        }
        Log.d("LocationService", "Location sharing stopped")
    }
    
    private fun startLocationUpdates() {
        try {
            // Try to get last known location first
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnownLocation != null && isLocationRecent(lastKnownLocation)) {
                lastLocation = lastKnownLocation
                notifyLocationReceived(lastKnownLocation.latitude, lastKnownLocation.longitude)
            }
            
            // Request fresh location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL,
                LOCATION_MIN_DISTANCE,
                this
            )
            
            // Also try network provider as backup
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_MIN_DISTANCE,
                    this
                )
            }
        } catch (e: SecurityException) {
            notifyLocationError("Location access denied: ${e.message}")
        }
    }
    
    private fun stopLocationUpdates() {
        if (hasLocationPermission()) {
            locationManager.removeUpdates(this)
        }
        locationUpdateJob?.removeCallbacksAndMessages(null)
        locationUpdateJob = null
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    private fun isLocationRecent(location: Location): Boolean {
        // Consider location recent if it's less than 5 minutes old
        return (System.currentTimeMillis() - location.time) < 5 * 60 * 1000
    }
    
    private fun notifyLocationReceived(latitude: Double, longitude: Double) {
        mainHandler.post {
            locationCallbacks.values.forEach { callback ->
                callback.onLocationReceived(latitude, longitude)
            }
        }
    }
    
    private fun notifyLocationError(error: String) {
        mainHandler.post {
            locationCallbacks.values.forEach { callback ->
                callback.onLocationError(error)
            }
        }
    }
    
    // LocationListener implementation
    override fun onLocationChanged(location: Location) {
        lastLocation = location
        Log.d("LocationService", "Location updated: ${location.latitude}, ${location.longitude}")
        notifyLocationReceived(location.latitude, location.longitude)
    }
    
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
        // Handle status changes if needed
    }
    
    override fun onProviderEnabled(provider: String) {
        // Provider enabled
    }
    
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            notifyLocationError("GPS provider disabled")
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Shows when location sharing is active"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = "STOP_LOCATION_SHARING"
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags)
        
        return NotificationCompat.Builder(this, LOCATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Live Location Active")
            .setContentText("Sharing your location")
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_clear_white_24dp, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun startPeriodicSender() {
        sendHandler.removeCallbacksAndMessages(null)
        val task = object : Runnable {
            override fun run() {
                if (isLocationSharing) {
                    lastLocation?.let { loc ->
                        sendLocationToDevice(loc)
                    }
                    sendHandler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
                }
            }
        }
        sendHandler.post(task)
    }

    private fun stopPeriodicSender() {
        sendHandler.removeCallbacksAndMessages(null)
    }

    private fun sendLocationToDevice(location: Location) {
        val svc = serialService ?: return
        val msg = EmergencyMessage(
            content = "LOC",
            type = EmergencyMessage.MessageType.REGULAR,
            latitude = location.latitude,
            longitude = location.longitude,
            status = EmergencyMessage.MessageStatus.SENDING
        )
        val data = "${msg.type.name}|${msg.content}|${msg.latitude}|${msg.longitude}|${msg.messageId}|${msg.status.code}"
        try {
            svc.write(data.toByteArray())
            Log.d("LocationService", "Sent live location ${location.latitude},${location.longitude}")
        } catch (e: Exception) {
            Log.e("LocationService", "Failed to send live location: ${e.message}")
        }
    }
}
