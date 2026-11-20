package com.avago.core.data.v2.repo

import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2Uuid
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class V2WorkOrderAlertsTest {
    private val business = V2Fixtures.business()
    private val me = V2Uuid.v7()
    private val boss = V2Uuid.v7()
    private val myWorker = V2Uuid.v7()
    private val otherWorker = V2Uuid.v7()
    private val mine = V2Uuid.v7()
    private val theirs = V2Uuid.v7()
    private val crewed = V2Uuid.v7()

    private suspend fun seed() = business.writeWithoutForeignKeys { tx ->
        for ((worker, user) in listOf(myWorker to me, otherWorker to boss)) {
            tx.execute("INSERT INTO workers (id, created_at, updated_at, version, user_id, attributes) VALUES (?, 1, 1, 1, ?, '{}')", listOf(worker, user))
        }
        for ((id, title, assignee) in listOf(Triple(mine, "Fix pump", myWorker), Triple(theirs, "Paint shed", otherWorker), Triple(crewed, "Move rack", otherWorker))) {
            workOrder(tx::execute, id, title, assignee, "open", boss)
        }
        assignment(tx::execute, V2Uuid.v7(), crewed, myWorker, "accepted", boss, version = 2)
    }

    private fun workOrder(exec: (String, List<Any?>) -> Long, id: String, title: String, worker: String, status: String, by: String, version: Long = 1) = exec(
        "INSERT OR REPLACE INTO work_orders (id, created_at, updated_at, version, title, status, assigned_worker_id, requester_user_id, updated_by, attributes) " +
            "VALUES (?, 1, 1, ?, ?, ?, ?, ?, ?, '{}')",
        listOf(id, version, title, status, worker, boss, by),
    )

    private fun assignment(exec: (String, List<Any?>) -> Long, id: String, wo: String, worker: String, status: String, by: String, version: Long = 1) = exec(
        "INSERT INTO work_order_assignments (id, created_at, updated_at, version, work_order_id, worker_id, acceptance_status, assigned_by) VALUES (?, 1, 1, ?, ?, ?, ?, ?)",
        listOf(id, version, wo, worker, status, by),
    )

    private fun comment(exec: (String, List<Any?>) -> Long, id: String, wo: String, author: String, body: String) = exec(
        "INSERT INTO comments (id, created_at, updated_at, version, work_order_id, author_user_id, body) VALUES (?, 1, 1, 1, ?, ?, ?)",
        listOf(id, wo, author, body),
    )

    private suspend fun select(upserted: Map<String, Set<String>>, canApprove: Boolean = false) =
        business.read { V2WorkOrderAlerts.select(it, upserted, me, canApprove) }

    @Test
    fun `a new assignment to me by someone else is announced once it is awaiting acceptance`() = runTest {
        seed()
        val fresh = V2Uuid.v7()
        val mySelf = V2Uuid.v7()
        val other = V2Uuid.v7()
        val accepted = V2Uuid.v7()
        business.writeWithoutForeignKeys { tx ->
            assignment(tx::execute, fresh, theirs, myWorker, "assigned", boss)
            assignment(tx::execute, mySelf, mine, myWorker, "pending", me)
            assignment(tx::execute, other, mine, otherWorker, "assigned", boss)
            assignment(tx::execute, accepted, mine, myWorker, "accepted", boss)
        }

        val alerts = select(mapOf("work_order_assignments" to setOf(fresh, mySelf, other, accepted))).alerts

        assertEquals(listOf(WorkOrderAlert("assignment:$fresh", WorkOrderAlert.Kind.ASSIGNED, theirs, "Paint shed")), alerts)
    }

    @Test
    fun `comments by others reach the requester, the assignee and the crew only`() = runTest {
        seed()
        val onMine = V2Uuid.v7()
        val onCrewed = V2Uuid.v7()
        val onTheirs = V2Uuid.v7()
        val byMe = V2Uuid.v7()
        business.writeWithoutForeignKeys { tx ->
            comment(tx::execute, onMine, mine, boss, " Parts arrived ")
            comment(tx::execute, onCrewed, crewed, boss, "Bring the forklift")
            comment(tx::execute, onTheirs, theirs, boss, "Not for me")
            comment(tx::execute, byMe, mine, me, "My own note")
        }

        val alerts = select(mapOf("comments" to setOf(onMine, onCrewed, onTheirs, byMe))).alerts

        assertEquals(
            setOf(
                WorkOrderAlert("comment:$onMine", WorkOrderAlert.Kind.COMMENT, mine, "Fix pump", "Parts arrived"),
                WorkOrderAlert("comment:$onCrewed", WorkOrderAlert.Kind.COMMENT, crewed, "Move rack", "Bring the forklift"),
            ),
            alerts.toSet(),
        )
    }

    @Test
    fun `approvers hear of work orders awaiting approval and the round settles when it leaves`() = runTest {
        seed()
        val request = V2Uuid.v7()
        business.writeWithoutForeignKeys { tx -> workOrder(tx::execute, request, "New guard rail", otherWorker, "pending_approval", boss) }
        val upserted = mapOf("work_orders" to setOf(request, mine))

        assertEquals(emptyList<WorkOrderAlert>(), select(upserted).alerts, "needs work_orders.approve")
        val selection = select(upserted, canApprove = true)
        assertEquals(listOf(WorkOrderAlert("approval:$request", WorkOrderAlert.Kind.NEEDS_APPROVAL, request, "New guard rail")), selection.alerts)
        assertEquals(setOf("approval:$mine"), selection.settled)

        business.writeWithoutForeignKeys { tx -> workOrder(tx::execute, request, "New guard rail", otherWorker, "pending_approval", me, version = 2) }
        assertEquals(emptyList<WorkOrderAlert>(), select(upserted, canApprove = true).alerts, "my own submission")
    }

    @Test
    fun `an alert is announced once until its event settles`() {
        val a = WorkOrderAlert("approval:1", WorkOrderAlert.Kind.NEEDS_APPROVAL, "1", "A")
        val b = WorkOrderAlert("comment:2", WorkOrderAlert.Kind.COMMENT, "2", "B", "hi")

        val (first, announced) = WorkOrderAlertSelection(listOf(a, b, b), emptySet()).unannounced(listOf("old"), cap = 2)
        assertEquals(listOf(a, b), first)
        assertEquals(listOf("approval:1", "comment:2"), announced, "capped to the newest keys")

        assertEquals(emptyList<WorkOrderAlert>(), WorkOrderAlertSelection(listOf(a), emptySet()).unannounced(announced).first)
        val (afterSettle, kept) = WorkOrderAlertSelection(emptyList(), setOf("approval:1")).unannounced(announced)
        assertEquals(emptyList<WorkOrderAlert>(), afterSettle)
        assertEquals(listOf(a), WorkOrderAlertSelection(listOf(a), emptySet()).unannounced(kept).first, "a new approval round")
    }
}
