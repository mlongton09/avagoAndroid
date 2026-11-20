package com.avago.core.data.v2.repo

import android.net.Uri
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityFilter
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.DefectState
import com.avago.core.data.domain.GlAccount
import com.avago.core.data.domain.InspectionDefect
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.MaintCommands
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2ActivityRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : ActivityRepository {

    override fun observeActivities(filter: ActivityFilter): Flow<List<Activity>> =
        store.observe(ACTIVITY_TABLES, emptyList()) { conn -> activities(conn, filter) }

    override fun observeActivity(id: String): Flow<Activity?> = store.observe(ACTIVITY_TABLES, null) { conn -> byId(conn, id) }

    override suspend fun activity(id: String): Activity? = store.read(null) { conn -> byId(conn, id) }

    override fun observeCostLines(activityId: String): Flow<List<CostLine>> =
        store.observe(setOf("activity_cost_lines"), emptyList()) { conn ->
            V2MaintRows.costLines(conn, "activity_id = ?", listOf(activityId)) { it["activity_id"] == activityId }
        }

    override fun observeAllCostLines(from: Long?, to: Long?): Flow<List<CostLine>> =
        store.observe(setOf("activity_cost_lines", "activity_records"), emptyList()) { conn ->
            if (from == null && to == null) {
                V2MaintRows.costLines(conn, null, emptyList()) { true }
            } else {
                val inWindow = activities(conn, ActivityFilter(from = from, to = to)).map { it.id }.toSet()
                V2MaintRows.costLines(conn, null, emptyList()) { true }.filter { it.activityId in inWindow }
            }
        }

    override fun observeDefects(assetId: String?, openOnly: Boolean): Flow<List<InspectionDefect>> =
        store.observe(setOf("inspection_defects"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "inspection_defects", assetId?.let { "asset_id = ?" }, listOfNotNull(assetId)) {
                assetId == null || it["asset_id"] == assetId
            }
                .map(V2MaintRows::defect)
                .filter { !openOnly || it.status.isOpen }
                .sortedByDescending { it.reportedAt }
        }

    override suspend fun create(draft: ActivityDraft, lines: List<CostLineDraft>, files: List<LocalFileRef>): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val userId = commands.currentUserId()
        val staged = files.map { stager.stage(Uri.parse(it.uri), it.fallbackMediaType) }
        val workers = performerWorkers(lines)
        val createId = commands.submit(MaintCommands.createActivity(id, draft, now, userId))
        lines.forEachIndexed { index, line ->
            commands.submit(
                MaintCommands.createCostLine(
                    V2Uuid.v7(now), id, null, line, index, now, userId,
                    performerWorkerId = line.performerUserId?.let(workers::get), dependsOn = listOf(createId),
                ),
            )
        }
        staged.forEachIndexed { index, file ->
            commands.submit(
                MaintCommands.attach(V2Uuid.v7(now), AttachmentTarget.ToActivity(id), file, index, now, userId, dependsOn = listOf(createId)),
            )
        }
        return id
    }

    override suspend fun update(id: String, draft: ActivityDraft) {
        val stored = activity(id) ?: throw RecordNotFoundException("activity", id)
        val lineIds = if (stored.workOrderId != draft.workOrderId) {
            store.read(emptyList()) { conn -> V2MaintRows.costLines(conn, "activity_id = ?", listOf(id)) { it["activity_id"] == id }.map { it.id } }
        } else {
            emptyList()
        }
        MaintCommands.updateActivity(stored, draft, System.currentTimeMillis(), lineIds)?.let { commands.submit(it) }
    }

    override suspend fun delete(id: String) {
        val stored = activity(id) ?: throw RecordNotFoundException("activity", id)
        commands.submit(MaintCommands.deleteActivity(stored))
    }

    override suspend fun addCostLine(activityId: String, draft: CostLineDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val next = store.read(0) { conn ->
            V2MaintRows.costLines(conn, "activity_id = ?", listOf(activityId)) { it["activity_id"] == activityId }
                .maxOfOrNull { it.sortOrder + 1 } ?: 0
        }
        val worker = performerWorkers(listOf(draft))[draft.performerUserId]
        commands.submit(MaintCommands.createCostLine(id, activityId, null, draft, next, now, commands.currentUserId(), worker))
        return id
    }

    override suspend fun updateCostLine(lineId: String, draft: CostLineDraft) {
        val (stored, performerUser, worker) = costLineContext(store, lineId, draft.performerUserId)
        MaintCommands.updateCostLine(stored, draft, System.currentTimeMillis(), performerUser, worker)?.let { commands.submit(it) }
    }

    override suspend fun deleteCostLine(lineId: String) {
        val stored = store.read(null) { conn -> costLine(conn, lineId) } ?: throw RecordNotFoundException("cost line", lineId)
        commands.submit(MaintCommands.deleteCostLine(stored))
    }

    override suspend fun updateDefect(id: String, status: DefectState, resolutionNote: String?) {
        val stored = store.read(null) { conn ->
            V2OverlayMerge.query(conn, "inspection_defects", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.let(V2MaintRows::defect)
        } ?: throw RecordNotFoundException("defect", id)
        MaintCommands.updateDefect(stored, status, resolutionNote, System.currentTimeMillis(), commands.currentUserId())
            ?.let { commands.submit(it) }
    }

    override fun observeGlAccounts(): Flow<List<GlAccount>> = store.observe(setOf("gl_accounts"), emptyList()) { conn ->
        V2OverlayMerge.query(conn, "gl_accounts")
            .filter { it.bool("is_active") }
            .map { GlAccount(id = it.str("id"), code = it.str("code"), name = it.str("name"), accountType = it.strOrNull("account_type")) }
            .sortedBy { it.code }
    }

    private suspend fun performerWorkers(lines: List<CostLineDraft>): Map<String, String> {
        val users = lines.mapNotNull { it.performerUserId }.toSet()
        if (users.isEmpty()) return emptyMap()
        return store.read(emptyMap()) { conn ->
            V2MaintRows.workers(conn).filter { it.userId in users }.associate { it.userId!! to it.id }
        }
    }

    internal companion object {
        val ACTIVITY_TABLES = setOf("activity_records", "activity_cost_lines", "meter_readings", "assets")

        fun activities(conn: V2Connection, filter: ActivityFilter): List<Activity> {
            val clauses = mutableListOf<String>()
            val args = mutableListOf<Any?>()
            filter.assetId?.let { clauses += "asset_id = ?"; args += it }
            filter.workOrderId?.let { clauses += "work_order_id = ?"; args += it }
            filter.from?.let { clauses += "occurred_at >= ?"; args += it }
            filter.to?.let { clauses += "occurred_at < ?"; args += it }
            val q = filter.query.trim().lowercase()
            return V2MaintRows.activities(conn, clauses.joinToString(" AND ").ifEmpty { null }, args) { r ->
                (filter.assetId == null || r["asset_id"] == filter.assetId) &&
                    (filter.workOrderId == null || r["work_order_id"] == filter.workOrderId) &&
                    (filter.from == null || ((r["occurred_at"] as? Number)?.toLong() ?: 0L) >= filter.from) &&
                    (filter.to == null || ((r["occurred_at"] as? Number)?.toLong() ?: 0L) < filter.to)
            }.filter { a ->
                (filter.types.isEmpty() || a.type in filter.types) &&
                    (
                        q.isEmpty() || listOfNotNull(a.title, a.categoryLabel, a.notes, a.assetName, a.performedByName, a.providerName)
                            .any { it.lowercase().contains(q) }
                        )
            }
        }

        fun byId(conn: V2Connection, id: String): Activity? =
            V2MaintRows.activities(conn, "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()

        fun costLine(conn: V2Connection, id: String): CostLine? =
            V2MaintRows.costLines(conn, "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()

        /** The stored line, the user behind its performer and the worker of [newPerformerUserId]. */
        suspend fun costLineContext(store: V2StoreAccess, lineId: String, newPerformerUserId: String?): Triple<CostLine, String?, String?> =
            store.read(null) { conn ->
                val line = costLine(conn, lineId) ?: return@read null
                val workers = V2MaintRows.workers(conn)
                Triple(
                    line,
                    workers.firstOrNull { it.id == line.performerWorkerId }?.userId,
                    newPerformerUserId?.let { uid -> workers.firstOrNull { it.userId == uid }?.id },
                )
            } ?: throw RecordNotFoundException("cost line", lineId)
    }
}

/** Ids of the work orders' assets: `work_order_assets` rows (overlay applied), primary first. */
internal fun workOrderAssetIds(conn: V2Connection, workOrderId: String? = null): Map<String, List<String>> =
    V2OverlayMerge.query(conn, "work_order_assets", workOrderId?.let { "work_order_id = ?" }, listOfNotNull(workOrderId)) {
        workOrderId == null || it["work_order_id"] == workOrderId
    }
        .groupBy { it.str("work_order_id") }
        .mapValues { (_, rows) ->
            rows.sortedWith(compareByDescending<Map<String, Any?>> { (it["is_primary"] as? Number)?.toLong() == 1L }
                .thenBy { (it["sort_order"] as? Number)?.toLong() ?: 0L })
                .map { it.str("asset_id") }
        }

