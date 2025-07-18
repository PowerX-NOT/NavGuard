package com.navguard.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

public class LocationManager implements LocationListener {
    
    public interface LocationCallback {
        void onLocationReceived(double latitude, double longitude);
        void onLocationError(String error);
    }
    
    private android.location.LocationManager locationManager;
    private Context context;
    private LocationCallback callback;
    private boolean isGpsEnabled = false;
    
    public LocationManager(Context context) {
        this.context = context;
        this.locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    public void getCurrentLocation(LocationCallback callback) {
        this.callback = callback;
        
        if (!hasLocationPermission()) {
            callback.onLocationError("Location permission not granted");
            return;
        }
        
        if (!isLocationEnabled()) {
            callback.onLocationError("GPS is disabled");
            return;
        }
        
        try {
            // Try to get last known location first
            Location lastLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (lastLocation != null && isLocationRecent(lastLocation)) {
                callback.onLocationReceived(lastLocation.getLatitude(), lastLocation.getLongitude());
                return;
            }
            
            // Request fresh location update
            locationManager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, this, null);
            
            // Also try network provider as backup
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, this, null);
            }
            
        } catch (SecurityException e) {
            callback.onLocationError("Location access denied: " + e.getMessage());
        }
    }
    
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
    }
    
    private boolean isLocationRecent(Location location) {
        // Consider location recent if it's less than 5 minutes old
        return (System.currentTimeMillis() - location.getTime()) < 5 * 60 * 1000;
    }
    
    @Override
    public void onLocationChanged(Location location) {
        if (callback != null) {
            callback.onLocationReceived(location.getLatitude(), location.getLongitude());
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Handle status changes if needed
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        if (provider.equals(android.location.LocationManager.GPS_PROVIDER)) {
            isGpsEnabled = true;
        }
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(android.location.LocationManager.GPS_PROVIDER)) {
            isGpsEnabled = false;
            if (callback != null) {
                callback.onLocationError("GPS provider disabled");
            }
        }
    }
    
    public void stopLocationUpdates() {
        if (hasLocationPermission()) {
            locationManager.removeUpdates(this);
        }
    }
}