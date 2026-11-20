package com.avago.feature.schedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.avago.core.data.domain.PmIntervalUnit
import com.avago.core.data.domain.PmMeterCondition
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerKind
import com.avago.feature.schedule.R
import com.avago.feature.schedule.util.PmSchedule
import com.avago.feature.schedule.util.RruleHelper

/** One-line description of a PM trigger: its rule, interval, meter interval or meter threshold. */
@Composable
fun pmTriggerText(t: PmTrigger): String = when (t.kind) {
    PmTriggerKind.CALENDAR -> RruleHelper.describe(t.rrule)
    PmTriggerKind.INTERVAL -> stringResource(
        R.string.schedule_trigger_interval,
        t.intervalValue ?: 0,
        PmIntervalUnit.fromWire(t.intervalUnit)?.let { intervalUnitLabel(it) } ?: t.intervalUnit.orEmpty(),
    )
    PmTriggerKind.METER -> {
        val every = t.meterInterval
        val threshold = t.thresholdValue
        val condition = PmMeterCondition.fromWire(t.meterCondition)
        when {
            every != null -> stringResource(R.string.schedule_trigger_meter, PmSchedule.formatValue(every))
            threshold != null && condition != null ->
                stringResource(R.string.schedule_trigger_meter_threshold, meterConditionLabel(condition), PmSchedule.formatValue(threshold))
            else -> stringResource(R.string.schedule_trigger_kind_meter)
        }
    }
}

@Composable
fun intervalUnitLabel(unit: PmIntervalUnit): String = stringResource(
    when (unit) {
        PmIntervalUnit.DAY -> R.string.schedule_unit_days
        PmIntervalUnit.WEEK -> R.string.schedule_unit_weeks
        PmIntervalUnit.MONTH -> R.string.schedule_unit_months
        PmIntervalUnit.YEAR -> R.string.schedule_unit_years
    },
)

@Composable
fun meterConditionLabel(c: PmMeterCondition): String = stringResource(
    when (c) {
        PmMeterCondition.ABOVE -> R.string.schedule_condition_above
        PmMeterCondition.AT_LEAST -> R.string.schedule_condition_at_least
        PmMeterCondition.BELOW -> R.string.schedule_condition_below
        PmMeterCondition.AT_MOST -> R.string.schedule_condition_at_most
        PmMeterCondition.EQUALS -> R.string.schedule_condition_equals
        PmMeterCondition.INCREMENTED_BY -> R.string.schedule_condition_incremented_by
        PmMeterCondition.DECREMENTED_BY -> R.string.schedule_condition_decremented_by
    },
)

@Composable
fun triggerKindLabel(kind: PmTriggerKind): String = stringResource(
    when (kind) {
        PmTriggerKind.CALENDAR -> R.string.schedule_trigger_kind_calendar
        PmTriggerKind.INTERVAL -> R.string.schedule_trigger_kind_interval
        PmTriggerKind.METER -> R.string.schedule_trigger_kind_meter
    },
)
