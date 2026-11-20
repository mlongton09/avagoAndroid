package com.avago.core.data.v2.repo

import com.avago.core.data.domain.PmFilter
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmOccurrenceState
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmPlanDraft
import com.avago.core.data.domain.PmRepository
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerKind
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.microsOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import com.avago.core.data.v2.commands.MaintCommands
import com.avago.core.data.v2.commands.PmCommands
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preventive maintenance: plans and their triggers (`maint.pm_plans.*`, `maint.pm_triggers.*`) and the
 * occurrences worked from the device (complete / skip / defer).
 */
@Singleton
class V2PmRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : PmRepository {

    override fun observeOccurrences(filter: PmFilter): Flow<List<PmOccurrence>> =
        store.observe(TABLES, emptyList()) { conn -> occurrences(conn, filter) }

    override fun observeOccurrence(id: String): Flow<PmOccurrence?> = store.observe(TABLES, null) { conn -> occurrence(conn, id) }

    override fun observePlans(assetId: String?): Flow<List<PmPlan>> =
        store.observe(TABLES, emptyList()) { conn -> plans(conn).filter { assetId == null || it.assetId == assetId } }

    override fun observePlan(id: String): Flow<PmPlan?> = store.observe(TABLES, null) { conn -> plans(conn).firstOrNull { it.id == id } }

    override suspend fun complete(occurrenceId: String, completedAt: Long?, meterValue: BigDecimal?) {
        val stored = open(occurrenceId)
        commands.submit(MaintCommands.completeOccurrence(stored, completedAt, meterValue, System.currentTimeMillis()))
    }

    override suspend fun skip(occurrenceId: String, reason: String) {
        commands.submit(MaintCommands.skipOccurrence(open(occurrenceId), reason, System.currentTimeMillis()))
    }

    override suspend fun defer(occurrenceId: String, dueAt: Long) {
        commands.submit(MaintCommands.deferOccurrence(open(occurrenceId), dueAt, System.currentTimeMillis()))
    }

    override suspend fun createPlan(draft: PmPlanDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(
            PmCommands.createPlan(id, draft, { V2Uuid.v7(System.currentTimeMillis()) }, ZoneId.systemDefault().id, now, commands.currentUserId()),
        )
        return id
    }

    override suspend fun updatePlan(id: String, draft: PmPlanDraft) {
        val stored = plan(id)
        PmCommands.updatePlan(stored, draft, { V2Uuid.v7(System.currentTimeMillis()) }, System.currentTimeMillis(), commands.currentUserId())
            .forEach { commands.submit(it) }
    }

    override suspend fun deletePlan(id: String) {
        commands.submit(PmCommands.deletePlan(plan(id)))
    }

    private suspend fun plan(id: String): PmPlan =
        store.read(null) { conn -> plans(conn).firstOrNull { it.id == id } } ?: throw RecordNotFoundException("pm plan", id)

    private suspend fun open(id: String): PmOccurrence {
        val stored = store.read(null) { conn -> occurrence(conn, id) } ?: throw RecordNotFoundException("pm occurrence", id)
        check(stored.state == PmOccurrenceState.OPEN) { "pm occurrence $id is ${stored.state.wire}" }
        return stored
    }

    internal companion object {
        val TABLES = setOf("pm_plans", "pm_triggers", "pm_occurrences", "assets")

        fun occurrences(conn: V2Connection, filter: PmFilter): List<PmOccurrence> {
            val plans = plans(conn).associateBy { it.id }
            val assets = V2MaintRows.assetNames(conn)
            return V2OverlayMerge.query(conn, "pm_occurrences")
                .mapNotNull { r -> plans[r.str("pm_plan_id")]?.let { occurrence(r, it, assets) } }
                .filter { o ->
                    (filter.states.isEmpty() || o.state in filter.states) &&
                        (filter.assetId == null || o.assetId == filter.assetId) &&
                        (filter.dueFrom == null || (o.dueAt != null && o.dueAt >= filter.dueFrom)) &&
                        (filter.dueTo == null || (o.dueAt != null && o.dueAt < filter.dueTo))
                }
                .sortedWith(compareBy<PmOccurrence, Long?>(nullsLast()) { it.dueAt }.thenBy { it.planTitle })
        }

        fun occurrence(conn: V2Connection, id: String): PmOccurrence? {
            val r = V2OverlayMerge.query(conn, "pm_occurrences", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull() ?: return null
            val plan = plans(conn).firstOrNull { it.id == r.str("pm_plan_id") } ?: return null
            return occurrence(r, plan, V2MaintRows.assetNames(conn))
        }

        fun plans(conn: V2Connection): List<PmPlan> {
            val assets = V2MaintRows.assetNames(conn)
            val triggers = V2OverlayMerge.query(conn, "pm_triggers").groupBy { it.str("pm_plan_id") }
            return V2OverlayMerge.query(conn, "pm_plans").map { r ->
                val id = r.str("id")
                val assetId = r.strOrNull("asset_id")
                PmPlan(
                    id = id,
                    title = r.str("title"),
                    description = r.strOrNull("description"),
                    assetId = assetId,
                    assetName = assetId?.let(assets::get),
                    templateId = r.strOrNull("work_order_template_id"),
                    active = r.bool("active"),
                    endType = r.strOrNull("end_type") ?: "never",
                    endCount = r.intOrNull("end_count"),
                    endOn = r.strOrNull("end_on")?.let(LocalDate::parse),
                    timezone = r.strOrNull("timezone"),
                    cycleNo = r.intOrNull("cycle_no") ?: 0,
                    lastCompletedAt = r.longOrNull("last_completed_at"),
                    triggers = triggers[id].orEmpty().sortedBy { it.intOrNull("priority") ?: 0 }.map(::trigger),
                    version = r.longOrNull("version") ?: 0L,
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, PmPlan::title))
        }

        private fun trigger(r: V2Row) = PmTrigger(
            id = r.str("id"),
            kind = PmTriggerKind.fromWire(r.strOrNull("kind")),
            rrule = r.strOrNull("rrule"),
            intervalValue = r.intOrNull("interval_value"),
            intervalUnit = r.strOrNull("interval_unit"),
            meterId = r.strOrNull("meter_id"),
            meterInterval = r.microsOrNull("meter_interval_micros"),
            nextDueAt = r.longOrNull("next_due_at"),
            nextDueValue = r.microsOrNull("next_due_value_micros"),
            priority = r.intOrNull("priority") ?: 0,
            assetId = r.strOrNull("asset_id"),
            meterCondition = r.strOrNull("meter_condition"),
            thresholdValue = r.microsOrNull("threshold_value_micros"),
            generateOnce = r.bool("generate_once"),
            version = r.longOrNull("version") ?: 0L,
        )

        private fun occurrence(r: V2Row, plan: PmPlan, assets: Map<String, String>): PmOccurrence {
            val assetId = plan.assetId
            return PmOccurrence(
                id = r.str("id"),
                planId = plan.id,
                planTitle = plan.title,
                assetId = assetId,
                assetName = assetId?.let(assets::get),
                cycleNo = r.intOrNull("cycle_no") ?: 0,
                state = PmOccurrenceState.fromWire(r.strOrNull("state")),
                dueAt = r.longOrNull("due_at"),
                dueValue = r.microsOrNull("due_value_micros"),
                workOrderId = r.strOrNull("work_order_id"),
                openedAt = r.longOrNull("opened_at") ?: 0L,
                closedAt = r.longOrNull("closed_at"),
                version = r.longOrNull("version") ?: 0L,
            )
        }
    }
}
