package com.avago.core.data.v2.commands

import com.avago.core.data.domain.ApprovalRequest
import com.avago.core.data.domain.RecurrenceRule
import com.avago.core.data.domain.TimeEntry
import com.avago.core.data.domain.TimeEntryDraft
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderDraft
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.data.domain.WorkOrderRecurrence
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkOrderCommandBuildersTest {

    private val wo = WorkOrder(
        id = "00000000-0000-7000-8000-0000000000w1", title = "Fix pump", description = null, categoryId = null, categoryName = null,
        priority = WorkOrderPriority.MEDIUM, status = WorkOrderStatus.OPEN, complexity = null, approvalState = null,
        requesterUserId = null, assignedWorkerId = null, assignedWorkerName = null, assignedTeamId = null, locationId = null,
        locationName = null, projectId = "p1", projectName = "Job 1", assetIds = emptyList(), primaryAssetName = null, dueAt = null,
        plannedStart = 1_000_000L, plannedEnd = 2_000_000L, startedAt = null, completedAt = null, completionActivityId = null,
        failureCode = null, causeCode = null, remedyCode = null, completionNotes = null, skipReason = null, requiresPermit = false,
        requiredSkills = emptyList(), estimatedEffortMinutes = null, currency = null, dispatcherNotes = null, attributes = "{}",
        createdBy = null, createdAt = 0, updatedAt = 0, version = 5,
    )

    private fun update(draft: WorkOrderDraft) =
        WorkOrderCommands.update(wo, draft, WorkOrderLookups(), 10_000, "u", null)

    private fun clears(command: com.avago.core.data.v2.V2NewCommand) =
        command.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `moving one bound of the planned window sends both`() {
        val command = update(WorkOrderDraft.of(wo, null).copy(plannedEnd = 3_000_000L))!!
        val set = command.payload.jsonObject["set"]!!.jsonObject
        assertEquals(setOf("planned_start", "planned_end"), set.keys)
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals(1_000_000L, overlay["planned_during_start"])
        assertEquals(3_000_000L, overlay["planned_during_end"])
    }

    @Test
    fun `clearing the planned window and the project clears all three`() {
        val command = update(WorkOrderDraft.of(wo, null).copy(plannedStart = null, plannedEnd = null, projectId = null))!!
        assertEquals(listOf("planned_end", "planned_start", "project_id"), clears(command))
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertTrue(overlay.containsKey("planned_during_start") && overlay["planned_during_start"] == null)
        assertTrue(overlay.containsKey("project_id") && overlay["project_id"] == null)
    }

    @Test
    fun `a half-open or inverted planned window is refused`() {
        assertThrows(IllegalArgumentException::class.java) { update(WorkOrderDraft.of(wo, null).copy(plannedEnd = null)) }
        assertThrows(IllegalArgumentException::class.java) { update(WorkOrderDraft.of(wo, null).copy(plannedEnd = 500L)) }
    }

    @Test
    fun `claim assigns the caller's worker optimistically`() {
        val command = WorkOrderCommands.claim(wo, "w-me", 10)
        assertEquals("maint.work_orders.claim", command.operation)
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals("w-me", overlay["assigned_worker_id"])
        assertEquals("assigned", overlay["status"])
    }

    private val recurrence = WorkOrderRecurrence(
        planId = "plan", triggerId = "trig", rrule = "FREQ=DAILY", endType = "never", endCount = null, endOnEpochDay = null,
        timezone = null, active = true,
    )

    @Test
    fun `changing a recurrence rewrites the plan's calendar trigger`() {
        val command = WorkOrderCommands.setRecurrence(
            wo.id, RecurrenceRule("FREQ=WEEKLY;BYDAY=MO", endType = "count", endCount = 4), recurrence, 10,
        )
        val payload = command.payload.jsonObject
        assertEquals("FREQ=WEEKLY;BYDAY=MO", payload["rrule"]!!.jsonPrimitive.content)
        assertEquals(4, payload["end_count"]!!.jsonPrimitive.content.toInt())
        val ops = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops
        assertEquals("FREQ=WEEKLY;BYDAY=MO", ops.single { it.table == "pm_triggers" }.set["rrule"])
        assertEquals(4L, ops.single { it.table == "pm_plans" }.set["end_count"])
    }

    @Test
    fun `an empty rule stops the recurrence and a new one has no overlay`() {
        val stop = WorkOrderCommands.setRecurrence(wo.id, RecurrenceRule(""), recurrence, 10)
        assertEquals("", stop.payload.jsonObject["rrule"]!!.jsonPrimitive.content)
        val ops = V2OverlayPatch.parse(stop.overlayPatch!!)!!.ops
        assertEquals(0L, ops.single().set["active"])
        val fresh = WorkOrderCommands.setRecurrence(wo.id, RecurrenceRule("FREQ=DAILY"), null, 10)
        assertTrue(fresh.overlayPatch?.let(V2OverlayPatch::parse)?.ops.isNullOrEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            WorkOrderCommands.setRecurrence(wo.id, RecurrenceRule("FREQ=DAILY", endType = "date"), null, 10)
        }
    }

    private val request = ApprovalRequest(
        id = "req", subjectKind = "work_order", workOrderId = wo.id, purchaseOrderId = null, requestedAmountMinor = null,
        currency = null, status = "pending", requestedBy = null, requestedAt = null, assignedApproverUserId = null, decidedAt = null,
        decisions = emptyList(),
    )

    @Test
    fun `an approval only records the decision while a rejection decides the work order`() {
        val approve = WorkOrderCommands.decideApproval(request, true, " ok ", "d1", 10, "u")
        assertEquals("maint.approval_requests.approve", approve.operation)
        assertEquals("ok", approve.payload.jsonObject["notes"]!!.jsonPrimitive.content)
        assertEquals(listOf("approval_decisions"), V2OverlayPatch.parse(approve.overlayPatch!!)!!.ops.map { it.table })

        val reject = WorkOrderCommands.decideApproval(request, false, null, "d2", 10, "u")
        assertEquals("maint.approval_requests.reject", reject.operation)
        val ops = V2OverlayPatch.parse(reject.overlayPatch!!)!!.ops
        assertEquals("rejected", ops.single { it.table == "approval_requests" }.set["status"])
        assertEquals("rejected", ops.single { it.table == "work_orders" }.set["approval_state"])
    }

    private val entry = TimeEntry(
        id = "te", workerId = "w", workOrderId = wo.id, start = 0, end = 3_600_000, breakMinutes = 0, status = "rejected",
        notes = "old", rejectReason = "too long", version = 3,
    )

    @Test
    fun `logging time validates the span and submits it`() {
        val command = WorkOrderCommands.logTime("te2", wo.id, TimeEntryDraft(0, 3_600_000, 15, " shift "), "w", 4_000_000, "u")
        assertEquals("maint.time_entries.create", command.operation)
        assertEquals(15, command.payload.jsonObject["break_minutes"]!!.jsonPrimitive.content.toInt())
        val set = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals("submitted", set["status"])
        assertEquals("shift", set["notes"])
        assertThrows(IllegalArgumentException::class.java) {
            WorkOrderCommands.logTime("x", wo.id, TimeEntryDraft(0, 5_000_000), "w", 4_000_000, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkOrderCommands.logTime("x", wo.id, TimeEntryDraft(0, 600_000, 10), "w", 4_000_000, "u")
        }
    }

    @Test
    fun `correcting a rejected entry resubmits it and clears emptied notes`() {
        val command = WorkOrderCommands.updateTimeEntry(entry, TimeEntryDraft(0, 1_800_000, 0, null), 4_000_000)!!
        assertEquals(3L, command.baseVersion)
        assertEquals(listOf("notes"), clears(command))
        assertEquals(setOf("end"), command.payload.jsonObject["set"]!!.jsonObject.keys)
        val set = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals("submitted", set["status"])
        assertNull(WorkOrderCommands.updateTimeEntry(entry, TimeEntryDraft(0, 3_600_000, 0, "old"), 4_000_000))
    }

    @Test
    fun `approving and rejecting carry the base version and a reason is required`() {
        val approve = WorkOrderCommands.approveTimeEntry(entry, 10, "boss")
        assertEquals(3L, approve.baseVersion)
        assertEquals("approved", V2OverlayPatch.parse(approve.overlayPatch!!)!!.ops.single().set["status"])
        val reject = WorkOrderCommands.rejectTimeEntry(entry.copy(status = "submitted"), " wrong day ", 10)
        assertEquals("wrong day", reject.payload.jsonObject["reason"]!!.jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) { WorkOrderCommands.rejectTimeEntry(entry, " ", 10) }
        val approved = entry.copy(status = "approved")
        assertFalse(approved.isEditable)
        assertThrows(IllegalArgumentException::class.java) { WorkOrderCommands.deleteTimeEntry(approved) }
    }
}
