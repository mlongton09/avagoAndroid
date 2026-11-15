package com.avago.feature.growth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.growth.R
import com.avago.feature.growth.data.ShopTrial

@Composable
fun GrowthHomeScreen(
    onBack: () -> Unit,
    onReviews: () -> Unit,
    onCases: () -> Unit,
    onNetwork: () -> Unit,
    onPayouts: () -> Unit,
    onReferrals: () -> Unit,
    viewModel: GrowthHomeViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val trial by viewModel.trial.collectAsStateWithLifecycle()
    val pending by viewModel.pendingIncoming.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices, snackbar)
    GrowthScaffold(stringResource(R.string.growth_title), onBack, snackbar) { inner ->
        LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
            trial?.let { t -> item(key = "trial") { TrialCard(t, busy, viewModel::startTrial) } }
            item(key = "links") {
                Panel {
                    HubRow(Icons.Filled.RateReview, stringResource(R.string.growth_reviews), stringResource(R.string.growth_reviews_sub), onReviews)
                    HorizontalDivider()
                    HubRow(Icons.Filled.Gavel, stringResource(R.string.growth_cases), stringResource(R.string.growth_cases_sub), onCases)
                    HorizontalDivider()
                    HubRow(Icons.AutoMirrored.Filled.CompareArrows, stringResource(R.string.growth_network), stringResource(R.string.growth_network_sub), onNetwork, badge = pending)
                    HorizontalDivider()
                    HubRow(Icons.Filled.AccountBalance, stringResource(R.string.growth_payouts), stringResource(R.string.growth_payouts_sub), onPayouts)
                    HorizontalDivider()
                    HubRow(Icons.Filled.CardGiftcard, stringResource(R.string.growth_referrals), stringResource(R.string.growth_referrals_sub), onReferrals)
                }
            }
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, badge: Int = 0) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (badge > 0) {
            { Badge { Text(badge.toString()) } }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** Shop trial status and "your data, live" checklist (H). */
@Composable
internal fun TrialCard(t: ShopTrial, busy: Boolean, onStart: () -> Unit) {
    Panel(title = stringResource(R.string.growth_trial_title)) {
        if (t.active) {
            Text(
                t.daysLeft?.let { pluralStringResource(R.plurals.growth_trial_days_left, it, it) } ?: stringResource(R.string.growth_trial_active),
                style = MaterialTheme.typography.titleMedium,
            )
            val total = t.checklist.size.coerceAtLeast(1)
            LinearProgressIndicator(progress = { t.checklistDone.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Caption(stringResource(R.string.growth_trial_progress, t.checklistDone, t.checklist.size))
            Column(Modifier.padding(top = 4.dp)) {
                t.checklist.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (s.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = stringResource(if (s.done) R.string.growth_done else R.string.growth_todo),
                            tint = if (s.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                        Text(trialStepLabel(s.key), modifier = Modifier.weight(1f))
                        s.count?.takeIf { it > 0 }?.let { Caption(it.toString()) }
                    }
                }
            }
        } else {
            Text(pluralStringResource(R.plurals.growth_trial_offer, t.trialDays, t.trialDays))
            Button(onClick = onStart, enabled = !busy, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.growth_trial_start))
            }
        }
    }
}

@Composable
private fun trialStepLabel(key: String): String = when (key) {
    "assets" -> stringResource(R.string.growth_step_assets)
    "customers" -> stringResource(R.string.growth_step_customers)
    "rates" -> stringResource(R.string.growth_step_rates)
    "reservations" -> stringResource(R.string.growth_step_reservations)
    "team" -> stringResource(R.string.growth_step_team)
    "payments" -> stringResource(R.string.growth_step_payments)
    "storefront" -> stringResource(R.string.growth_step_storefront)
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
