package com.avago.feature.rentals.marketplace.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktOwnerLocation
import com.avago.feature.rentals.marketplace.domain.MktOwnerMarketplace
import com.avago.feature.rentals.marketplace.domain.MktOwnerSettings
import com.avago.feature.rentals.marketplace.domain.MktReadiness
import com.avago.feature.rentals.marketplace.domain.MktWindows
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.rememberLocationRequest
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth

@Composable
fun MktOwnerSettingsScreen(
    onBack: () -> Unit,
    onListings: () -> Unit,
    onBlocks: () -> Unit,
    vm: MktOwnerSettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val requestLocation = rememberLocationRequest(precise = true) { vm.useCurrentLocation() }
    MktOwnerSettingsContent(
        state, onBack,
        MktSettingsActions(
            onListings = onListings, onBlocks = onBlocks, onRetry = vm::load, onEdit = vm::edit, onSave = vm::save,
            onDiscard = vm::discard, onNoticeShown = vm::noticeShown, onEditLocation = vm::editLocation,
            onCloseLocation = vm::closeLocation, onLat = vm::setLat, onLng = vm::setLng,
            onUseCurrent = requestLocation, onSaveLocation = vm::saveLocation,
        ),
    )
}

internal class MktSettingsActions(
    val onListings: () -> Unit = {},
    val onBlocks: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEdit: ((MktSettingsDraft) -> MktSettingsDraft) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val onEditLocation: (MktOwnerLocation) -> Unit = {},
    val onCloseLocation: () -> Unit = {},
    val onLat: (String) -> Unit = {},
    val onLng: (String) -> Unit = {},
    val onUseCurrent: () -> Unit = {},
    val onSaveLocation: () -> Unit = {},
)

/** "You need the … permission" full-screen state shared by the owner screens. */
@Composable
internal fun NoPermission(message: String) {
    EmptyState(stringResource(R.string.mkt_no_permission_title), icon = Icons.Outlined.Lock, message = message)
}

@Composable
internal fun MktOwnerSettingsContent(state: MktSettingsState, onBack: () -> Unit, actions: MktSettingsActions) {
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.mkt_admin_saved)
    val locSaved = stringResource(R.string.mkt_location_saved)
    val reloaded = stringResource(R.string.mkt_admin_err_version_conflict)
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(
                when (it) {
                    MktSettingsNotice.Saved -> saved
                    MktSettingsNotice.LocationSaved -> locSaved
                    MktSettingsNotice.Reloaded -> reloaded
                },
            )
            actions.onNoticeShown()
        }
    }
    Scaffold(
        topBar = {
            RentalsTopBar(stringResource(R.string.mkt_settings_title), onBack) {
                if (state.allowed) {
                    IconButton(onClick = actions.onListings) { Icon(Icons.Outlined.Inventory2, contentDescription = stringResource(R.string.mkt_my_listings)) }
                    IconButton(onClick = actions.onBlocks) { Icon(Icons.Outlined.Block, contentDescription = stringResource(R.string.mkt_inbox_blocks_title)) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.allowed && state.data != null && (state.dirty || state.saving)) {
                BottomActionBar {
                    Text(stringResource(R.string.mkt_admin_unsaved), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                    TextButton(onClick = actions.onDiscard, enabled = !state.saving, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_admin_discard)) }
                    Button(onClick = actions.onSave, enabled = !state.saving, modifier = Modifier.heightIn(min = MinTouch)) {
                        if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.mkt_admin_save))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val data = state.data
            when {
                !state.allowed -> NoPermission(stringResource(R.string.mkt_admin_no_permission))
                data == null && state.loading -> SkeletonList(rows = 6)
                data == null -> EmptyState(
                    mktErrorText(state.loadError ?: MktError.Generic, owner = true), icon = Icons.Outlined.Storefront,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = actions.onRetry,
                )
                else -> SettingsBody(state, data, actions)
            }
        }
    }
    state.editing?.let { LocationDialog(state, it, actions) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsBody(state: MktSettingsState, data: MktOwnerMarketplace, actions: MktSettingsActions) {
    val d = state.draft
    val r = data.readiness
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = AvagoSpacing.xxl)) {
        Text(
            stringResource(R.string.mkt_admin_subtitle), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
        )
        state.error?.let { ErrorBanner(mktErrorText(it, owner = true), modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg)) }

        // Status + opt-in toggle
        SectionCard(
            Modifier.readableWidth(), title = stringResource(R.string.mkt_admin_status_title),
            trailing = {
                StatusPill(
                    stringResource(if (data.settings.enabled) R.string.mkt_admin_listed_on else R.string.mkt_admin_listed_off),
                    if (data.settings.enabled) StatusTone.Success else StatusTone.Neutral,
                )
            },
            contentPadding = true,
        ) {
            Text(stringResource(R.string.mkt_admin_status_body), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.mkt_admin_checklist), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = AvagoSpacing.sm))
            CheckRow(r.storefrontEnabled, stringResource(R.string.mkt_admin_check_storefront))
            CheckRow(r.publicClasses > 0, stringResource(R.string.mkt_admin_check_classes, r.publicClasses.toString()))
            CheckRow(r.hasLocatedOfferedLocation, stringResource(R.string.mkt_admin_check_location))
            Text(stringResource(R.string.mkt_admin_listed_count, r.listedClasses.toString()), style = MaterialTheme.typography.bodySmall)
            val canToggle = d.enabled || data.settings.enabled || r.canEnable
            SwitchRow(stringResource(R.string.mkt_admin_toggle), d.enabled, enabled = canToggle) { on -> actions.onEdit { it.copy(enabled = on) } }
            if (!canToggle) Text(stringResource(R.string.mkt_admin_toggle_blocked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.mkt_admin_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Locations
        SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_admin_locations_title)) {
            Text(
                stringResource(R.string.mkt_admin_locations_body), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
            )
            if (r.locations.isEmpty()) {
                Text(stringResource(R.string.mkt_admin_no_locations), modifier = Modifier.padding(AvagoSpacing.lg))
            }
            r.locations.forEach { l ->
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = {
                        Column {
                            if (l.addressLine.isNotBlank()) Text(l.addressLine)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                                StatusPill(
                                    stringResource(if (l.offered) R.string.mkt_admin_loc_offered else R.string.mkt_admin_loc_not_offered),
                                    if (l.offered) StatusTone.Info else StatusTone.Neutral,
                                )
                                StatusPill(
                                    stringResource(if (l.hasCoordinates) R.string.mkt_admin_loc_on_map else R.string.mkt_admin_loc_no_map),
                                    if (l.hasCoordinates) StatusTone.Success else StatusTone.Warning,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { actions.onEditLocation(l) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_edit_location)) }
                    },
                )
            }
        }

        // Profile + fulfilment
        SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_admin_profile_title), contentPadding = true) {
            OutlinedTextField(
                d.displayName, { v -> actions.onEdit { it.copy(displayName = v.take(120)) } },
                label = { Text(stringResource(R.string.mkt_admin_display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                d.about, { v -> actions.onEdit { it.copy(about = v.take(2000)) } },
                label = { Text(stringResource(R.string.mkt_admin_about)) },
                supportingText = { Text(stringResource(R.string.mkt_admin_about_hint)) },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_admin_fulfilment_title), contentPadding = true) {
            SwitchRow(stringResource(R.string.mkt_admin_offer_pickup), d.offerPickup) { on -> actions.onEdit { it.copy(offerPickup = on) } }
            SwitchRow(stringResource(R.string.mkt_admin_offer_delivery), d.offerDelivery) { on -> actions.onEdit { it.copy(offerDelivery = on) } }
            if (d.offerDelivery) {
                OutlinedTextField(
                    d.deliveryRadiusKm, { v -> actions.onEdit { it.copy(deliveryRadiusKm = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)) } },
                    label = { Text(stringResource(R.string.mkt_delivery_radius_km)) },
                    isError = state.draftError?.code == "radius",
                    supportingText = if (state.draftError?.code == "radius") {
                        { Text(stringResource(R.string.mkt_admin_err_range, "0", MktSettingsForm.RADIUS_MAX.toString())) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            SwitchRow(stringResource(R.string.mkt_admin_offer_meetup), d.offerMeetup) { on -> actions.onEdit { it.copy(offerMeetup = on) } }
        }

        // Meet-up hours
        if (d.offerMeetup) MeetHours(state, actions)
        Text(
            stringResource(R.string.mkt_enable_hint_save), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetHours(state: MktSettingsState, actions: MktSettingsActions) {
    val d = state.draft
    val err = state.draftError
    SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_admin_meet_title), contentPadding = true) {
        Text(stringResource(R.string.mkt_admin_meet_body), style = MaterialTheme.typography.bodySmall)
        if (err?.code == "no_windows") Text(stringResource(R.string.mkt_admin_err_no_windows), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        MktWindows.DAYS.forEach { day ->
            val dayLabel = stringResource(dayRes(day))
            val bad = err?.code == "windows" && err.day == day
            OutlinedTextField(
                d.windows[day].orEmpty(), { v -> actions.onEdit { it.copy(windows = it.windows + (day to v.take(120))) } },
                label = { Text(dayLabel) },
                placeholder = { Text(stringResource(R.string.mkt_admin_closed)) },
                isError = bad,
                supportingText = if (bad) {
                    { Text(stringResource(R.string.mkt_admin_err_windows, dayLabel)) }
                } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.mkt_admin_windows_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.mkt_admin_slot_minutes), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = AvagoSpacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            (MktSettingsForm.SLOT_CHOICES + d.slotMinutes).distinct().sorted().forEach { m ->
                FilterChip(
                    selected = d.slotMinutes == m, onClick = { actions.onEdit { it.copy(slotMinutes = m) } },
                    label = { Text(stringResource(R.string.mkt_admin_minutes, m.toString())) }, modifier = Modifier.heightIn(min = MinTouch),
                )
            }
        }
        OutlinedTextField(
            d.leadHours, { v -> actions.onEdit { it.copy(leadHours = v.filter(Char::isDigit).take(3)) } },
            label = { Text(stringResource(R.string.mkt_admin_lead_hours)) },
            isError = err?.code == "lead",
            supportingText = if (err?.code == "lead") {
                { Text(stringResource(R.string.mkt_admin_err_range, "0", MktSettingsForm.LEAD_MAX.toString())) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            d.timezone, { v -> actions.onEdit { it.copy(timezone = v.take(64)) } },
            label = { Text(stringResource(R.string.mkt_admin_timezone)) },
            placeholder = { Text(stringResource(R.string.mkt_admin_tz_placeholder)) },
            isError = err?.code == "timezone",
            supportingText = { Text(stringResource(R.string.mkt_admin_timezone_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            d.blackouts, { v -> actions.onEdit { it.copy(blackouts = v.take(2000)) } },
            label = { Text(stringResource(R.string.mkt_admin_blackouts)) },
            placeholder = { Text(stringResource(R.string.mkt_admin_blackouts_placeholder)) },
            isError = err?.code == "blackouts",
            supportingText = {
                Text(stringResource(if (err?.code == "blackouts") R.string.mkt_admin_err_blackouts else R.string.mkt_admin_blackouts_hint))
            },
            minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun dayRes(day: String): Int = when (day) {
    "mon" -> R.string.mkt_day_mon
    "tue" -> R.string.mkt_day_tue
    "wed" -> R.string.mkt_day_wed
    "thu" -> R.string.mkt_day_thu
    "fri" -> R.string.mkt_day_fri
    "sat" -> R.string.mkt_day_sat
    else -> R.string.mkt_day_sun
}

@Composable
private fun CheckRow(done: Boolean, text: String) {
    val status = stringResource(if (done) R.string.mkt_admin_check_done else R.string.mkt_admin_check_todo)
    val desc = "$text: $status"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clearAndSetSemantics { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = AvagoSpacing.sm))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun LocationDialog(state: MktSettingsState, l: MktOwnerLocation, actions: MktSettingsActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseLocation,
        title = { Text(l.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                if (l.addressLine.isNotBlank()) Text(l.addressLine, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.mkt_admin_coords_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = actions.onUseCurrent, enabled = !state.locating, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                    if (state.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.MyLocation, contentDescription = null)
                    Text(" " + stringResource(R.string.mkt_admin_use_current_location))
                }
                if (state.locationError == MktError.Generic) {
                    Text(stringResource(R.string.mkt_admin_geo_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    state.locationError?.let { Text(mktErrorText(it, owner = true), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                OutlinedTextField(
                    state.lat, actions.onLat, label = { Text(stringResource(R.string.mkt_admin_latitude)) },
                    placeholder = { Text(stringResource(R.string.mkt_admin_lat_placeholder)) }, isError = state.coordsError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.lng, actions.onLng, label = { Text(stringResource(R.string.mkt_admin_longitude)) },
                    placeholder = { Text(stringResource(R.string.mkt_admin_lng_placeholder)) }, isError = state.coordsError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (state.coordsError) Text(stringResource(R.string.mkt_admin_err_coords), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSaveLocation, enabled = !state.savingLocation, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.mkt_admin_save_location))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCloseLocation, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) }
        },
    )
}

internal val previewOwnerMarketplace = MktOwnerMarketplace(
    settings = MktOwnerSettings(
        enabled = true, displayName = "Cariboo Rentals", about = "Trailers and small equipment.", offerPickup = true, offerDelivery = true,
        deliveryRadiusKm = 40.0, offerMeetup = true, meetAvailability = mapOf("mon" to listOf("09:00" to "17:00")), meetBlackouts = emptyList(),
        slotMinutes = 30, leadHours = 24, timezone = "America/Vancouver", serverVersion = 3,
    ),
    readiness = MktReadiness(
        storefrontConfigured = true, storefrontEnabled = true, slug = "acme", bookingMode = "hold", publicClasses = 6, listedClasses = 5,
        locations = listOf(
            MktOwnerLocation("loc1", "Main yard", "100 Birch Ave", null, "100 Mile House", "BC", "V0K 2E0", "CA", 51.64, -121.29, "America/Vancouver", true, emptyMap()),
        ),
    ),
    categories = emptyList(),
    publicUrl = null,
)

@MktPreviews
@Composable
private fun MktOwnerSettingsPreview() {
    AvagoTheme {
        val data = previewOwnerMarketplace
        MktOwnerSettingsContent(
            MktSettingsState(loading = false, data = data, draft = MktSettingsDraft.of(data.settings).copy(about = "Changed")),
            onBack = {}, actions = MktSettingsActions(),
        )
    }
}
