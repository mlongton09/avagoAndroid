package com.avago.feature.schedule.util

import com.avago.core.data.domain.PmEndType
import com.avago.core.data.domain.PmIntervalUnit
import com.avago.core.data.domain.PmMeterCondition
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmPlanDraft
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerDraft
import com.avago.core.data.domain.PmTriggerKind
import java.math.BigDecimal
import java.time.LocalDate

/** RRULE frequencies the plan editor can set; other rule parts of a stored rule are kept. */
enum class CalendarFreq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** How a meter trigger falls due: every N units, or when a reading meets a threshold. */
enum class MeterMode { EVERY, THRESHOLD }

/**
 * One trigger in the plan editor. [key] identifies the row in the list; [id] is the stored trigger
 * (null for a new one). [every] is the calendar or interval count, [freq] null when the stored rule
 * uses a frequency the editor does not offer (the rule is then kept as is).
 */
data class TriggerForm(
    val key: Long,
    val id: String? = null,
    val kind: PmTriggerKind = PmTriggerKind.CALENDAR,
    val storedRrule: String? = null,
    val freq: CalendarFreq? = CalendarFreq.MONTHLY,
    val every: String = "1",
    val unit: PmIntervalUnit = PmIntervalUnit.MONTH,
    val meterId: String? = null,
    val meterMode: MeterMode = MeterMode.EVERY,
    val meterInterval: String = "",
    val condition: PmMeterCondition = PmMeterCondition.AT_LEAST,
    val threshold: String = "",
    val generateOnce: Boolean = false,
) {
    companion object {
        fun of(key: Long, t: PmTrigger): TriggerForm {
            val rule = RruleHelper.parse(t.rrule)
            val threshold = t.meterInterval == null && t.thresholdValue != null
            return TriggerForm(
                key = key,
                id = t.id,
                kind = t.kind,
                storedRrule = t.rrule,
                freq = if (t.kind == PmTriggerKind.CALENDAR) rule.freq else CalendarFreq.MONTHLY,
                every = when (t.kind) {
                    PmTriggerKind.CALENDAR -> rule.interval.toString()
                    PmTriggerKind.INTERVAL -> t.intervalValue?.toString().orEmpty()
                    PmTriggerKind.METER -> "1"
                },
                unit = PmIntervalUnit.fromWire(t.intervalUnit) ?: PmIntervalUnit.MONTH,
                meterId = t.meterId,
                meterMode = if (threshold) MeterMode.THRESHOLD else MeterMode.EVERY,
                meterInterval = t.meterInterval?.stripTrailingZeros()?.toPlainString().orEmpty(),
                condition = PmMeterCondition.fromWire(t.meterCondition) ?: PmMeterCondition.AT_LEAST,
                threshold = t.thresholdValue?.stripTrailingZeros()?.toPlainString().orEmpty(),
                generateOnce = t.generateOnce,
            )
        }
    }
}

/** The PM plan editor state. [assetId] is fixed once the plan exists. */
data class PlanForm(
    val title: String = "",
    val description: String = "",
    val assetId: String? = null,
    val assetName: String? = null,
    val templateId: String? = null,
    val active: Boolean = true,
    val endType: String = PmEndType.NEVER,
    val endCount: String = "",
    val endOn: LocalDate? = null,
    val lastDoneAt: Long? = null,
    val triggers: List<TriggerForm> = listOf(TriggerForm(key = 0)),
) {
    val nextKey: Long get() = (triggers.maxOfOrNull { it.key } ?: -1) + 1

    companion object {
        fun of(p: PmPlan) = PlanForm(
            title = p.title,
            description = p.description.orEmpty(),
            assetId = p.assetId,
            assetName = p.assetName,
            templateId = p.templateId,
            active = p.active,
            endType = p.endType,
            endCount = p.endCount?.toString().orEmpty(),
            endOn = p.endOn,
            lastDoneAt = p.lastCompletedAt,
            triggers = p.triggers.mapIndexed { i, t -> TriggerForm.of(i.toLong(), t) },
        )
    }
}

enum class PlanFormError {
    TITLE, NO_TRIGGERS, END_COUNT, END_DATE, TRIGGER_EVERY, METER_NEEDS_ASSET, METER_MISSING, METER_VALUE
}

sealed interface PlanFormResult {
    data class Valid(val draft: PmPlanDraft) : PlanFormResult

    /** [triggerKey] is the offending trigger row, when the error is about one. */
    data class Invalid(val error: PlanFormError, val triggerKey: Long? = null) : PlanFormResult
}

/** Validates the editor state and builds the draft the repository saves. */
fun PlanForm.toDraft(): PlanFormResult {
    if (title.isBlank()) return PlanFormResult.Invalid(PlanFormError.TITLE)
    if (triggers.isEmpty()) return PlanFormResult.Invalid(PlanFormError.NO_TRIGGERS)
    val count = endCount.trim().toIntOrNull()
    when (endType) {
        PmEndType.COUNT -> if (count == null || count <= 0) return PlanFormResult.Invalid(PlanFormError.END_COUNT)
        PmEndType.DATE -> if (endOn == null) return PlanFormResult.Invalid(PlanFormError.END_DATE)
    }
    val drafts = triggers.map { t ->
        when (val r = t.toDraft(assetId)) {
            is TriggerResult.Ok -> r.draft
            is TriggerResult.Bad -> return PlanFormResult.Invalid(r.error, t.key)
        }
    }
    return PlanFormResult.Valid(
        PmPlanDraft(
            title = title.trim(),
            description = description.trim().ifEmpty { null },
            assetId = assetId,
            templateId = templateId,
            active = active,
            endType = endType,
            endCount = if (endType == PmEndType.COUNT) count else null,
            endOn = if (endType == PmEndType.DATE) endOn else null,
            lastDoneAt = lastDoneAt,
            triggers = drafts,
        ),
    )
}

private sealed interface TriggerResult {
    data class Ok(val draft: PmTriggerDraft) : TriggerResult

    data class Bad(val error: PlanFormError) : TriggerResult
}

private fun TriggerForm.toDraft(planAssetId: String?): TriggerResult {
    val base = PmTriggerDraft(id = id, kind = kind, generateOnce = generateOnce)
    return when (kind) {
        PmTriggerKind.CALENDAR -> {
            val f = freq
            if (f == null) {
                // A rule the editor cannot express is kept as stored.
                TriggerResult.Ok(base.copy(rrule = storedRrule))
            } else {
                val n = every.trim().toIntOrNull()?.takeIf { it > 0 } ?: return TriggerResult.Bad(PlanFormError.TRIGGER_EVERY)
                TriggerResult.Ok(base.copy(rrule = RruleHelper.compose(storedRrule, f, n)))
            }
        }
        PmTriggerKind.INTERVAL -> {
            val n = every.trim().toIntOrNull()?.takeIf { it > 0 } ?: return TriggerResult.Bad(PlanFormError.TRIGGER_EVERY)
            TriggerResult.Ok(base.copy(intervalValue = n, intervalUnit = unit))
        }
        PmTriggerKind.METER -> when {
            planAssetId == null -> TriggerResult.Bad(PlanFormError.METER_NEEDS_ASSET)
            meterId == null -> TriggerResult.Bad(PlanFormError.METER_MISSING)
            meterMode == MeterMode.EVERY -> {
                val v = parseDecimal(meterInterval)?.takeIf { it.signum() > 0 } ?: return TriggerResult.Bad(PlanFormError.METER_VALUE)
                TriggerResult.Ok(base.copy(meterId = meterId, meterInterval = v))
            }
            else -> {
                val v = parseDecimal(threshold) ?: return TriggerResult.Bad(PlanFormError.METER_VALUE)
                TriggerResult.Ok(base.copy(meterId = meterId, meterCondition = condition, thresholdValue = v))
            }
        }
    }
}

internal fun parseDecimal(text: String): BigDecimal? = text.trim().replace(',', '.').toBigDecimalOrNull()
