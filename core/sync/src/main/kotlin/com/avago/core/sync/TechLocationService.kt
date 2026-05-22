package com.avago.core.sync

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Sends the technician's current location to the server on significant location changes.
 * Uses [LocationManager.NETWORK_PROVIDER] for battery-efficient coarse positioning.
 *
 * Call [startMonitoring] when the app comes to the foreground and [stopMonitoring] when
 * it goes to the background.  Requires [android.Manifest.permission.ACCESS_COARSE_LOCATION].
 */
@Singleton
class TechLocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityManager: IdentityManager,
    private val serviceClientProvider: Provider<AvagoServiceClient>,
) {

    // 5 minutes between updates, 500 m minimum displacement
    private val minTimeMs = 5 * 60 * 1_000L
    private val minDistanceM = 500f

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val locationListener = LocationListener { location ->
        onLocationUpdate(location.latitude, location.longitude)
    }

    /**
     * Registers for network-provider location updates.
     * Silently returns if location permission has not been granted.
     */
    fun startMonitoring() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                minTimeMs,
                minDistanceM,
                locationListener,
            )
            Timber.d("TechLocationService: started monitoring")
        } catch (e: SecurityException) {
            Timber.w("TechLocationService: ACCESS_COARSE_LOCATION not granted — location monitoring disabled")
        } catch (e: Exception) {
            Timber.e(e, "TechLocationService: failed to start monitoring")
        }
    }

    /**
     * Unregisters location updates to stop battery drain when the app is backgrounded.
     */
    fun stopMonitoring() {
        try {
            locationManager.removeUpdates(locationListener)
            Timber.d("TechLocationService: stopped monitoring")
        } catch (e: Exception) {
            Timber.e(e, "TechLocationService: failed to stop monitoring")
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private fun onLocationUpdate(lat: Double, lon: Double) {
        val accountId = identityManager.getActiveAccountId() ?: run {
            Timber.d("TechLocationService: no active account, skipping location update")
            return
        }
        val userId = identityManager.activeUserId.value ?: run {
            Timber.d("TechLocationService: no active userId (anonymous), skipping location update")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                serviceClientProvider.get().updateTechLocation(accountId, userId, lat, lon)
                Timber.d("TechLocationService: posted location ($lat, $lon) for user $userId")
            } catch (e: Exception) {
                Timber.w(e, "TechLocationService: failed to post location update")
            }
        }
    }
}
