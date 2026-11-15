package com.avago.feature.growth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.growth.R
import com.avago.feature.growth.data.CaseEvent
import com.avago.feature.growth.data.OwnerReview
import com.avago.feature.growth.data.RenterRating
import com.avago.feature.growth.data.ToRate
import com.avago.feature.growth.data.TrustCase

// ---- Reviews ------------------------------------------------------------------------------

@Composable
fun ReviewsScreen(onBack: () -> Unit, viewModel: ReviewsViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var rating by remember { mutableStateOf<ToRate?>(null) }
    var replying by remember { mutableStateOf<OwnerReview?>(null) }
    NoticeEffect(viewModel.notices, snackbar)
    GrowthScaffold(stringResource(R.string.growth_reviews), onBack, snackbar) { inner ->
        LoadBox(state, viewModel::refresh, inner) { data ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item(key = "summary") {
                    Panel(title = stringResource(R.string.growth_rating_summary)) {
                        if (data.summary.newOwner || data.summary.ownerAverage == null) {
                            Text(stringResource(R.string.growth_new_owner))
                        } else {
                            Stars(data.summary.ownerAverage)
                        }
                        Caption(stringResource(R.string.growth_review_count, data.summary.reviewCount))
                    }
                }
                if (data.toRate.isNotEmpty()) {
                    item(key = "to_rate_h") { ListCaption(stringResource(R.string.growth_to_rate)) }
                    items(data.toRate, key = { "r" + it.orderId }) { t ->
                        Panel {
                            Text(t.customerName ?: stringResource(R.string.growth_renter), style = MaterialTheme.typography.titleSmall)
                            Caption(listOfNotNull(t.orderNumber, t.windowEndsAt?.let { stringResource(R.string.growth_rate_by, Fmt.date(it)) }).joinToString(" · "))
                            OutlinedButton(onClick = { rating = t }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.growth_rate_renter))
                            }
                        }
                    }
                }
                item(key = "reviews_h") { ListCaption(stringResource(R.string.growth_reviews_received)) }
                if (data.reviews.isEmpty()) {
                    item(key = "empty") { EmptyStateView(icon = Icons.Filled.RateReview, title = stringResource(R.string.growth_no_reviews)) }
                }
                items(data.reviews, key = { it.id }) { r -> ReviewCard(r, busy) { replying = r } }
            }
        }
    }
    rating?.let { t ->
        RateRenterDialog(t, onDismiss = { rating = null }) { r ->
            viewModel.rate(t.orderId, r)
            rating = null
        }
    }
    replying?.let { r ->
        TextInputDialog(
            title = stringResource(R.string.growth_reply_title),
            label = stringResource(R.string.growth_reply_label),
            confirm = stringResource(R.string.growth_reply_send),
            required = true,
            onDismiss = { replying = null },
        ) { text ->
            viewModel.reply(r.id, text)
            replying = null
        }
    }
}

@Composable
private fun ReviewCard(r: OwnerReview, busy: Boolean, onReply: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.customerName ?: stringResource(R.string.growth_renter), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (r.status != "published") StatusPill(reviewStatus(r.status), StatusTone.Warning)
        }
        Stars(r.average)
        Caption(listOfNotNull(r.orderNumber, r.revealedAt?.let { Fmt.date(it) }).joinToString(" · "))
        r.body?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
        if (!r.ownerReply.isNullOrBlank()) {
            Column(Modifier.padding(top = 8.dp, start = 12.dp)) {
                Text(stringResource(R.string.growth_your_reply), style = MaterialTheme.typography.labelLarge)
                Text(r.ownerReply)
            }
        } else if (r.canReply) {
            TextButton(onClick = onReply, enabled = !busy) { Text(stringResource(R.string.growth_reply)) }
        }
    }
}

@Composable
private fun reviewStatus(s: String) = when (s) {
    "hidden" -> stringResource(R.string.growth_review_hidden)
    "removed" -> stringResource(R.string.growth_review_removed)
    "pending" -> stringResource(R.string.growth_review_pending)
    else -> s
}

@Composable
private fun RateRenterDialog(t: ToRate, onDismiss: () -> Unit, onSave: (RenterRating) -> Unit) {
    var onTime by rememberSaveable { mutableIntStateOf(0) }
    var care by rememberSaveable { mutableIntStateOf(0) }
    var comm by rememberSaveable { mutableIntStateOf(0) }
    var body by rememberSaveable { mutableStateOf("") }
    val r = RenterRating(onTime, care, comm, body.trim().ifBlank { null })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.growth_rate_title, t.customerName ?: stringResource(R.string.growth_renter))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Caption(stringResource(R.string.growth_rate_private))
                StarPicker(stringResource(R.string.growth_score_on_time), onTime) { onTime = it }
                StarPicker(stringResource(R.string.growth_score_care), care) { care = it }
                StarPicker(stringResource(R.string.growth_score_communication), comm) { comm = it }
                OutlinedTextField(body, { body = it.take(2000) }, label = { Text(stringResource(R.string.growth_note_optional)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(r) }, enabled = r.valid) { Text(stringResource(R.string.growth_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.growth_cancel)) } },
    )
}

/** Single text field dialog; [required] disables confirm while blank. */
@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    confirm: String,
    required: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(2000) }, label = { Text(label) }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = !required || text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.growth_cancel)) } },
    )
}

// ---- Cases --------------------------------------------------------------------------------

@Composable
fun CasesScreen(onBack: () -> Unit, onCase: (String) -> Unit, viewModel: CasesViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openOnly by viewModel.openOnly.collectAsStateWithLifecycle()
    GrowthScaffold(stringResource(R.string.growth_cases), onBack, snackbar) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = openOnly, onClick = { viewModel.setOpenOnly(true) }, label = { Text(stringResource(R.string.growth_filter_open)) })
                FilterChip(selected = !openOnly, onClick = { viewModel.setOpenOnly(false) }, label = { Text(stringResource(R.string.growth_filter_all)) })
            }
            val bottom = androidx.compose.foundation.layout.PaddingValues(bottom = inner.calculateBottomPadding())
            LoadBox(state, viewModel::refresh, bottom) { list ->
                if (list.isEmpty()) {
                    EmptyStateView(icon = Icons.Filled.Gavel, title = stringResource(R.string.growth_no_cases), modifier = Modifier.padding(bottom))
                } else {
                    LazyColumn(contentPadding = bottom, verticalArrangement = ListGap) {
                        items(list, key = { it.id }) { c -> CaseCard(c, Modifier.clickable { onCase(c.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseCard(c: TrustCase, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.subject.ifBlank { caseType(c.caseType) }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            CaseStatusPill(c.status)
        }
        Caption(listOfNotNull(c.caseNumber, caseType(c.caseType), c.orderNumber, c.customerName).joinToString(" · "))
        if (c.open && c.resolveDue != null) {
            Caption(stringResource(if (c.slaBreached) R.string.growth_case_overdue else R.string.growth_case_due, Fmt.dateTime(c.resolveDue)))
        }
    }
}

@Composable
private fun CaseStatusPill(status: String) {
    val (label, tone) = when (status) {
        "open" -> R.string.growth_case_open to StatusTone.Info
        "awaiting_owner" -> R.string.growth_case_awaiting_you to StatusTone.Warning
        "awaiting_renter" -> R.string.growth_case_awaiting_renter to StatusTone.Neutral
        "resolved" -> R.string.growth_case_resolved to StatusTone.Success
        "closed" -> R.string.growth_case_closed to StatusTone.Neutral
        else -> R.string.growth_case_open to StatusTone.Info
    }
    StatusPill(stringResource(label), tone)
}

@Composable
private fun caseType(t: String) = when (t) {
    "damage" -> stringResource(R.string.growth_case_type_damage)
    "late_return" -> stringResource(R.string.growth_case_type_late)
    "no_show" -> stringResource(R.string.growth_case_type_no_show)
    "billing", "payment" -> stringResource(R.string.growth_case_type_billing)
    "review" -> stringResource(R.string.growth_case_type_review)
    else -> t.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
fun CaseDetailScreen(onBack: () -> Unit, viewModel: CaseDetailViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val sent by viewModel.sent.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var ownerOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sent) { if (sent > 0) draft = "" }
    NoticeEffect(viewModel.notices, snackbar)
    GrowthScaffold(stringResource(R.string.growth_case), onBack, snackbar) { inner ->
        LoadBox(state, viewModel::refresh, inner) { d ->
            Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = ListGap, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                    item(key = "head") {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.case.subject.ifBlank { caseType(d.case.caseType) }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                CaseStatusPill(d.case.status)
                            }
                            Caption(listOfNotNull(d.case.caseNumber, d.case.orderNumber, d.case.customerName).joinToString(" · "))
                            d.case.description?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                            d.case.outcomeNote?.takeIf { it.isNotBlank() }?.let {
                                Text(stringResource(R.string.growth_case_outcome, it), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    items(d.events, key = { it.id }) { e -> EventRow(e) }
                }
                HorizontalDivider()
                if (d.case.status == "closed") {
                    Caption(stringResource(R.string.growth_case_closed_note), Modifier.padding(16.dp))
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                draft,
                                { draft = it.take(4000) },
                                label = { Text(stringResource(R.string.growth_message)) },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                            )
                            IconButton(onClick = { viewModel.send(draft.trim(), ownerOnly) }, enabled = !busy && draft.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.growth_send))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(ownerOnly, { ownerOnly = it })
                            Text(stringResource(R.string.growth_internal_note), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: CaseEvent) {
    val who = when (e.actor) {
        "owner" -> stringResource(R.string.growth_actor_you)
        "renter", "customer" -> stringResource(R.string.growth_actor_renter)
        "admin", "support", "avago" -> stringResource(R.string.growth_actor_support)
        else -> stringResource(R.string.growth_actor_system)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(who, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (e.visibility == "owner") StatusPill(stringResource(R.string.growth_internal), StatusTone.Neutral)
        }
        Caption(Fmt.dateTime(e.createdAt))
        Text(e.body?.takeIf { it.isNotBlank() } ?: e.kind.replace('_', ' '), modifier = Modifier.padding(top = 2.dp))
        if (e.photos > 0) Caption(stringResource(R.string.growth_photos_n, e.photos))
    }
}

@Composable
internal fun PrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) { Text(text) }
}
