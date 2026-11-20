package com.avago.core.data.v2.commands

import com.avago.core.data.domain.PmEndType
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmPlanDraft
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerDraft
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import java.math.BigDecimal
import java.time.Instant
import com.avago.core.data.domain.PmTriggerKind as DomainTriggerKind

/**
 * Builders for the PM plan catalog commands (`maint.pm_plans.*`, `maint.pm_triggers.*`). A new plan
 * carries its triggers; an edit is one plan update plus one command per added, changed or removed
 * trigger. Each command carries its optimistic overlay.
 */
object PmCommands {

    fun createPlan(
        id: String,
        draft: PmPlanDraft,
        newId: () -> String,
        timezone: String?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val title = requireNotNull(draft.title.clean()) { "a PM plan needs a title" }
        require(draft.triggers.isNotEmpty()) { "a PM plan needs at least one trigger" }
        val end = PlanEnd.of(draft)
        val triggers = draft.triggers.mapIndexed { i, t -> NormalizedTrigger.of(t, draft.assetId, i) to newId() }
        val payload = MaintPmPlansCreate(
            id = id,
            title = title,
            description = draft.description.clean(),
            assetId = draft.assetId,
            workOrderTemplateId = draft.templateId,
            timezone = timezone,
            active = draft.active,
            endType = end.type,
            endCount = end.count,
            endOn = end.on,
            lastDoneAt = draft.lastDoneAt?.let(Instant::ofEpochMilli),
            triggers = triggers.map { (t, tid) -> t.toInput(tid) },
        )
        val plan = V2RowOp.upsert(
            "pm_plans", id,
            mapOf(
                "title" to title,
                "description" to payload.description,
                "asset_id" to draft.assetId,
                "work_order_template_id" to draft.templateId,
                "timezone" to timezone,
                "active" to draft.active,
                "end_type" to end.type,
                "end_count" to end.count,
                "end_on" to end.on?.toString(),
                "cycle_no" to 0L,
                "last_completed_at" to draft.lastDoneAt,
                "ai_suggested" to false,
                "attributes" to "{}",
            ) + created(nowMillis, userId),
        )
        val rows = triggers.map { (t, tid) -> V2RowOp.upsert("pm_triggers", tid, t.row(id) + created(nowMillis, userId)) }
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(plan) + rows))
    }

    /**
     * The commands that turn [stored] into [draft]: the plan update (when the plan changed), then a
     * delete for each removed trigger, an update for each changed one and a create for each new one
     * (a trigger whose kind changed is replaced). Empty when nothing changed.
     */
    fun updatePlan(stored: PmPlan, draft: PmPlanDraft, newId: () -> String, nowMillis: Long, userId: String?): List<V2NewCommand> {
        require(draft.triggers.isNotEmpty()) { "a PM plan needs at least one trigger" }
        val commands = mutableListOf<V2NewCommand>()
        planUpdate(stored, draft, nowMillis)?.let(commands::add)
        val storedById = stored.triggers.associateBy { it.id }
        val normalized = draft.triggers.mapIndexed { i, t -> t to NormalizedTrigger.of(t, stored.assetId, i) }
        val kept = normalized.filter { (t, n) -> t.id != null && storedById[t.id]?.kind == n.kind }
        val keptIds = kept.mapNotNull { it.first.id }.toSet()
        stored.triggers.filter { it.id !in keptIds }.forEach { commands += deleteTrigger(it) }
        kept.forEach { (t, n) -> triggerUpdate(storedById.getValue(requireNotNull(t.id)), n, nowMillis)?.let(commands::add) }
        normalized.filter { (t, _) -> t.id !in keptIds }.forEach { (_, n) ->
            val tid = newId()
            commands += MaintPmTriggersCreate(
                id = tid, pmPlanId = stored.id, kind = n.wireKind, priority = n.priority, rrule = n.rrule,
                intervalValue = n.intervalValue, intervalUnit = n.intervalUnit, assetId = n.assetId, meterId = n.meterId,
                meterCondition = n.meterCondition, meterInterval = n.meterInterval, thresholdValue = n.thresholdValue,
                generateOnce = n.generateOnce,
            ).toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("pm_triggers", tid, n.row(stored.id) + created(nowMillis, userId))))
        }
        return commands
    }

    fun deletePlan(stored: PmPlan): V2NewCommand =
        MaintPmPlansDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.delete("pm_plans", stored.id)) + stored.triggers.map { V2RowOp.delete("pm_triggers", it.id) }),
        )

    private fun planUpdate(stored: PmPlan, draft: PmPlanDraft, nowMillis: Long): V2NewCommand? {
        require(draft.assetId == stored.assetId) { "the asset of a PM plan cannot change" }
        val end = PlanEnd.of(draft)
        val d = V2UpdateDiff(MaintPmPlansUpdate.Clear.entries)
        val title = d.text("title", stored.title, draft.title)
        val description = d.text("description", stored.description, draft.description)
        val template = d.text("work_order_template_id", stored.templateId, draft.templateId)
        val active = d.field("active", stored.active, draft.active)
        val endType = d.field("end_type", stored.endType, end.type)
        val endCount = d.field("end_count", stored.endCount, end.count)
        val endOn = d.field("end_on", stored.endOn, end.on, toColumn = { it.toString() })
        val lastDone = draft.lastDoneAt?.let { d.field("last_done_at", stored.lastCompletedAt, it, column = "last_completed_at") }
        d.check()
        if (d.isEmpty) return null
        return MaintPmPlansUpdate(
            id = stored.id, title = title, description = description, workOrderTemplateId = template, active = active,
            endType = endType, endCount = endCount, endOn = endOn, lastDoneAt = lastDone?.let(Instant::ofEpochMilli),
            clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("pm_plans", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    private fun triggerUpdate(stored: PmTrigger, n: NormalizedTrigger, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(MaintPmTriggersUpdate.Clear.entries)
        val priority = d.field("priority", stored.priority, n.priority)
        val rrule = d.text("rrule", stored.rrule, n.rrule)
        val intervalValue = d.field("interval_value", stored.intervalValue, n.intervalValue)
        val intervalUnit = d.text("interval_unit", stored.intervalUnit, n.intervalUnit)
        val meterId = d.text("meter_id", stored.meterId, n.meterId)
        val condition = d.text("meter_condition", stored.meterCondition, n.meterCondition)
        val meterInterval = d.decimal("meter_interval", stored.meterInterval, n.meterInterval, column = "meter_interval_micros", toColumn = V2Micros::fromDecimal)
        val threshold = d.decimal("threshold_value", stored.thresholdValue, n.thresholdValue, column = "threshold_value_micros", toColumn = V2Micros::fromDecimal)
        val once = d.field("generate_once", stored.generateOnce, n.generateOnce)
        d.check()
        if (d.isEmpty) return null
        return MaintPmTriggersUpdate(
            id = stored.id, priority = priority, rrule = rrule, intervalValue = intervalValue, intervalUnit = intervalUnit,
            meterId = meterId, meterCondition = condition, meterInterval = meterInterval, thresholdValue = threshold,
            generateOnce = once, clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("pm_triggers", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    private fun deleteTrigger(stored: PmTrigger): V2NewCommand =
        MaintPmTriggersDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("pm_triggers", stored.id)),
        )

    /** The end of the series with the fields the end type does not use dropped. */
    private data class PlanEnd(val type: String, val count: Int?, val on: java.time.LocalDate?) {
        companion object {
            fun of(draft: PmPlanDraft): PlanEnd = when (draft.endType) {
                PmEndType.NEVER -> PlanEnd(PmEndType.NEVER, null, null)
                PmEndType.COUNT -> {
                    val count = requireNotNull(draft.endCount?.takeIf { it > 0 }) { "a counted series needs a positive count" }
                    PlanEnd(PmEndType.COUNT, count, null)
                }
                PmEndType.DATE -> PlanEnd(PmEndType.DATE, null, requireNotNull(draft.endOn) { "a dated series needs an end date" })
                else -> throw IllegalArgumentException("unknown end type ${draft.endType}")
            }
        }
    }

    /** A validated trigger with only the fields its kind uses. */
    private data class NormalizedTrigger(
        val kind: DomainTriggerKind,
        val priority: Int,
        val rrule: String? = null,
        val intervalValue: Int? = null,
        val intervalUnit: String? = null,
        val assetId: String? = null,
        val meterId: String? = null,
        val meterCondition: String? = null,
        val meterInterval: BigDecimal? = null,
        val thresholdValue: BigDecimal? = null,
        val generateOnce: Boolean = false,
    ) {
        val wireKind: PmTriggerKind get() = PmTriggerKind.entries.first { it.wire == kind.wire }

        fun toInput(id: String) = PmTriggerIn(
            id = id, kind = wireKind, priority = priority, rrule = rrule, intervalValue = intervalValue,
            intervalUnit = intervalUnit, assetId = assetId, meterId = meterId, meterCondition = meterCondition,
            meterInterval = meterInterval, thresholdValue = thresholdValue, generateOnce = generateOnce,
        )

        fun row(planId: String): Map<String, Any?> = mapOf(
            "pm_plan_id" to planId,
            "kind" to kind.wire,
            "priority" to priority.toLong(),
            "rrule" to rrule,
            "interval_value" to intervalValue?.toLong(),
            "interval_unit" to intervalUnit,
            "asset_id" to assetId,
            "meter_id" to meterId,
            "meter_condition" to meterCondition,
            "meter_interval_micros" to meterInterval?.let(V2Micros::fromDecimal),
            "threshold_value_micros" to thresholdValue?.let(V2Micros::fromDecimal),
            "generate_once" to generateOnce,
        )

        companion object {
            fun of(t: PmTriggerDraft, planAssetId: String?, priority: Int): NormalizedTrigger = when (t.kind) {
                DomainTriggerKind.CALENDAR -> NormalizedTrigger(
                    kind = t.kind, priority = priority,
                    rrule = requireNotNull(t.rrule.clean()) { "a calendar trigger needs a recurrence rule" },
                    generateOnce = t.generateOnce,
                )
                DomainTriggerKind.INTERVAL -> NormalizedTrigger(
                    kind = t.kind, priority = priority,
                    intervalValue = requireNotNull(t.intervalValue?.takeIf { it > 0 }) { "an interval trigger needs a positive interval" },
                    intervalUnit = requireNotNull(t.intervalUnit) { "an interval trigger needs a unit" }.wire,
                    generateOnce = t.generateOnce,
                )
                DomainTriggerKind.METER -> {
                    val assetId = requireNotNull(planAssetId) { "a meter trigger needs a plan with an asset" }
                    val meterId = requireNotNull(t.meterId) { "a meter trigger needs a meter" }
                    val every = t.meterInterval?.takeIf { it.signum() > 0 }
                    if (every != null) {
                        NormalizedTrigger(
                            kind = t.kind, priority = priority, assetId = assetId, meterId = meterId,
                            meterInterval = every, generateOnce = t.generateOnce,
                        )
                    } else {
                        NormalizedTrigger(
                            kind = t.kind, priority = priority, assetId = assetId, meterId = meterId,
                            meterCondition = requireNotNull(t.meterCondition) { "a meter threshold needs a condition" }.wire,
                            thresholdValue = requireNotNull(t.thresholdValue) { "a meter trigger needs an interval or a threshold" },
                            generateOnce = t.generateOnce,
                        )
                    }
                }
            }
        }
    }
}
