package com.example.pdrapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

data class GPSLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)

class GPSManager(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<GPSLocation?>(null)
    val currentLocation: StateFlow<GPSLocation?> = _currentLocation.asStateFlow()
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private var locationCallback: LocationCallback? = null
    private var initialLocation: GPSLocation? = null
    
    fun startTracking() {
        if (!hasLocationPermission()) {
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000 // 1秒間隔
        ).apply {
            setMinUpdateIntervalMillis(500)
            setMaxUpdateDelayMillis(2000)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val gpsLocation = GPSLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    if (initialLocation == null) {
                        initialLocation = gpsLocation
                    }
                    
                    _currentLocation.value = gpsLocation
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            _isTracking.value = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    fun stopTracking() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        _isTracking.value = false
    }
    
    fun resetInitialLocation() {
        initialLocation = null
    }
    
    // GPS座標を相対座標（メートル）に変換
    fun getRelativePosition(location: GPSLocation): Pair<Float, Float> {
        val initial = initialLocation ?: return Pair(0f, 0f)
        
        // ハーバーサイン公式を使用して距離を計算
        val earthRadius = 6371000.0 // メートル
        val latDiff = Math.toRadians(location.latitude - initial.latitude)
        val lonDiff = Math.toRadians(location.longitude - initial.longitude)
        
        val x = lonDiff * cos(Math.toRadians((location.latitude + initial.latitude) / 2)) * earthRadius
        val y = latDiff * earthRadius
        
        return Pair(x.toFloat(), y.toFloat())
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
