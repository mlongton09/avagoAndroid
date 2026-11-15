package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktMeetSlots
import com.avago.feature.rentals.marketplace.domain.MktSlot
import com.avago.feature.rentals.marketplace.domain.MktSlots
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.timeRange
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.SectionHeader
import com.avago.feature.rentals.ui.readableWidth
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MktMeetScreen(
    onBack: () -> Unit,
    onSignIn: (next: String) -> Unit,
    onRequests: () -> Unit,
    onDone: () -> Unit,
    vm: MktMeetViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    MktMeetContent(
        state, signedIn = session != null, onBack = onBack,
        actions = MktMeetActions(
            onSignIn = { onSignIn("/marketplace/${vm.id}") },
            onRetry = vm::load, onToggle = vm::toggle, onMessage = vm::setMessage,
            onIncludePeriod = vm::setIncludePeriod, onPeriod = vm::setPeriod,
            onSubmit = vm::submit, onRequests = onRequests, onDone = onDone,
        ),
    )
}

internal class MktMeetActions(
    val onSignIn: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggle: (MktSlot) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onIncludePeriod: (Boolean) -> Unit = {},
    val onPeriod: (LocalDate, LocalDate?) -> Unit = { _, _ -> },
    val onSubmit: () -> Unit = {},
    val onRequests: () -> Unit = {},
    val onDone: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MktMeetContent(state: MktMeetState, signedIn: Boolean, onBack: () -> Unit, actions: MktMeetActions) {
    var pickPeriod by rememberSaveable { mutableStateOf(false) }
    val zone = state.zone
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.mkt_tab_meetup), onBack) },
        bottomBar = {
            if (signedIn && state.done == null && state.slots?.slots?.isNotEmpty() == true) {
                BottomActionBar {
                    Button(
                        onClick = actions.onSubmit,
                        enabled = !state.submitting && state.selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) {
                        if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.mkt_submit_meetup))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val slots = state.slots
            when {
                state.done != null -> DoneView(
                    stringResource(R.string.mkt_meetup_sent_title), stringResource(R.string.mkt_meetup_sent_body),
                    actions.onRequests, actions.onDone,
                )
                !signedIn -> SignInPrompt(stringResource(R.string.mkt_sign_in_to_book), actions.onSignIn)
                slots == null && state.loading -> SkeletonList(rows = 5)
                slots == null -> EmptyState(
                    mktErrorText(state.loadError ?: MktError.Generic), icon = Icons.Outlined.ErrorOutline,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = actions.onRetry,
                )
                slots.slots.isEmpty() -> EmptyState(stringResource(R.string.mkt_no_slots), icon = Icons.Outlined.EventBusy)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "intro") {
                        androidx.compose.foundation.layout.Column(
                            Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs),
                        ) {
                            state.listing?.let { Text(it.title(locale), style = MaterialTheme.typography.titleLarge) }
                            Text(stringResource(R.string.mkt_meetup_explainer, zone.id), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.mkt_selected_n, state.selected.size, MktSlots.MAX),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            )
                            state.error?.let { ErrorBanner(mktErrorText(it)) }
                        }
                    }
                    MktSlots.groupByDay(slots.slots, zone).forEach { (day, list) ->
                        item(key = "d:$day") {
                            SectionHeader(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(day))
                        }
                        item(key = "s:$day") {
                            FlowRow(
                                Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg),
                                horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                            ) {
                                list.forEach { s ->
                                    val on = state.selected.any { it.start == s.start }
                                    FilterChip(
                                        selected = on,
                                        onClick = { actions.onToggle(s) },
                                        enabled = on || state.selected.size < MktSlots.MAX,
                                        label = { Text(timeRange(s, zone, locale)) },
                                        modifier = Modifier.heightIn(min = MinTouch),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "form") {
                        androidx.compose.foundation.layout.Column(
                            Modifier.readableWidth().padding(AvagoSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                        ) {
                            OutlinedTextField(
                                value = state.message, onValueChange = actions.onMessage,
                                label = { Text(stringResource(R.string.mkt_message_to_owner)) },
                                placeholder = { Text(stringResource(R.string.mkt_message_placeholder)) },
                                supportingText = { Text("${state.message.length}/${MktSlots.MESSAGE_LIMIT}") },
                                minLines = 3, modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MinTouch)
                                    .toggleable(state.includePeriod, role = Role.Switch, onValueChange = actions.onIncludePeriod),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.mkt_include_period), modifier = Modifier.weight(1f))
                                Switch(checked = state.includePeriod, onCheckedChange = null)
                            }
                            if (state.includePeriod) {
                                OutlinedButton(onClick = { pickPeriod = true }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                    val a = state.firstDay
                                    Text(if (a == null) stringResource(R.string.mkt_pick_dates) else dayRange(a, state.lastDay))
                                }
                            }
                            Text(stringResource(R.string.mkt_meetup_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (pickPeriod) {
        DateRangeDialog(state.firstDay, state.lastDay, onDismiss = { pickPeriod = false }, onPick = { a, b -> actions.onPeriod(a, b); pickPeriod = false })
    }
}

@MktPreviews
@Composable
private fun MktMeetPreview() {
    AvagoTheme {
        val zone = ZoneId.of("America/Los_Angeles")
        val base = LocalDate.now().plusDays(2).atTime(10, 0).atZone(zone).toInstant()
        val slots = (0 until 6).map { i ->
            val s: Instant = base.plus(Duration.ofMinutes(30L * i)).plus(Duration.ofDays((i / 3).toLong()))
            MktSlot(s, s.plus(Duration.ofMinutes(30)))
        }
        MktMeetContent(
            MktMeetState(
                listing = previewListing, loading = false, slots = MktMeetSlots("loc1", zone.id, 30, slots),
                selected = slots.take(1), message = "Can I see the hitch?",
            ),
            signedIn = true, onBack = {}, actions = MktMeetActions(),
        )
    }
}
