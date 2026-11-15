package com.avago.feature.rentals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.FeeBreakdown
import com.avago.feature.rentals.domain.InstantBookCheck

/** String for an instant-book reason code (AvagoSvc docs/rentals-api-instant-network.md §2.2). */
internal fun instantReasonLabel(code: String): Int = when (code) {
    "not_enabled" -> R.string.rentals_ib_not_enabled
    "lead_time" -> R.string.rentals_ib_lead_time
    "max_duration" -> R.string.rentals_ib_max_duration
    "delivery_not_allowed" -> R.string.rentals_ib_delivery
    "renter_not_verified", "renter_verification_unavailable" -> R.string.rentals_ib_renter_verified
    "licence_not_verified", "licence_verification_unavailable" -> R.string.rentals_ib_licence
    "rating_too_low", "rating_unavailable" -> R.string.rentals_ib_rating
    "repeat_renters_only" -> R.string.rentals_ib_repeat
    "buffer_conflict" -> R.string.rentals_ib_buffer
    "operator_unavailable" -> R.string.rentals_ib_operator
    "payments_not_configured" -> R.string.rentals_ib_payments
    "not_available" -> R.string.rentals_unavailable
    else -> R.string.rentals_ib_other
}

/** Instant-book badge under the QuickBook price: whether an online booking would skip approval. */
@Composable
internal fun InstantBookCard(c: InstantBookCheck) {
    SectionCard(title = stringResource(R.string.rentals_ib_title), contentPadding = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.eligible) {
                    StatusPill(stringResource(R.string.rentals_ib_instant), StatusTone.Success, icon = Icons.Filled.Bolt)
                } else {
                    StatusPill(stringResource(R.string.rentals_ib_request), StatusTone.Neutral, icon = Icons.Outlined.HourglassEmpty)
                }
            }
            Text(
                stringResource(
                    when {
                        !c.eligible -> R.string.rentals_ib_request_note
                        c.outcome == "instant_confirmed" -> R.string.rentals_ib_confirmed_note
                        else -> R.string.rentals_ib_pay_note
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            c.reasons.map { instantReasonLabel(it) }.distinct().forEach { res ->
                Text("• " + stringResource(res), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Platform fee, renter fee and net payout for an order (Connect, feature K). */
@Composable
internal fun FeeBreakdownCard(f: FeeBreakdown) {
    SectionCard(
        title = stringResource(R.string.rentals_fees_title),
        contentPadding = true,
        trailing = {
            StatusPill(
                stringResource(if (f.connect) R.string.rentals_fees_mode_connect else R.string.rentals_fees_mode_keys),
                if (f.connect) StatusTone.Info else StatusTone.Neutral,
            )
        },
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            LabelValue(stringResource(R.string.rentals_fees_order_total), formatMoney(f.orderTotal))
            f.renterFee?.let { LabelValue(stringResource(R.string.rentals_fees_renter_fee, FeeBreakdown.percent(f.renterFeeBp)), formatMoney(it)) }
            if (f.renterFee != null) LabelValue(stringResource(R.string.rentals_fees_renter_pays), formatMoney(f.renterTotal))
            f.depositHold?.let { LabelValue(stringResource(R.string.rentals_fees_deposit_hold), formatMoney(it)) }
            LabelValue(stringResource(R.string.rentals_fees_commission, FeeBreakdown.percent(f.commissionBp)), "−" + formatMoney(f.commission))
            f.laborCommission?.let { LabelValue(stringResource(R.string.rentals_fees_labor_commission), formatMoney(it)) }
            f.absorbedRenterFee?.let { LabelValue(stringResource(R.string.rentals_fees_absorbed), "−" + formatMoney(it)) }
            LabelValue(stringResource(R.string.rentals_fees_net_payout), formatMoney(f.netPayout), emphasise = true)
            if (!f.connect) {
                Text(
                    stringResource(R.string.rentals_fees_keys_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
