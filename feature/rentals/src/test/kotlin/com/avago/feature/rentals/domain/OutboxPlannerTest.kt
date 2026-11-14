package com.avago.feature.rentals.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutboxPlannerTest {

    private fun item(
        id: String,
        type: String,
        group: String = "o1",
        at: Long = 0,
        dependsOn: List<String> = emptyList(),
        photos: List<String> = emptyList(),
        log: String? = null,
        status: String = "pending",
        attempts: Long = 0,
    ) = OutboxPlanner.Item(id, type, "cu-$id", group, at, dependsOn, photos, log, status, attempts)

    @Test
    fun `steps of one group run in upload order regardless of creation time`() {
        val items = listOf(
            item("ledger", OutboxPlanner.TYPE_LEDGER, at = 1),
            item("handover", OutboxPlanner.TYPE_HANDOVER, at = 2),
            item("sig", OutboxPlanner.TYPE_SIGNATURE, at = 3),
            item("order", OutboxPlanner.TYPE_ORDER, at = 4),
            item("cust", OutboxPlanner.TYPE_CUSTOMER, at = 5),
        )
        val plan = OutboxPlanner.plan(items)
        assertEquals(listOf("cust", "order", "sig", "handover", "ledger"), plan.runnable.map { it.queueId })
        assertTrue(plan.waiting.isEmpty())
    }

    @Test
    fun `handover waits for photos then inspection log and blocks the ledger behind it`() {
        val items = listOf(
            item("handover", OutboxPlanner.TYPE_HANDOVER, photos = listOf("p1"), log = "log1"),
            item("ledger", OutboxPlanner.TYPE_LEDGER),
            item("other", OutboxPlanner.TYPE_ORDER, group = "o2"),
        )
        val noPhotos = OutboxPlanner.plan(items, photoUploaded = { false }, logSynced = { false })
        assertEquals(OutboxPlanner.Wait.PHOTOS, noPhotos.waiting["handover"])
        assertEquals(OutboxPlanner.Wait.EARLIER_STEP, noPhotos.waiting["ledger"])
        assertEquals(listOf("other"), noPhotos.runnable.map { it.queueId }, "other groups continue")

        val noLog = OutboxPlanner.plan(items, photoUploaded = { true }, logSynced = { false })
        assertEquals(OutboxPlanner.Wait.INSPECTION_LOG, noLog.waiting["handover"])

        val ready = OutboxPlanner.plan(items)
        assertEquals(listOf("handover", "ledger", "other"), ready.runnable.map { it.queueId })
    }

    @Test
    fun `cross group dependency runs first and blocks while unrunnable`() {
        val cust = item("cust", OutboxPlanner.TYPE_CUSTOMER, group = "c1", at = 9)
        val order = item("order", OutboxPlanner.TYPE_ORDER, group = "o1", at = 1, dependsOn = listOf("cu-cust"))
        val plan = OutboxPlanner.plan(listOf(order, cust))
        assertEquals(listOf("cust", "order"), plan.runnable.map { it.queueId })

        val conflicted = OutboxPlanner.plan(listOf(order, cust.copy(status = "conflict")))
        assertEquals(OutboxPlanner.Wait.CONFLICT, conflicted.waiting["cust"])
        assertEquals(OutboxPlanner.Wait.DEPENDENCY, conflicted.waiting["order"])

        // Dependency already confirmed (no longer queued) → order runs.
        val confirmed = OutboxPlanner.plan(listOf(order), queued = setOf("cu-order"))
        assertEquals(listOf("order"), confirmed.runnable.map { it.queueId })
    }

    @Test
    fun `items that exhausted attempts stop their group`() {
        val plan = OutboxPlanner.plan(
            listOf(
                item("a", OutboxPlanner.TYPE_ORDER_ACTION, attempts = OutboxPlanner.MAX_ATTEMPTS),
                item("b", OutboxPlanner.TYPE_LEDGER),
            ),
        )
        assertEquals(OutboxPlanner.Wait.GAVE_UP, plan.waiting["a"])
        assertEquals(OutboxPlanner.Wait.EARLIER_STEP, plan.waiting["b"])
        assertTrue(plan.runnable.isEmpty())
    }

    @Test
    fun `a failed item stops its group but not other groups`() {
        val plan = OutboxPlanner.plan(
            listOf(
                item("h", OutboxPlanner.TYPE_HANDOVER, status = OutboxPlanner.STATUS_FAILED),
                item("l", OutboxPlanner.TYPE_LEDGER),
                item("x", OutboxPlanner.TYPE_ORDER, group = "o2"),
            ),
        )
        assertEquals(OutboxPlanner.Wait.FAILED, plan.waiting["h"])
        assertEquals(OutboxPlanner.Wait.EARLIER_STEP, plan.waiting["l"])
        assertEquals(listOf("x"), plan.runnable.map { it.queueId })
    }

    @Test
    fun `idempotent replays and error dispositions`() {
        val d = OutboxPlanner::disposition
        assertEquals(OutboxPlanner.Disposition.UNAUTHORIZED, d(OutboxPlanner.TYPE_ORDER, 401, null, null))
        assertEquals(OutboxPlanner.Disposition.LATER, d(OutboxPlanner.TYPE_HANDOVER, 501, "not_implemented", null))
        // A missing route (code-less 404, 405) is a visible failure, never a silent stall.
        assertEquals(OutboxPlanner.Disposition.FAILED, d(OutboxPlanner.TYPE_HANDOVER, 404, null, "Not found"))
        assertEquals(OutboxPlanner.Disposition.FAILED, d(OutboxPlanner.TYPE_LEDGER, 405, null, null))
        assertEquals(OutboxPlanner.Disposition.FAILED, d(OutboxPlanner.TYPE_ORDER, 404, null, "Not found"))
        assertEquals(OutboxPlanner.Disposition.CONFLICT, d(OutboxPlanner.TYPE_ORDER, 404, "order_not_found", null))
        assertEquals(OutboxPlanner.Disposition.RETRY, d(OutboxPlanner.TYPE_HANDOVER, 409, "duplicate_client_uuid", null))
        assertEquals(OutboxPlanner.Disposition.CONFLICT, d(OutboxPlanner.TYPE_HANDOVER, 409, "already_checked_out", null))
        assertEquals(
            OutboxPlanner.Disposition.RETRY,
            d(OutboxPlanner.TYPE_ORDER, 409, null, "A request with this idempotency key is still in progress"),
        )
        assertEquals(OutboxPlanner.Disposition.CONFLICT, d(OutboxPlanner.TYPE_ORDER, 409, "version_conflict", null))
        assertEquals(OutboxPlanner.Disposition.RETRY, d(OutboxPlanner.TYPE_ORDER, 503, null, null))
        assertEquals(OutboxPlanner.Disposition.RETRY, d(OutboxPlanner.TYPE_ORDER, 429, null, null))
        assertEquals(OutboxPlanner.Disposition.CONFLICT, d(OutboxPlanner.TYPE_ORDER, 400, "validation", null))
    }

    @Test
    fun `each item keeps its own client uuid so a replay is the same request`() {
        val items = listOf(item("x", OutboxPlanner.TYPE_ORDER), item("x2", OutboxPlanner.TYPE_ORDER_ACTION))
        val first = OutboxPlanner.plan(items).runnable.map { it.clientUuid }
        val replay = OutboxPlanner.plan(items).runnable.map { it.clientUuid }
        assertEquals(first, replay)
        assertEquals(first.toSet().size, first.size)
    }
}
