package com.avago.feature.rentalsops.ui

import javax.inject.Inject
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentalsops.push.RentalOpsPushPrefs
import com.avago.feature.rentalsops.push.RentalPushRouter
import com.avago.feature.rentalsops.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(vertical = AvagoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        ) {
            if (!systemOn) {
                ErrorBanner(
                    stringResource(R.string.rentalops_push_disabled),
                    tone = StatusTone.Warning,
                    icon = Icons.Filled.NotificationsOff,
                    retryLabel = stringResource(R.string.rentalops_open_settings),
                    onRetry = {
                        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                        }
                        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
            when (s.mode) {
                PrefsMode.OFFLINE -> ErrorBanner(
                    stringResource(R.string.rentalops_prefs_offline),
                    tone = StatusTone.Neutral,
                    icon = Icons.Filled.CloudOff,
                    retryLabel = stringResource(R.string.rentalops_retry),
                    onRetry = { vm.load() },
                )
                PrefsMode.DEVICE_ONLY -> OfflineBanner(stringResource(R.string.rentalops_prefs_device_only), icon = Icons.Filled.PhoneAndroid)
                else -> Unit
            }
            Text(
                stringResource(R.string.rentalops_push_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AvagoSpacing.lg),
            )
            if (s.mode == PrefsMode.LOADING && s.events.isEmpty()) {
                SkeletonList(rows = 5)
            } else if (s.events.isNotEmpty()) {
                val editable = s.mode == PrefsMode.SERVER || s.mode == PrefsMode.DEVICE_ONLY
                SectionCard(title = stringResource(R.string.rentalops_settings_title)) {
                    s.events.forEachIndexed { idx, e ->
                        if (idx > 0) CardDivider()
                        val on = s.enabled[e] ?: true
                        val busy = e in s.busy
                        val canToggle = editable && !busy
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouch)
                                .toggleable(value = on, enabled = canToggle, role = Role.Switch, onValueChange = { vm.set(e, it) })
                                .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
                        ) {
                            LeadingBadge(eventIcon(e), eventTone(e))
                            Column(Modifier.weight(1f)) {
                                Text(eventTitle(e)?.let { stringResource(it) } ?: e, style = MaterialTheme.typography.bodyLarge)
                                eventDesc(e)?.let {
                                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            Switch(checked = on, onCheckedChange = null, enabled = canToggle)
                        }
                    }
                }
            }
        }
    }
}

private fun eventIcon(e: String): ImageVector = when (e) {
    "rental.booking_request" -> Icons.Filled.EventAvailable
    "rental.off_rent_requested" -> Icons.AutoMirrored.Filled.AssignmentReturn
    "rental.task_assigned" -> Icons.Filled.AssignmentInd
    "rental.task_completed" -> Icons.Filled.TaskAlt
    "rental.task_failed" -> Icons.Filled.ErrorOutline
    "rental.overdue" -> Icons.Filled.Schedule
    "rental.cycle_invoiced" -> Icons.AutoMirrored.Filled.ReceiptLong
    "rental.doc_expiring", "rental.credit_application" -> Icons.Filled.Badge
    "rental.dunning_hold" -> Icons.Filled.Block
    "rental.subrental_return_due" -> Icons.Filled.Schedule
    "rental.marketplace_meetup" -> Icons.Filled.Handshake
    else -> Icons.Filled.Notifications
}

private fun eventTone(e: String): StatusTone = when (e) {
    "rental.task_failed", "rental.overdue", "rental.dunning_hold" -> StatusTone.Danger
    "rental.task_completed" -> StatusTone.Success
    "rental.cycle_invoiced" -> StatusTone.Accent
    "rental.off_rent_requested", "rental.doc_expiring", "rental.subrental_return_due" -> StatusTone.Warning
    else -> StatusTone.Info
}
private fun eventTitle(e: String): Int? = when (e) {
    "rental.booking_request" -> R.string.rentalops_event_booking_request
    "rental.off_rent_requested" -> R.string.rentalops_event_off_rent_requested
    "rental.task_assigned" -> R.string.rentalops_event_task_assigned
    "rental.task_completed" -> R.string.rentalops_event_task_completed
    "rental.task_failed" -> R.string.rentalops_event_task_failed
    "rental.overdue" -> R.string.rentalops_event_overdue
    "rental.cycle_invoiced" -> R.string.rentalops_event_cycle_invoiced
    "rental.doc_expiring" -> R.string.rentalops_event_doc_expiring
    "rental.credit_application" -> R.string.rentalops_event_credit_application
    "rental.dunning_hold" -> R.string.rentalops_event_dunning_hold
    "rental.subrental_return_due" -> R.string.rentalops_event_subrental_return_due
    "rental.payment_received" -> R.string.rentalops_event_payment_received
    "rental.payment_failed" -> R.string.rentalops_event_payment_failed
    "rental.payment_dispute" -> R.string.rentalops_event_payment_dispute
    "rental.autopay_failed" -> R.string.rentalops_event_autopay_failed
    "rental.deposit_auth_expiring" -> R.string.rentalops_event_deposit_auth_expiring
    "rental.marketplace_meetup" -> R.string.rentalops_event_marketplace_meetup
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
    "rental.doc_expiring" -> R.string.rentalops_event_doc_expiring_desc
    "rental.credit_application" -> R.string.rentalops_event_credit_application_desc
    "rental.dunning_hold" -> R.string.rentalops_event_dunning_hold_desc
    "rental.subrental_return_due" -> R.string.rentalops_event_subrental_return_due_desc
    "rental.marketplace_meetup" -> R.string.rentalops_event_marketplace_meetup_desc
    else -> null
}