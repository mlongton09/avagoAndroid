package com.avago.feature.rentals.ops.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.avago.feature.rentals.R
import com.avago.feature.rentals.ops.data.RentalOpsRepository
import com.avago.feature.rentals.ops.domain.DriverRoute
import com.avago.feature.rentals.ops.domain.PingPolicy
import com.avago.feature.rentals.ops.domain.RoutePing
import com.avago.feature.rentals.ops.domain.StationaryDetector
import com.avago.feature.rentals.ops.domain.TrackingRules
import com.avago.feature.rentals.ops.push.RentalOpsNotifier
import com.avago.feature.rentals.ops.push.RentalOpsRoutes
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Wave 2 G5 location sharing during an active delivery route: a `location` foreground service
 * with an ongoing notification (Stop action). Fixes go to [RouteTracker]'s offline queue and are
 * uploaded in batches. It stops itself when the driver turns sharing off, the server route no
 * longer says `tracking` (no assigned / en-route / arrived stop), at local midnight, or on 401/403.
 *
 * Battery: the server interval (default 60 s) is doubled below 30 % and quadrupled below 15 %
 * when not charging; after 2 minutes below 1 m/s it drops to balanced accuracy at twice the
 * interval until the driver moves again.
 */
@AndroidEntryPoint
class RouteTrackingService : Service() {
    @Inject lateinit var tracker: RouteTracker

    @Inject lateinit var repo: RentalOpsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stationary = StationaryDetector()
    private var loop: Job? = null
    private var route: DriverRoute? = null
    private var startedOn: LocalDate? = null
    private var requested: Pair<Int, Boolean>? = null
    private var stopping = false

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onFix(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Timber.i("[RouteTracking] stop requested")
            scope.launch { tracker.setSharing(false) }
            finish()
            return START_NOT_STICKY
        }
        if (!tracker.hasForegroundLocation() || !promote()) {
            stopSelf()
            return START_NOT_STICKY
        }
        tracker.setRunning(true)
        if (startedOn == null) startedOn = LocalDate.now()
        if (loop == null) loop = scope.launch { run() }
        // Restarted by Android after process death: continue only with "Allow all the time".
        return if (tracker.hasBackgroundLocation()) START_STICKY else START_NOT_STICKY
    }

    private fun promote(): Boolean = runCatching {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        true
    }.onFailure { Timber.w(it, "[RouteTracking] startForeground refused") }.getOrDefault(false)

    private suspend fun run() {
        var lastCheck = 0L
        while (scope.isActive && !stopping) {
            val now = System.currentTimeMillis()
            if (now - lastCheck >= PingPolicy.ROUTE_CHECK_MS || route == null) {
                lastCheck = now
                refreshRoute()
            }
            val keep = TrackingRules.shouldTrack(
                enabled = tracker.isSharingEnabled(),
                routeTracking = route?.tracking ?: true,
                startedOn = startedOn,
                now = Instant.now(),
                zone = ZoneId.systemDefault(),
            )
            if (!keep) {
                Timber.i("[RouteTracking] route inactive; stopping")
                break
            }
            requestUpdates()
            if (tracker.flush() == RouteTracker.Flush.FORBIDDEN) {
                Timber.i("[RouteTracking] pings forbidden; stopping")
                break
            }
            delay(PingPolicy.MIN_FLUSH_GAP_MS)
        }
        finish()
    }

    private suspend fun refreshRoute() {
        when (val f = repo.myRoute()) {
            is RentalOpsRepository.Fetch.Ok -> route = DriverRoute.parse(f.data.body)
            // Offline: keep tracking on the last known route (the queue uploads later).
            is RentalOpsRepository.Fetch.Failed -> f.cached?.let { if (route == null) route = DriverRoute.parse(it.body) }
        }
        route?.let { if (tracker.baseIntervalSeconds == null) tracker.baseIntervalSeconds = it.pingIntervalSeconds }
    }

    private fun battery(): Pair<Int?, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null to false
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val (pct, charging) = battery()
        val still = stationaryNow
        val base = PingPolicy.intervalSeconds(tracker.baseIntervalSeconds, pct, charging)
        val seconds = if (still) base * 2 else base
        val want = seconds to still
        if (want == requested) return
        if (!tracker.hasForegroundLocation()) return
        val priority = if (still) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_HIGH_ACCURACY
        val req = LocationRequest.Builder(priority, seconds * 1000L)
            .setMinUpdateIntervalMillis(PingPolicy.MIN_GAP_MS)
            .setMinUpdateDistanceMeters(if (still) 25f else 0f)
            .setMaxUpdateDelayMillis(seconds * 1000L * 2)
            .build()
        runCatching {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
            requested = want
            Timber.d("[RouteTracking] interval ${seconds}s stationary=$still battery=$pct charging=$charging")
        }.onFailure { Timber.w(it, "[RouteTracking] location request failed") }
    }

    @Volatile
    private var stationaryNow = false

    private fun onFix(l: Location) {
        val speed = if (l.hasSpeed()) l.speed else null
        val wasStill = stationaryNow
        stationaryNow = stationary.update(speed, l.time)
        val ping = RoutePing(
            lat = l.latitude,
            lng = l.longitude,
            recordedAtMs = l.time,
            accuracyM = if (l.hasAccuracy()) l.accuracy else null,
            speedMps = speed,
            heading = if (l.hasBearing()) l.bearing else null,
            batteryPct = battery().first,
            taskId = TrackingRules.activeTaskId(route),
        )
        scope.launch {
            tracker.record(ping)
            if (wasStill != stationaryNow) requestUpdates()
        }
    }

    private fun finish() {
        if (stopping) return
        stopping = true
        runCatching { fused.removeLocationUpdates(callback) }
        scope.launch {
            // Best effort: send what is queued before going away; the rest waits for next time.
            runCatching { tracker.flush(System.currentTimeMillis() + PingPolicy.MIN_FLUSH_GAP_MS) }
            tracker.setRunning(false)
            ServiceCompat.stopForeground(this@RouteTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        tracker.setRunning(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        ensureChannel()
        val open = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = Intent.ACTION_VIEW
            putExtra(RentalOpsRoutes.EXTRA_ROUTE, RentalOpsRoutes.ROUTE)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = open?.let { PendingIntent.getActivity(this, NOTIFICATION_ID, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val stopPi = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 1,
            Intent(this, RouteTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.rentalops_w2_track_notif_title))
            .setContentText(getString(R.string.rentalops_w2_track_notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { openPi?.let { setContentIntent(it) } }
            .addAction(0, getString(R.string.rentalops_w2_track_stop), stopPi)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup(RentalOpsNotifier.GROUP, getString(R.string.rentalops_channel_group)))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.rentalops_w2_track_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.rentalops_w2_track_channel_desc)
                group = RentalOpsNotifier.GROUP
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val ACTION_STOP = "com.avago.feature.rentals.ops.action.STOP_ROUTE_TRACKING"
        const val CHANNEL = "rentalops_route_tracking"
        private const val NOTIFICATION_ID = 0x52_54_01
    }
}
