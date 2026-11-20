package com.avago.core.data.v2.repo

import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.long
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull

/** A work-order event worth a local notification for the signed-in user. */
data class WorkOrderAlert(
    /** Stable per event, so the same event is never announced twice. */
    val key: String,
    val kind: Kind,
    val workOrderId: String,
    val workOrderTitle: String,
    /** The comment text for [Kind.COMMENT]; null otherwise. */
    val text: String? = null,
) {
    enum class Kind { ASSIGNED, COMMENT, NEEDS_APPROVAL }
}

/**
 * [alerts] to post; [settled] are keys of earlier alerts whose event is over (a work order left
 * `pending_approval`), so the same event happening again is announced again.
 */
data class WorkOrderAlertSelection(val alerts: List<WorkOrderAlert>, val settled: Set<String>) {
    /**
     * Against [announced] (keys already notified, oldest first): the alerts not yet announced and
     * the next announced list, without settled keys and capped at the newest [cap] entries.
     */
    fun unannounced(announced: List<String>, cap: Int = ANNOUNCED_CAP): Pair<List<WorkOrderAlert>, List<String>> {
        val known = announced.filterNot { it in settled }
        val fresh = alerts.filter { it.key !in known }.distinctBy { it.key }
        return fresh to (known + fresh.map { it.key }).takeLast(cap)
    }

    companion object {
        const val ANNOUNCED_CAP = 500
    }
}

/**
 * Picks the local work-order notifications from the rows a sync run upserted
 * ([com.avago.core.data.v2.V2SyncResult.Synced.upserted]). Reads the synced base tables only, so
 * the user's own queued changes never announce themselves:
 *
 * - **assigned**: a new `work_order_assignments` row (version 1) for the user's worker that is
 *   still awaiting acceptance and was made by someone else;
 * - **comment**: a new `comments` row by someone else on a work order the user requested, is the
 *   assigned worker of, or is on the crew of;
 * - **needs approval**: a work order in `pending_approval` that someone else put there, for users
 *   allowed to approve work orders (once per approval round, see [WorkOrderAlertSelection.settled]).
 */
object V2WorkOrderAlerts {
    private val AWAITING_ACCEPTANCE = setOf("assigned", "pending")

    fun select(conn: V2Connection, upserted: Map<String, Set<String>>, userId: String, canApprove: Boolean): WorkOrderAlertSelection {
        val myWorker = conn.query("SELECT id FROM workers WHERE user_id = ?", listOf(userId)).firstOrNull()?.str("id")
        val workOrders = HashMap<String, V2Row?>()
        fun workOrder(id: String) = workOrders.getOrPut(id) { conn.query("SELECT * FROM work_orders WHERE id = ?", listOf(id)).firstOrNull() }

        val alerts = mutableListOf<WorkOrderAlert>()
        if (myWorker != null) {
            for (a in rows(conn, "work_order_assignments", upserted)) {
                if (a.str("worker_id") != myWorker || a.long("version") != 1L) continue
                if (a.str("acceptance_status") !in AWAITING_ACCEPTANCE || a.strOrNull("assigned_by") == userId) continue
                val wo = workOrder(a.str("work_order_id")) ?: continue
                alerts += WorkOrderAlert("assignment:${a.str("id")}", WorkOrderAlert.Kind.ASSIGNED, wo.str("id"), wo.str("title"))
            }
        }
        for (c in rows(conn, "comments", upserted)) {
            val woId = c.strOrNull("work_order_id") ?: continue
            if (c.long("version") != 1L || c.strOrNull("author_user_id") == userId) continue
            val wo = workOrder(woId) ?: continue
            val mine = wo.strOrNull("requester_user_id") == userId ||
                (myWorker != null && (wo.strOrNull("assigned_worker_id") == myWorker || onCrew(conn, woId, myWorker)))
            if (!mine) continue
            alerts += WorkOrderAlert("comment:${c.str("id")}", WorkOrderAlert.Kind.COMMENT, woId, wo.str("title"), c.strOrNull("body")?.trim())
        }
        val settled = mutableSetOf<String>()
        for (wo in rows(conn, "work_orders", upserted)) {
            val id = wo.str("id")
            when {
                wo.str("status") != "pending_approval" -> settled += approvalKey(id)
                canApprove && wo.strOrNull("updated_by") != userId ->
                    alerts += WorkOrderAlert(approvalKey(id), WorkOrderAlert.Kind.NEEDS_APPROVAL, id, wo.str("title"))
            }
        }
        return WorkOrderAlertSelection(alerts, settled)
    }

    private fun approvalKey(workOrderId: String) = "approval:$workOrderId"

    private fun rows(conn: V2Connection, table: String, upserted: Map<String, Set<String>>): List<V2Row> {
        val ids = upserted[table].orEmpty()
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(500).flatMap { chunk ->
            conn.query("SELECT * FROM $table WHERE id IN (${chunk.joinToString(",") { "?" }})", chunk)
        }
    }

    private fun onCrew(conn: V2Connection, workOrderId: String, workerId: String) = conn.query(
        "SELECT 1 FROM work_order_assignments WHERE work_order_id = ? AND worker_id = ? AND acceptance_status <> 'declined'",
        listOf(workOrderId, workerId),
    ).isNotEmpty()
}
