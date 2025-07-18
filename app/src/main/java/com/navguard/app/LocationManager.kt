package com.navguard.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.core.app.ActivityCompat

class LocationManager(private val context: Context) : LocationListener {
    
    interface LocationCallback {
        fun onLocationReceived(latitude: Double, longitude: Double)
        fun onLocationError(error: String)
    }
    
    private val locationManager: android.location.LocationManager = 
        context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    private var callback: LocationCallback? = null
    private var isGpsEnabled = false
    
    fun getCurrentLocation(callback: LocationCallback) {
        this.callback = callback
        
        if (!hasLocationPermission()) {
            callback.onLocationError("Location permission not granted")
            return
        }
        
        if (!isLocationEnabled()) {
            callback.onLocationError("GPS is disabled")
            return
        }
        
        try {
            // Try to get last known location first
            val lastLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            if (lastLocation != null && isLocationRecent(lastLocation)) {
                callback.onLocationReceived(lastLocation.latitude, lastLocation.longitude)
                return
            }
            
            // Request fresh location update
            locationManager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, this, null)
            
            // Also try network provider as backup
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, this, null)
            }
            
        } catch (e: SecurityException) {
            callback.onLocationError("Location access denied: ${e.message}")
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
    
    private fun isLocationRecent(location: Location): Boolean {
        // Consider location recent if it's less than 5 minutes old
        return (System.currentTimeMillis() - location.time) < 5 * 60 * 1000
    }
    
    override fun onLocationChanged(location: Location) {
        callback?.onLocationReceived(location.latitude, location.longitude)
    }
    
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Handle status changes if needed
    }
    
    override fun onProviderEnabled(provider: String) {
        if (provider == android.location.LocationManager.GPS_PROVIDER) {
            isGpsEnabled = true
        }
    }
    
    override fun onProviderDisabled(provider: String) {
        if (provider == android.location.LocationManager.GPS_PROVIDER) {
            isGpsEnabled = false
            callback?.onLocationError("GPS provider disabled")
        }
    }
    
    fun stopLocationUpdates() {
        if (hasLocationPermission()) {
            locationManager.removeUpdates(this)
        }
    }
}