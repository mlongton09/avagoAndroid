package com.avago.feature.growth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.json.ApiError
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.KpiGrid
import com.avago.core.ui.components.KpiItem
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.growth.R
import com.avago.feature.growth.data.ConnectStatus
import com.avago.feature.growth.data.Load
import com.avago.feature.growth.data.NetworkRequest
import com.avago.feature.growth.data.Payout
import kotlinx.coroutines.flow.Flow

// ---- Network requests inbox (A) -------------------------------------------------------------

@Composable
fun NetworkInboxScreen(onBack: () -> Unit, viewModel: NetworkInboxViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var pendingAction by remember { mutableStateOf<Pair<NetworkRequest, String>?>(null) }
    NoticeEffect(viewModel.notices, snackbar)
    GrowthScaffold(stringResource(R.string.growth_network), onBack, snackbar) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            val incoming = direction == "incoming"
            TabRow(selectedTabIndex = if (incoming) 0 else 1) {
                Tab(incoming, { viewModel.setDirection("incoming") }, text = { Text(stringResource(R.string.growth_incoming)) })
                Tab(!incoming, { viewModel.setDirection("outgoing") }, text = { Text(stringResource(R.string.growth_outgoing)) })
            }
            val bottom = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = inner.calculateBottomPadding() + 16.dp)
            LoadBox(state, viewModel::refresh, bottom) { data ->
                if (data.requests.isEmpty()) {
                    EmptyStateView(icon = Icons.AutoMirrored.Filled.CompareArrows, title = stringResource(R.string.growth_no_requests), modifier = Modifier.padding(bottom))
                } else {
                    LazyColumn(contentPadding = bottom, verticalArrangement = ListGap) {
                        items(data.requests, key = { it.id }) { r -> RequestCard(r, busy) { action -> pendingAction = r to action } }
                    }
                }
            }
        }
    }
    pendingAction?.let { (r, action) ->
        TextInputDialog(
            title = stringResource(
                when (action) {
                    "approve" -> R.string.growth_approve_title
                    "decline" -> R.string.growth_decline_title
                    else -> R.string.growth_cancel_request_title
                },
            ),
            label = stringResource(R.string.growth_note_optional),
            confirm = stringResource(
                when (action) {
                    "approve" -> R.string.growth_approve
                    "decline" -> R.string.growth_decline
                    else -> R.string.growth_cancel_request
                },
            ),
            required = false,
            onDismiss = { pendingAction = null },
        ) { note ->
            viewModel.decide(r.id, action, note.ifBlank { null })
            pendingAction = null
        }
    }
}

@Composable
private fun RequestCard(r: NetworkRequest, busy: Boolean, onAction: (String) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.itemName ?: "—", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            RequestStatusPill(r.status)
        }
        r.counterparty?.let { Caption(stringResource(if (r.incoming) R.string.growth_from_shop else R.string.growth_to_shop, it)) }
        Caption(stringResource(R.string.growth_period, Fmt.dateTime(r.startAt), Fmt.dateTime(r.endAt)))
        if (r.amountMinor != null && r.currency != null) Caption(Fmt.money(r.amountMinor, r.currency))
        if (r.pending && r.expiresAt != null) Caption(stringResource(R.string.growth_expires, Fmt.dateTime(r.expiresAt)))
        r.borrowerNote?.takeIf { it.isNotBlank() }?.let { Text("“$it”", modifier = Modifier.padding(top = 4.dp)) }
        r.lenderNote?.takeIf { it.isNotBlank() }?.let { Text("“$it”", modifier = Modifier.padding(top = 4.dp)) }
        if (r.canCancel) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (r.canApprove) {
                    TextButton(onClick = { onAction("decline") }, enabled = !busy) { Text(stringResource(R.string.growth_decline)) }
                    OutlinedButton(onClick = { onAction("approve") }, enabled = !busy) { Text(stringResource(R.string.growth_approve)) }
                } else {
                    TextButton(onClick = { onAction("cancel") }, enabled = !busy) { Text(stringResource(R.string.growth_cancel_request)) }
                }
            }
        }
    }
}

@Composable
private fun RequestStatusPill(s: String) {
    val (label, tone) = when (s) {
        "pending" -> R.string.growth_status_pending to StatusTone.Warning
        "approved" -> R.string.growth_status_approved to StatusTone.Success
        "declined" -> R.string.growth_status_declined to StatusTone.Danger
        "cancelled" -> R.string.growth_status_cancelled to StatusTone.Neutral
        else -> R.string.growth_status_expired to StatusTone.Neutral
    }
    StatusPill(stringResource(label), tone)
}

// ---- Payouts (K) ------------------------------------------------------------------------------

@Composable
fun PayoutsScreen(onBack: () -> Unit, viewModel: PayoutsViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val payouts by viewModel.payouts.collectAsStateWithLifecycle()
    val cursor by viewModel.hasMore.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices, snackbar)
    OpenLinksEffect(viewModel.links, snackbar)
    GrowthScaffold(stringResource(R.string.growth_payouts), onBack, snackbar) { inner ->
        val s = status
        if (s is Load.Failed && s.error.status in setOf(403, 503)) {
            Column(Modifier.fillMaxSize().padding(inner)) { PayoutsUnavailable(s.error) }
            return@GrowthScaffold
        }
        LoadBox(s, viewModel::refresh, inner) { st ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item(key = "status") { ConnectStatusCard(st, busy, viewModel::openOnboarding, viewModel::openDashboard) }
                balance?.let { b ->
                    item(key = "balance") {
                        val items = b.available.map { m ->
                            KpiItem(stringResource(R.string.growth_balance_available), Fmt.money(m.amountMinor, m.currency), Icons.Filled.AccountBalance, StatusTone.Success)
                        } + b.pending.map { m ->
                            KpiItem(stringResource(R.string.growth_balance_pending), Fmt.money(m.amountMinor, m.currency), Icons.Filled.AccountBalance, StatusTone.Neutral)
                        }
                        if (items.isNotEmpty()) KpiGrid(items)
                    }
                }
                if (st.available && st.onboardingState != "not_started") {
                    item(key = "payouts_h") { ListCaption(stringResource(R.string.growth_recent_payouts)) }
                    if (payouts.isEmpty()) item(key = "none") { ListCaption(stringResource(R.string.growth_no_payouts)) }
                    items(payouts, key = { it.id }) { p -> PayoutRow(p) }
                    if (cursor != null) {
                        item(key = "more") {
                            TextButton(onClick = viewModel::loadMore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.growth_load_more)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayoutsUnavailable(e: ApiError) {
    val msg = when {
        e.status == 403 -> stringResource(R.string.growth_payouts_forbidden)
        else -> stringResource(R.string.growth_payouts_unavailable)
    }
    EmptyStateView(icon = Icons.Filled.Block, title = stringResource(R.string.growth_payouts), message = msg)
}

@Composable
private fun ConnectStatusCard(st: ConnectStatus, busy: Boolean, onOnboard: () -> Unit, onDashboard: () -> Unit) {
    Panel(title = stringResource(R.string.growth_stripe_account)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.businessName ?: stringResource(R.string.growth_stripe_connect), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val (label, tone) = when (st.onboardingState) {
                "complete" -> R.string.growth_connect_complete to StatusTone.Success
                "pending_verification" -> R.string.growth_connect_pending to StatusTone.Info
                "restricted" -> R.string.growth_connect_restricted to StatusTone.Danger
                "incomplete" -> R.string.growth_connect_incomplete to StatusTone.Warning
                "disconnected" -> R.string.growth_connect_disconnected to StatusTone.Danger
                else -> R.string.growth_connect_not_started to StatusTone.Neutral
            }
            StatusPill(stringResource(label), tone)
        }
        if (!st.available) {
            Caption(stringResource(R.string.growth_payouts_unavailable))
            return@Panel
        }
        Caption(stringResource(if (st.payoutsEnabled) R.string.growth_payouts_on else R.string.growth_payouts_off))
        if (st.pastDue > 0) {
            ErrorBanner(message = stringResource(R.string.growth_requirements_past_due, st.pastDue), modifier = Modifier.padding(top = 8.dp), tone = StatusTone.Danger)
        } else if (st.currentlyDue > 0) {
            Caption(stringResource(R.string.growth_requirements_due, st.currentlyDue))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st.needsOnboarding) {
                OutlinedButton(onClick = onOnboard, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text(
                        stringResource(if (st.onboardingState == "not_started") R.string.growth_onboarding_start else R.string.growth_onboarding_continue),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (st.detailsSubmitted) {
                OutlinedButton(onClick = onDashboard, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text(stringResource(R.string.growth_stripe_dashboard), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PayoutRow(p: Payout) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.money(p.amountMinor, p.currency), style = MaterialTheme.typography.titleSmall)
                Caption(stringResource(R.string.growth_arrives, Fmt.epochDate(p.arrivalDate)))
            }
            val (label, tone) = when (p.status) {
                "paid" -> R.string.growth_payout_paid to StatusTone.Success
                "in_transit" -> R.string.growth_payout_in_transit to StatusTone.Info
                "failed" -> R.string.growth_payout_failed to StatusTone.Danger
                "canceled" -> R.string.growth_payout_canceled to StatusTone.Neutral
                else -> R.string.growth_payout_pending to StatusTone.Warning
            }
            StatusPill(stringResource(label), tone)
        }
        p.failureMessage?.takeIf { it.isNotBlank() }?.let { Caption(it) }
    }
}

/** Opens hosted pages (Stripe, identity verification) in the browser. */
@Composable
internal fun OpenLinksEffect(links: Flow<String>, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(links) {
        links.collect { url -> if (!openUrl(ctx, url)) snackbar.showSnackbar(resources.getString(R.string.growth_no_browser)) }
    }
}

// ---- Referrals (G) ------------------------------------------------------------------------------

@Composable
fun ReferralsScreen(onBack: () -> Unit, viewModel: ReferralsViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val copied = stringResource(R.string.growth_copied)
    val shareMsg = stringResource(R.string.growth_share_message)
    var copyTick by remember { mutableStateOf(0) }
    LaunchedEffect(copyTick) { if (copyTick > 0) snackbar.showSnackbar(copied) }
    GrowthScaffold(stringResource(R.string.growth_referrals), onBack, snackbar) { inner ->
        LoadBox(state, viewModel::refresh, inner) { r ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item(key = "link") {
                    Panel(title = stringResource(R.string.growth_your_link)) {
                        Text(r.link.ifBlank { r.code }, style = MaterialTheme.typography.bodyLarge)
                        Caption(stringResource(R.string.growth_code, r.code))
                        r.holdDays?.let { Caption(stringResource(R.string.growth_referral_terms, it)) }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { shareText(ctx, "$shareMsg ${r.link}".trim()) }, enabled = r.link.isNotBlank()) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Text(stringResource(R.string.growth_share), modifier = Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(onClick = { copyText(ctx, "referral", r.link.ifBlank { r.code }); copyTick++ }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Text(stringResource(R.string.growth_copy), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                item(key = "kpis") {
                    KpiGrid(
                        listOf(
                            KpiItem(stringResource(R.string.growth_ref_signed_up), r.signedUp.toString(), Icons.Filled.PersonAdd, StatusTone.Info),
                            KpiItem(stringResource(R.string.growth_ref_activated), r.activated.toString(), Icons.Filled.CheckCircle, StatusTone.Accent),
                            KpiItem(stringResource(R.string.growth_ref_rewarded), r.rewarded.toString(), Icons.Filled.EmojiEvents, StatusTone.Success),
                            KpiItem(stringResource(R.string.growth_ref_blocked), r.blocked.toString(), Icons.Filled.Block, StatusTone.Neutral),
                        ),
                    )
                }
                item(key = "list_h") { ListCaption(stringResource(R.string.growth_referred_shops)) }
                if (r.referrals.isEmpty()) {
                    item(key = "empty") { EmptyStateView(icon = Icons.Filled.CardGiftcard, title = stringResource(R.string.growth_no_referrals)) }
                }
                items(r.referrals, key = { it.id }) { ref ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ref.accountName ?: "—", style = MaterialTheme.typography.titleSmall)
                                Caption(stringResource(R.string.growth_signed_up_on, Fmt.date(ref.signedUpAt)))
                            }
                            val (label, tone) = when (ref.status) {
                                "rewarded" -> R.string.growth_ref_rewarded to StatusTone.Success
                                "activated", "qualified" -> R.string.growth_ref_activated to StatusTone.Accent
                                "blocked", "rejected" -> R.string.growth_ref_blocked to StatusTone.Danger
                                else -> R.string.growth_ref_signed_up to StatusTone.Info
                            }
                            StatusPill(stringResource(label), tone)
                        }
                    }
                }
            }
        }
    }
}

// ---- Renter portal (E) ------------------------------------------------------------------------

@Composable
fun PortalScreen(onBack: () -> Unit, viewModel: PortalViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val verification by viewModel.verification.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices, snackbar)
    OpenLinksEffect(viewModel.links, snackbar)
    GrowthScaffold(stringResource(R.string.growth_portal_title), onBack, snackbar) { inner ->
        LoadBox(session, viewModel::start, inner) { s ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item(key = "hello") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            s.customerName?.let { stringResource(R.string.growth_portal_hello, it) } ?: stringResource(R.string.growth_portal_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        s.shopName?.let { Caption(it) }
                    }
                }
                item(key = "verification") {
                    when (val v = verification) {
                        is Load.Loading -> Unit
                        is Load.Failed -> ErrorBanner(message = errorText(LocalContext.current, v.error), modifier = Modifier.padding(horizontal = 16.dp), onRetry = viewModel::loadData, retryLabel = stringResource(R.string.growth_retry))
                        is Load.Ready -> Panel(title = stringResource(R.string.growth_verification)) {
                            val vv = v.value
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = if (vv.verifiedRenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                Text(
                                    stringResource(if (vv.verifiedRenter) R.string.growth_verified_renter else R.string.growth_not_verified_renter),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            vv.verifiedName?.let { Caption(it) }
                            Level(R.string.growth_level_email, vv.emailVerified)
                            Level(R.string.growth_level_phone, vv.phoneVerified)
                            Level(R.string.growth_level_payment, vv.paymentVerified)
                            Level(R.string.growth_level_id, vv.idVerified)
                            Level(R.string.growth_level_licence, vv.licenceVerified)
                            if (!vv.goodStanding) Caption(stringResource(R.string.growth_not_good_standing))
                            if (vv.available && (!vv.idVerified || !vv.licenceVerified)) {
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!vv.idVerified) OutlinedButton(onClick = { viewModel.verify("identity") }, enabled = !busy) { Text(stringResource(R.string.growth_verify_id)) }
                                    if (!vv.licenceVerified) OutlinedButton(onClick = { viewModel.verify("driver_licence") }, enabled = !busy) { Text(stringResource(R.string.growth_verify_licence)) }
                                }
                            }
                        }
                    }
                }
                item(key = "reviews_h") { ListCaption(stringResource(R.string.growth_my_reviews)) }
                when (val rv = reviews) {
                    is Load.Ready -> {
                        if (rv.value.isEmpty()) item(key = "none") { ListCaption(stringResource(R.string.growth_no_portal_reviews)) }
                        items(rv.value, key = { it.orderId }) { r ->
                            Panel {
                                Text(r.orderNumber ?: "—", style = MaterialTheme.typography.titleSmall)
                                when {
                                    r.myStatus != null -> Caption(stringResource(R.string.growth_review_submitted))
                                    r.canSubmit -> Caption(stringResource(R.string.growth_review_open_until, Fmt.date(r.windowEndsAt)))
                                    else -> Caption(stringResource(R.string.growth_review_closed))
                                }
                                r.myBody?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                                r.ownerReply?.takeIf { it.isNotBlank() }?.let { Caption(stringResource(R.string.growth_owner_replied, it)) }
                                if (r.revealed && r.ownerRatingAverage != null) {
                                    Text(stringResource(R.string.growth_owner_rated_you), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                                    Stars(r.ownerRatingAverage)
                                    r.ownerRatingBody?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                }
                            }
                        }
                    }
                    is Load.Failed -> item(key = "err") {
                        ErrorBanner(message = errorText(LocalContext.current, rv.error), modifier = Modifier.padding(horizontal = 16.dp), onRetry = viewModel::loadData, retryLabel = stringResource(R.string.growth_retry))
                    }
                    is Load.Loading -> Unit
                }
            }
        }
    }
}

@Composable
private fun Level(label: Int, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        StatusPill(stringResource(if (ok) R.string.growth_verified else R.string.growth_not_verified), if (ok) StatusTone.Success else StatusTone.Neutral)
    }
}
