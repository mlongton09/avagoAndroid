package com.avago.feature.rentalsops.domain

import com.avago.feature.rentalsops.domain.DeliveryStateMachine.QueuedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeliveryStateMachineTest {

    private fun t(
        from: TaskStatus,
        action: TaskAction,
        kind: TaskKind = TaskKind.DELIVERY,
        hasDriver: Boolean = true,
        proof: Proof = Proof(),
        reason: String? = null,
    ) = DeliveryStateMachine.transition(from, action, kind, hasDriver, proof, reason)

    @Test
    fun `delivery happy path with photo proof`() {
        var s = TaskStatus.ASSIGNED
        for (a in listOf(TaskAction.START, TaskAction.ARRIVE, TaskAction.COMPLETE)) {
            val r = t(s, a, proof = Proof(photos = 1))
            s = (r as TransitionResult.Ok).to
        }
        assertEquals(TaskStatus.COMPLETED, s)
        assertTrue(!s.isOpen)
    }

    @Test
    fun `delivery completion requires photo or named signature`() {
        assertEquals(
            TransitionResult.Rejected(TransitionError.ProofRequired),
            t(TaskStatus.ARRIVED, TaskAction.COMPLETE),
        )
        // Signature without a printed name is not accepted client-side.
        assertEquals(
            TransitionResult.Rejected(TransitionError.ProofRequired),
            t(TaskStatus.ARRIVED, TaskAction.COMPLETE, proof = Proof(signature = true, signedName = "  ")),
        )
        assertEquals(
            TransitionResult.Ok(TaskStatus.COMPLETED),
            t(TaskStatus.ARRIVED, TaskAction.COMPLETE, proof = Proof(signature = true, signedName = "Ana")),
        )
        // Complete straight from en_route is allowed (arrive is optional).
        assertEquals(
            TransitionResult.Ok(TaskStatus.COMPLETED),
            t(TaskStatus.EN_ROUTE, TaskAction.COMPLETE, proof = Proof(photos = 2)),
        )
    }

    @Test
    fun `pickup and transfer complete without proof`() {
        assertEquals(TransitionResult.Ok(TaskStatus.COMPLETED), t(TaskStatus.EN_ROUTE, TaskAction.COMPLETE, TaskKind.PICKUP))
        assertEquals(TransitionResult.Ok(TaskStatus.COMPLETED), t(TaskStatus.ARRIVED, TaskAction.COMPLETE, TaskKind.TRANSFER))
    }

    @Test
    fun `fail needs a reason and an active task`() {
        assertEquals(TransitionResult.Rejected(TransitionError.ReasonRequired), t(TaskStatus.EN_ROUTE, TaskAction.FAIL, reason = " "))
        assertEquals(TransitionResult.Ok(TaskStatus.FAILED), t(TaskStatus.ARRIVED, TaskAction.FAIL, reason = "gate locked"))
        // Pending (unassigned) tasks cannot be failed by a driver — matches the server.
        assertEquals("invalid_transition", (t(TaskStatus.PENDING, TaskAction.FAIL, reason = "x") as TransitionResult.Rejected).error.code)
    }

    @Test
    fun `start from pending needs a driver`() {
        assertEquals(TransitionResult.Rejected(TransitionError.DriverRequired), t(TaskStatus.PENDING, TaskAction.START, hasDriver = false))
        assertEquals(TransitionResult.Ok(TaskStatus.EN_ROUTE), t(TaskStatus.PENDING, TaskAction.START))
    }

    @Test
    fun `terminal states reject every driver action`() {
        for (s in listOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
            for (a in TaskAction.entries) {
                val r = t(s, a, proof = Proof(photos = 1), reason = "r")
                assertTrue(r is TransitionResult.Rejected, "$s/$a")
            }
            assertTrue(DeliveryStateMachine.availableActions(s, true).isEmpty())
        }
        assertEquals("invalid_transition", (t(TaskStatus.ASSIGNED, TaskAction.ARRIVE) as TransitionResult.Rejected).error.code)
    }

    @Test
    fun `available actions only offer valid transitions`() {
        for (s in TaskStatus.entries) {
            for (a in DeliveryStateMachine.availableActions(s, hasDriver = true)) {
                val r = t(s, a, proof = Proof(photos = 1), reason = "r")
                assertTrue(r is TransitionResult.Ok, "$s offers $a but it is rejected")
            }
        }
        assertTrue(DeliveryStateMachine.availableActions(TaskStatus.PENDING, hasDriver = false).isEmpty())
    }

    @Test
    fun `fold replays queued events and reports stale ones`() {
        val queued = listOf(
            QueuedEvent("q1", TaskAction.START),
            QueuedEvent("q2", TaskAction.ARRIVE),
            QueuedEvent("q3", TaskAction.COMPLETE, proof = Proof(photos = 1)),
        )
        val ok = DeliveryStateMachine.fold(TaskStatus.ASSIGNED, TaskKind.DELIVERY, true, queued)
        assertEquals(TaskStatus.COMPLETED, ok.status)
        assertTrue(ok.stale.isEmpty())

        // Dispatcher cancelled the task while the driver was offline: every event is stale.
        val cancelled = DeliveryStateMachine.fold(TaskStatus.CANCELLED, TaskKind.DELIVERY, true, queued)
        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(listOf("q1", "q2", "q3"), cancelled.stale.map { it.queueId })

        // Server already applied START (ack lost): START is stale, the rest still applies.
        val partial = DeliveryStateMachine.fold(TaskStatus.EN_ROUTE, TaskKind.DELIVERY, true, queued)
        assertEquals(TaskStatus.COMPLETED, partial.status)
        assertEquals(listOf("q1"), partial.stale.map { it.queueId })
    }

    @Test
    fun `wire names round trip`() {
        for (s in TaskStatus.entries) assertEquals(s, TaskStatus.parse(s.wire))
        for (a in TaskAction.entries) assertEquals(a, TaskAction.parse(a.wire))
        assertEquals(TaskKind.TRANSFER, TaskKind.parse("transfer"))
        assertEquals(null, TaskAction.parse("teleport"))
    }
}
