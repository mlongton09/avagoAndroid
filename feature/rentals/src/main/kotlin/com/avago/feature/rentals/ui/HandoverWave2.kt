package com.avago.feature.rentals.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.CreditCheck
import com.avago.feature.rentals.domain.Esign
import com.avago.feature.rentals.domain.HandoverChecks
import com.avago.feature.rentals.domain.MeterInfo
import java.io.File
import java.math.BigDecimal

// Wave 2 hand-over additions (docs/rentals-api-wave2-esign-scan-meter.md §1/§3 and
// docs/rentals-api-wave2-credit-collections-bank.md checkout gate). Stateless; the
// HandoverViewModel owns the state.

/** Short unit label of a meter (`h`, `km`, `mi`). */
@Composable
internal fun meterUnitLabel(unit: String?): String = when (unit) {
    "hours", "h", null -> stringResource(R.string.rentals_meter_unit_hours)
    else -> unit
}

internal fun formatReading(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

/**
 * Rules of the unit's meter under the reading field: requirement, last reading, validation,
 * the override reason (rental.meter_override) and an optional photo of the meter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeterRules(
    info: MeterInfo?,
    check: MeterInfo.Check?,
    belowPrevious: Boolean,
    canOverride: Boolean,
    reason: String,
    onReason: (String) -> Unit,
    photoPath: String?,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Column(Modifier.padding(top = AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
        if (info?.hasMeter == true) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                StatusPill(
                    stringResource(if (info.required) R.string.rentals_meter_required else R.string.rentals_meter_optional),
                    if (info.required) StatusTone.Warning else StatusTone.Neutral,
                    icon = Icons.Default.Speed,
                )
            }
            info.lastValue?.let { v ->
                Text(
                    stringResource(
                        R.string.rentals_meter_last,
                        "${formatReading(v)} ${meterUnitLabel(info.unit)}",
                        meterSourceLabel(info.lastSource),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (check) {
            MeterInfo.Check.REQUIRED -> InlineIssue(stringResource(R.string.rentals_meter_enter_reading), StatusTone.Danger)
            MeterInfo.Check.INVALID -> InlineIssue(stringResource(R.string.rentals_meter_invalid), StatusTone.Danger)
            MeterInfo.Check.BELOW_CHECKOUT -> {
                InlineIssue(stringResource(R.string.rentals_meter_below_checkout), StatusTone.Danger)
                if (canOverride) {
                    OutlinedTextField(
                        reason, onReason,
                        label = { Text(stringResource(R.string.rentals_override_reason)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CardNoteText(stringResource(R.string.rentals_override_ask_manager))
                }
            }
            else -> if (belowPrevious) InlineIssue(stringResource(R.string.rentals_meter_below_previous), StatusTone.Warning)
        }
        if (photoPath == null) {
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp))
                Text(stringResource(R.string.rentals_meter_photo), Modifier.padding(start = AvagoSpacing.sm))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                if (!LocalInspectionMode.current) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = stringResource(R.string.rentals_meter_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rentals_meter_photo_added), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRemovePhoto, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun meterSourceLabel(source: String?): String = stringResource(
    when (source) {
        "checkout" -> R.string.rentals_meter_source_checkout
        "checkin" -> R.string.rentals_meter_source_checkin
        else -> R.string.rentals_meter_source_manual
    },
)

@Composable
private fun InlineIssue(text: String, tone: StatusTone) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
        Icon(
            if (tone == StatusTone.Danger) Icons.Default.Error else Icons.Default.Warning, null,
            Modifier.size(18.dp),
            tint = com.avago.core.ui.components.toneColors(tone).content,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CardNoteText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Check-out gates on the review step: the agreement signature (sign now / override) and the
 * customer's credit and required-document status (block messages, add a document, override).
 * Hidden sections: nothing is shown for a gate the account does not use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckoutGates(
    esign: Esign.Status?,
    canOverrideSignature: Boolean,
    overrideSignature: Boolean,
    onOverrideSignature: (Boolean) -> Unit,
    onSignNow: (() -> Unit)?,
    credit: CreditCheck?,
    creditUnknown: Boolean,
    canOverrideCredit: Boolean,
    creditReason: String,
    onCreditReason: (String) -> Unit,
    onAddDocument: (() -> Unit)?,
) {
    if (esign != null && (esign.requireBeforeCheckout || esign.requests.isNotEmpty())) {
        val local = esign.requests.any { r -> r.signers.any { it.status == Esign.STATUS_SIGNED_LOCAL } }
        SectionCard(title = stringResource(R.string.rentals_agreement), contentPadding = true) {
            when {
                esign.agreementSigned -> StatusPill(stringResource(R.string.rentals_agreement_signed), StatusTone.Success, icon = Icons.Default.CheckCircle)
                local -> StatusPill(stringResource(R.string.rentals_agreement_signed_syncing), StatusTone.Info, icon = Icons.Default.Schedule)
                else -> StatusPill(
                    stringResource(R.string.rentals_agreement_unsigned),
                    if (esign.requireBeforeCheckout) StatusTone.Warning else StatusTone.Neutral,
                    icon = Icons.Default.Draw,
                )
            }
            if (!esign.agreementSigned && !local) {
                if (esign.requireBeforeCheckout) {
                    Text(
                        stringResource(R.string.rentals_agreement_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AvagoSpacing.sm),
                    )
                }
                onSignNow?.let {
                    FilledTonalButton(onClick = it, modifier = Modifier.padding(top = AvagoSpacing.md).fillMaxWidth().heightIn(min = MinTouch)) {
                        Icon(Icons.Default.Draw, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_sign_agreement_now), Modifier.padding(start = AvagoSpacing.sm))
                    }
                }
                if (esign.requireBeforeCheckout && canOverrideSignature) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouch)
                            .toggleable(overrideSignature, role = Role.Switch, onValueChange = onOverrideSignature),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.rentals_override_signature), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = overrideSignature, onCheckedChange = null)
                    }
                }
            }
        }
    }
    if (credit != null) {
        SectionCard(title = stringResource(R.string.rentals_credit_status), contentPadding = true) {
            if (credit.allowed) {
                StatusPill(stringResource(R.string.rentals_credit_ok), StatusTone.Success, icon = Icons.Default.CheckCircle)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    credit.blocks.forEach { b ->
                        InlineIssue(b.message ?: creditBlockLabel(b.code), StatusTone.Danger)
                    }
                }
            }
            val docs = credit.missingDocuments
            if (docs.isNotEmpty()) {
                CardDivider(Modifier.padding(vertical = AvagoSpacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    docs.forEach { d ->
                        StatusPill(
                            stringResource(
                                if (d.status == "expired") R.string.rentals_doc_expired else R.string.rentals_doc_missing,
                                stringResource(documentKindLabel(d.kind)),
                            ),
                            StatusTone.Danger,
                            icon = Icons.Default.Badge,
                        )
                    }
                }
                onAddDocument?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.padding(top = AvagoSpacing.sm).heightIn(min = MinTouch)) {
                        Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_add_document), Modifier.padding(start = AvagoSpacing.sm))
                    }
                }
            }
            if (!credit.allowed) {
                if (canOverrideCredit && credit.canOverride) {
                    OutlinedTextField(
                        creditReason, onCreditReason,
                        label = { Text(stringResource(R.string.rentals_override_reason)) },
                        supportingText = { Text(stringResource(R.string.rentals_credit_override_hint)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(top = AvagoSpacing.sm),
                    )
                } else {
                    Text(
                        stringResource(R.string.rentals_override_ask_manager),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AvagoSpacing.sm),
                    )
                }
            }
        }
    } else if (creditUnknown) {
        ErrorBanner(
            stringResource(R.string.rentals_credit_unknown),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            tone = StatusTone.Info,
            icon = Icons.Default.Info,
        )
    }
}

/** Fallback text of a credit / documents block code (the server message is preferred). */
@Composable
internal fun creditBlockLabel(code: String): String = stringResource(
    when (code) {
        "customer_blocked" -> R.string.rentals_credit_blocked
        "customer_on_hold" -> R.string.rentals_credit_on_hold
        "customer_cash_only" -> R.string.rentals_credit_cash_only
        "credit_limit_exceeded" -> R.string.rentals_credit_limit_exceeded
        "required_documents_expired" -> R.string.rentals_credit_docs_expired
        else -> R.string.rentals_credit_docs_missing
    },
)

/** Why "Complete check-out / check-in" is disabled. */
@Composable
internal fun blockerText(b: HandoverChecks.Blocker): String = stringResource(
    when (b) {
        HandoverChecks.Blocker.METER_REQUIRED -> R.string.rentals_meter_enter_reading
        HandoverChecks.Blocker.METER_INVALID -> R.string.rentals_meter_invalid
        HandoverChecks.Blocker.METER_NEEDS_REASON -> R.string.rentals_blocker_meter_reason
        HandoverChecks.Blocker.METER_NOT_PERMITTED -> R.string.rentals_meter_below_checkout
        HandoverChecks.Blocker.SIGNATURE -> R.string.rentals_blocker_signature
        HandoverChecks.Blocker.CREDIT_NEEDS_REASON -> R.string.rentals_blocker_credit_reason
        HandoverChecks.Blocker.CREDIT_NOT_PERMITTED -> R.string.rentals_blocker_credit
    },
)

/** Step that fixes [b] (2 = meter, 3 = review). */
internal fun blockerStep(b: HandoverChecks.Blocker): Int = when (b) {
    HandoverChecks.Blocker.METER_REQUIRED, HandoverChecks.Blocker.METER_INVALID,
    HandoverChecks.Blocker.METER_NEEDS_REASON, HandoverChecks.Blocker.METER_NOT_PERMITTED -> 2
    else -> 3
}

@Preview(name = "Wave 2 hand-over – light", showBackground = true, widthDp = 380)
@Preview(name = "Wave 2 hand-over – dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Wave 2 hand-over – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Preview(name = "Wave 2 hand-over – German", showBackground = true, widthDp = 380, locale = "de")
@Composable
private fun HandoverWave2Preview() {
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                SectionCard(title = "Meter", contentPadding = true) {
                    MeterRules(
                        info = MeterInfo("hours", "required", "hours", 24.0, BigDecimal("1250.5"), "checkout"),
                        check = MeterInfo.Check.BELOW_CHECKOUT,
                        belowPrevious = false,
                        canOverride = true,
                        reason = "",
                        onReason = {},
                        photoPath = null,
                        onTakePhoto = {},
                        onRemovePhoto = {},
                    )
                }
                CheckoutGates(
                    esign = Esign.Status("a1", agreementSigned = false, requireBeforeCheckout = true, requests = emptyList()),
                    canOverrideSignature = true,
                    overrideSignature = false,
                    onOverrideSignature = {},
                    onSignNow = {},
                    credit = CreditCheck(
                        allowed = false, canOverride = true, accountStatus = "on_hold", accountStatusReason = null,
                        blocks = listOf(CreditCheck.Block("credit_limit_exceeded", null, "credit")),
                        documents = listOf(CreditCheck.Doc("coi", "expired", "2024-01-31")),
                        availableMinor = 0, afterOrderMinor = -12_000, creditLimitMinor = 500_000,
                    ),
                    creditUnknown = false,
                    canOverrideCredit = true,
                    creditReason = "",
                    onCreditReason = {},
                    onAddDocument = {},
                )
            }
        }
    }
}
