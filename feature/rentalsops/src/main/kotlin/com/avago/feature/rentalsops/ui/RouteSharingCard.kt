package com.avago.feature.rentalsops.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentalsops.R

/** What the location-sharing card shows; see [DriverRouteViewModel.sharing]. */
data class SharingState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    /** The route has an assigned / en-route / arrived stop (server `tracking`). */
    val routeActive: Boolean = false,
)

/**
 * G5 opt-in for location sharing during the route. Permission flow: precise/approximate
 * location → notifications (Android 13+, for the ongoing notice) → optional "Allow all the
 * time" with a rationale first (only needed for Android to restart sharing after killing the
 * app; a service started from this screen works with while-in-use access).
 */
@Composable
internal fun RouteSharingCard(s: SharingState, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    var askBackground by rememberSaveable { mutableStateOf(false) }
    var backgroundAsked by rememberSaveable { mutableStateOf(false) }

    fun maybeAskBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundAsked && !granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            askBackground = true
        }
    }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onToggle(true)
        maybeAskBackground()
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) {
            denied = false
            if (Build.VERSION.SDK_INT >= 33 && !granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onToggle(true)
                maybeAskBackground()
            }
        } else {
            denied = true
        }
    }

    fun turnOn() {
        if (granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            denied = false
            onToggle(true)
            maybeAskBackground()
        } else {
            location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val on = s.enabled && !denied
    SectionCard(modifier = Modifier.padding(vertical = AvagoSpacing.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouch)
                .toggleable(value = on, role = Role.Switch, onValueChange = { if (it) turnOn() else onToggle(false) })
                .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
        ) {
            LeadingBadge(
                if (on) Icons.Filled.MyLocation else Icons.Outlined.LocationOff,
                if (s.running) StatusTone.Success else StatusTone.Neutral,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rentalops_w2_track_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        s.running -> stringResource(R.string.rentalops_w2_track_on)
                        on && !s.routeActive -> stringResource(R.string.rentalops_w2_track_no_route)
                        on -> stringResource(R.string.rentalops_w2_track_on)
                        else -> stringResource(R.string.rentalops_w2_track_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.rentalops_w2_track_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
    }
    if (denied) {
        ErrorBanner(
            stringResource(R.string.rentalops_w2_track_denied),
            modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
            tone = StatusTone.Warning,
            retryLabel = stringResource(R.string.rentalops_w2_track_settings),
            onRetry = { openAppSettings(ctx) },
        )
    }
    if (askBackground) {
        AlertDialog(
            onDismissRequest = {
                askBackground = false
                backgroundAsked = true
            },
            icon = { androidx.compose.material3.Icon(Icons.Filled.MyLocation, contentDescription = null) },
            title = { Text(stringResource(R.string.rentalops_w2_track_bg_title)) },
            text = { Text(stringResource(R.string.rentalops_w2_track_bg_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askBackground = false
                    backgroundAsked = true
                    val activity = ctx.findActivity()
                    // After a refusal Android no longer shows the prompt; send the driver to Settings.
                    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                        backgroundRequestedBefore(ctx)
                    ) {
                        openAppSettings(ctx)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        markBackgroundRequested(ctx)
                        background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }) { Text(stringResource(R.string.rentalops_w2_track_bg_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askBackground = false
                    backgroundAsked = true
                }) { Text(stringResource(R.string.rentalops_w2_track_bg_skip)) }
            },
        )
    }
}

private fun granted(ctx: Context, p: String) =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ("package:" + ctx.packageName).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private const val PREFS = "rentalops_route_tracking_ui"
private const val KEY_BG_REQUESTED = "bg_requested"

private fun backgroundRequestedBefore(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BG_REQUESTED, false)

private fun markBackgroundRequested(ctx: Context) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_BG_REQUESTED, true) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
