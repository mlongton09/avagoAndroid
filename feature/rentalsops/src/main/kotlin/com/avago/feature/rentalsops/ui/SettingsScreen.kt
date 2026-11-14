package com.avago.feature.rentalsops.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.push.RentalOpsPushPrefs
import com.avago.feature.rentalsops.push.RentalPushRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PrefsMode { LOADING, SERVER, DEVICE_ONLY, OFFLINE }

data class PrefsState(
    val mode: PrefsMode = PrefsMode.LOADING,
    /** Events shown: the server's list (permission-filtered) or every event for device-only. */
    val events: List<String> = emptyList(),
    val enabled: Map<String, Boolean> = emptyMap(),
    val busy: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RentalOpsSettingsViewModel @Inject constructor(
    private val prefs: RentalOpsPushPrefs,
    identity: IdentityManager,
) : ViewModel() {
    private val mode = MutableStateFlow(PrefsMode.LOADING)
    private val events = MutableStateFlow<List<String>>(emptyList())
    private val busy = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<PrefsState> = combine(
        mode, events, busy, identity.activeAccountId.flatMapLatest { prefs.local(it) },
    ) { m, e, b, local -> PrefsState(m, e, local, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrefsState())

    init { load() }

    fun load() = viewModelScope.launch {
        mode.value = PrefsMode.LOADING
        apply(prefs.load())
    }

    fun set(event: String, on: Boolean) = viewModelScope.launch {
        busy.value = busy.value + event
        apply(prefs.set(event, on, server = mode.value == PrefsMode.SERVER))
        busy.value = busy.value - event
    }

    private fun apply(r: RentalOpsPushPrefs.Load) {
        when (r) {
            is RentalOpsPushPrefs.Load.Server -> {
                mode.value = PrefsMode.SERVER
                events.value = r.items.map { it.event }
            }
            RentalOpsPushPrefs.Load.DeviceOnly -> {
                mode.value = PrefsMode.DEVICE_ONLY
                if (events.value.isEmpty()) events.value = RentalPushRouter.EVENTS
            }
            is RentalOpsPushPrefs.Load.Offline -> {
                // Keep a previously loaded server list; otherwise show the cached switches read-only.
                if (mode.value != PrefsMode.SERVER) {
                    mode.value = PrefsMode.OFFLINE
                    if (events.value.isEmpty()) events.value = RentalPushRouter.EVENTS
                }
            }
        }
    }
}

/**
 * Push preferences per rental event. Saved on the server (`notification-prefs`) so it stops
 * sending muted events to every device; drive-only users get device-local switches.
 */
@Composable
fun RentalOpsSettingsScreen(onBack: () -> Unit, vm: RentalOpsSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val systemOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_settings_title), onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            if (!systemOn) {
                OfflineBanner(stringResource(R.string.rentalops_push_disabled))
                OutlinedButton(
                    onClick = {
                        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                        }
                        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.padding(horizontal = AvagoSpacing.lg).heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_open_settings)) }
            }
            when (s.mode) {
                PrefsMode.OFFLINE -> {
                    OfflineBanner(stringResource(R.string.rentalops_prefs_offline))
                    OutlinedButton(onClick = { vm.load() }, modifier = Modifier.padding(horizontal = AvagoSpacing.lg).heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentalops_retry))
                    }
                }
                PrefsMode.DEVICE_ONLY -> OfflineBanner(stringResource(R.string.rentalops_prefs_device_only))
                else -> Unit
            }
            Text(
                stringResource(R.string.rentalops_push_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(AvagoSpacing.lg),
            )
            if (s.mode == PrefsMode.LOADING) {
                CircularProgressIndicator(Modifier.padding(AvagoSpacing.lg))
            }
            val editable = s.mode == PrefsMode.SERVER || s.mode == PrefsMode.DEVICE_ONLY
            s.events.forEach { e ->
                val on = s.enabled[e] ?: true
                val canToggle = editable && e !in s.busy
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouch)
                        .toggleable(value = on, enabled = canToggle, role = Role.Switch, onValueChange = { vm.set(e, it) })
                        .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(eventTitle(e)?.let { stringResource(it) } ?: e, style = MaterialTheme.typography.bodyLarge)
                        eventDesc(e)?.let {
                            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = on, onCheckedChange = null, enabled = canToggle)
                }
            }
        }
    }
}

private fun eventTitle(e: String): Int? = when (e) {
    "rental.booking_request" -> R.string.rentalops_event_booking_request
    "rental.off_rent_requested" -> R.string.rentalops_event_off_rent_requested
    "rental.task_assigned" -> R.string.rentalops_event_task_assigned
    "rental.task_completed" -> R.string.rentalops_event_task_completed
    "rental.task_failed" -> R.string.rentalops_event_task_failed
    "rental.overdue" -> R.string.rentalops_event_overdue
    "rental.cycle_invoiced" -> R.string.rentalops_event_cycle_invoiced
    else -> null
}

private fun eventDesc(e: String): Int? = when (e) {
    "rental.booking_request" -> R.string.rentalops_event_booking_request_desc
    "rental.off_rent_requested" -> R.string.rentalops_event_off_rent_requested_desc
    "rental.task_assigned" -> R.string.rentalops_event_task_assigned_desc
    "rental.task_completed" -> R.string.rentalops_event_task_completed_desc
    "rental.task_failed" -> R.string.rentalops_event_task_failed_desc
    "rental.overdue" -> R.string.rentalops_event_overdue_desc
    "rental.cycle_invoiced" -> R.string.rentalops_event_cycle_invoiced_desc
    else -> null
}