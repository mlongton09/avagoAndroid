package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import com.avago.feature.rentals.marketplace.domain.MktFulfilment
import com.avago.feature.rentals.marketplace.domain.MktReservation
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.QtyStepper
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.SectionHeader
import com.avago.feature.rentals.ui.readableWidth
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MktReserveScreen(
    onBack: () -> Unit,
    onSignIn: (next: String) -> Unit,
    onRequests: () -> Unit,
    onDone: () -> Unit,
    vm: MktReserveViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    MktReserveContent(
        state = state,
        signedIn = session != null,
        onBack = onBack,
        actions = MktReserveActions(
            onSignIn = { onSignIn("/marketplace/${vm.id}") },
            onRetry = vm::load,
            onDates = vm::setDates,
            onTime = vm::setTime,
            onQty = vm::setQty,
            onFulfilment = vm::setFulfilment,
            onAddress = vm::setAddress,
            onNotes = vm::setNotes,
            onSubmit = vm::submit,
            onRequests = onRequests,
            onDone = onDone,
        ),
    )
}

internal class MktReserveActions(
    val onSignIn: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDates: (LocalDate, LocalDate?) -> Unit = { _, _ -> },
    val onTime: (LocalTime) -> Unit = {},
    val onQty: (Int) -> Unit = {},
    val onFulfilment: (String) -> Unit = {},
    val onAddress: (String) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onRequests: () -> Unit = {},
    val onDone: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MktReserveContent(state: MktReserveState, signedIn: Boolean, onBack: () -> Unit, actions: MktReserveActions) {
    val l = state.listing
    val quote = l?.isQuote == true
    var pickDates by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { RentalsTopBar(stringResource(if (quote) R.string.mkt_quote_heading else R.string.mkt_reserve_heading), onBack) },
        bottomBar = {
            if (l != null && signedIn && state.done == null) {
                BottomActionBar {
                    Button(onClick = actions.onSubmit, enabled = !state.submitting, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                        if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (quote) R.string.mkt_submit_quote else R.string.mkt_submit_hold))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.done != null -> DoneView(
                    stringResource(if (state.done.isQuote || quote) R.string.mkt_quote_sent_title else R.string.mkt_hold_placed_title),
                    state.done.reference?.let { stringResource(R.string.mkt_reserve_done_body, it) }.orEmpty(),
                    actions.onRequests, actions.onDone,
                )
                !signedIn -> SignInPrompt(stringResource(R.string.mkt_sign_in_to_book), actions.onSignIn)
                l == null && state.loadError != null -> EmptyState(
                    mktErrorText(state.loadError), icon = Icons.Outlined.ErrorOutline,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = actions.onRetry,
                )
                l == null -> SkeletonList(rows = 4)
                else -> Column(
                    Modifier.verticalScroll(rememberScrollState()).readableWidth().padding(horizontal = AvagoSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                ) {
                    val locale = LocalConfiguration.current.locales[0]
                    Text(l.title(locale), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = AvagoSpacing.sm))
                    Text(stringResource(if (quote) R.string.mkt_quote_explainer else R.string.mkt_hold_explainer), style = MaterialTheme.typography.bodyMedium)
                    state.error?.let { ErrorBanner(mktErrorText(it)) }

                    SectionHeader(stringResource(R.string.mkt_dates), Modifier.padding(start = 0.dp))
                    OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                        val first = state.firstDay
                        Text(if (first == null) stringResource(R.string.mkt_pick_dates) else dayRange(first, state.lastDay))
                    }
                    state.days?.let { n ->
                        Text(
                            if (n == 1L) stringResource(R.string.mkt_one_day) else stringResource(R.string.mkt_days_n, n.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    l.earliestStart?.takeIf { it.isAfter(java.time.Instant.now()) }?.let { e ->
                        Text(
                            stringResource(R.string.mkt_earliest_start, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(java.time.ZoneId.systemDefault()).format(e)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SectionHeader(stringResource(R.string.mkt_pickup_time), Modifier.padding(start = 0.dp))
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(state.time))
                    }
                    SectionHeader(stringResource(R.string.mkt_qty), Modifier.padding(start = 0.dp))
                    QtyStepper(state.qty, actions.onQty)

                    if (l.fulfilment.pickup && l.fulfilment.delivery) {
                        SectionHeader(stringResource(R.string.mkt_fulfilment), Modifier.padding(start = 0.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                            FilterChip(state.fulfilment == MktFulfilment.PICKUP, { actions.onFulfilment(MktFulfilment.PICKUP) }, { Text(stringResource(R.string.mkt_ful_pickup)) }, Modifier.heightIn(min = MinTouch))
                            FilterChip(state.fulfilment == MktFulfilment.DELIVERY, { actions.onFulfilment(MktFulfilment.DELIVERY) }, { Text(stringResource(R.string.mkt_ful_delivery)) }, Modifier.heightIn(min = MinTouch))
                        }
                    }
                    if (state.fulfilment == MktFulfilment.DELIVERY) {
                        OutlinedTextField(
                            value = state.address, onValueChange = actions.onAddress,
                            label = { Text(stringResource(R.string.mkt_delivery_address)) },
                            isError = state.error == MktError.AddressRequired,
                            minLines = 2, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = state.notes, onValueChange = actions.onNotes,
                        label = { Text(stringResource(R.string.mkt_notes)) },
                        minLines = 2, modifier = Modifier.fillMaxWidth(),
                    )
                    InfoNote(stringResource(if (l.onlinePayments) R.string.mkt_pay_online_note else R.string.mkt_pay_in_person_note), Modifier.padding(bottom = AvagoSpacing.lg))
                }
            }
        }
    }
    if (pickDates) {
        DateRangeDialog(state.firstDay, state.lastDay, onDismiss = { pickDates = false }, onPick = { a, b -> actions.onDates(a, b); pickDates = false })
    }
    if (pickTime) {
        TimeDialog(state.time, onDismiss = { pickTime = false }, onPick = { actions.onTime(it); pickTime = false })
    }
}

@MktPreviews
@Composable
private fun MktReservePreview() {
    AvagoTheme {
        val today = LocalDate.now()
        MktReserveContent(
            MktReserveState(listing = previewListing, firstDay = today.plusDays(2), lastDay = today.plusDays(4), fulfilment = MktFulfilment.DELIVERY),
            signedIn = true, onBack = {}, actions = MktReserveActions(),
        )
    }
}

@MktPreviews
@Composable
private fun MktReserveDonePreview() {
    AvagoTheme {
        MktReserveContent(
            MktReserveState(listing = previewListing, done = MktReservation("o1", "R-1042", "held", false, false)),
            signedIn = true, onBack = {}, actions = MktReserveActions(),
        )
    }
}
