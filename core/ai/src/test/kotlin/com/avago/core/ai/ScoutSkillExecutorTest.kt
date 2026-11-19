package com.avago.core.ai

import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.StockCondition
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderDraft
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ScoutSkillExecutorTest {

    private val identity = mockk<IdentityManager>()
    private val activities = mockk<ActivityRepository>(relaxed = true)
    private val workOrders = mockk<WorkOrderRepository>(relaxed = true)
    private val assets = mockk<AssetRepository>(relaxed = true)
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val existingWo = mockk<WorkOrder>(relaxed = true)

    private lateinit var executor: ScoutSkillExecutor

    @BeforeEach
    fun setUp() {
        every { identity.getActiveAccountId() } returns "acct-001"
        coEvery { workOrders.workOrder("wo-1") } returns existingWo
        coEvery { workOrders.workOrder("missing") } returns null
        executor = ScoutSkillExecutor(identity, activities, workOrders, assets, inventory)
    }

    @Test
    fun `log-entry-create creates an activity with a cost line`() = runTest {
        val fields = mapOf("title" to "Oil change", "asset_id" to "ast-1", "cost" to "89.99", "category" to "oil_change")
        assertTrue(executor.executeIfPossible("log-entry-create", fields))
        coVerify(exactly = 1) {
            activities.create(
                match { it.title == "Oil change" && it.assetId == "ast-1" && it.type == ActivityType.SERVICE },
                match { it.single().kind == CostLineType.MISC && it.single().unitCost == BigDecimal("89.99") },
                any(),
            )
        }
    }

    @Test
    fun `fuel-log maps to a fuel activity`() = runTest {
        assertTrue(executor.executeIfPossible("fuel-log", mapOf("title" to "Fill-up", "asset_id" to "a", "category" to "fuel")))
        coVerify { activities.create(match { it.type == ActivityType.FUEL }, emptyList(), any()) }
    }

    @Test
    fun `log-entry-create falls back without title or asset`() = runTest {
        assertFalse(executor.executeIfPossible("log-entry-create", mapOf("asset_id" to "a")))
        assertFalse(executor.executeIfPossible("inspection-from-voice", mapOf("title" to "x", "asset_id" to " ")))
        coVerify(exactly = 0) { activities.create(any(), any(), any()) }
    }

    @Test
    fun `work-order-create defaults an invalid priority to medium and parses dates`() = runTest {
        val draft = slot<WorkOrderDraft>()
        coEvery { workOrders.create(capture(draft), any()) } returns "wo-new"
        val fields = mapOf("title" to "Fix pump", "priority" to "urgent!!", "asset_id" to "a1", "due_date" to "2024-06-15T10:30:00Z")
        assertTrue(executor.executeIfPossible("work-order-create", fields))
        assertEquals(WorkOrderPriority.MEDIUM, draft.captured.priority)
        assertEquals(listOf("a1"), draft.captured.assetIds)
        assertEquals(1718447400000L, draft.captured.dueAt)
    }

    @Test
    fun `work-order-create falls back on a blank title`() = runTest {
        assertFalse(executor.executeIfPossible("work-order-create", mapOf("title" to "  ")))
    }

    @Test
    fun `reschedule edits the due date only`() = runTest {
        val change = slot<(WorkOrderDraft) -> WorkOrderDraft>()
        coEvery { workOrders.edit("wo-1", capture(change)) } returns Unit
        assertTrue(executor.executeIfPossible("reschedule", mapOf("wo_id" to "wo-1", "due_date" to "2024-07-01")))
        val edited = change.captured(WorkOrderDraft(title = "Keep", priority = WorkOrderPriority.HIGH))
        assertEquals("Keep", edited.title)
        assertEquals(WorkOrderPriority.HIGH, edited.priority)
        assertEquals(1719792000000L, edited.dueAt)
    }

    @Test
    fun `reschedule falls back for unknown work orders or no changes`() = runTest {
        assertFalse(executor.executeIfPossible("reschedule", mapOf("wo_id" to "missing", "due_date" to "2024-07-01")))
        assertFalse(executor.executeIfPossible("reschedule", mapOf("wo_id" to "wo-1")))
        coVerify(exactly = 0) { workOrders.edit(any(), any()) }
    }

    @Test
    fun `work-order-action maps actions to statuses`() = runTest {
        mapOf(
            "complete" to WorkOrderStatus.COMPLETED,
            "start" to WorkOrderStatus.IN_PROGRESS,
            "pause" to WorkOrderStatus.ON_HOLD,
            "cancel" to WorkOrderStatus.CANCELLED,
            "reopen" to WorkOrderStatus.OPEN,
        ).forEach { (action, status) ->
            assertTrue(executor.executeIfPossible("work-order-action", mapOf("wo_id" to "wo-1", "action" to action)))
            coVerify { workOrders.transition("wo-1", status, any()) }
        }
    }

    @Test
    fun `work-order-action falls back without a work order or action`() = runTest {
        assertFalse(executor.executeIfPossible("work-order-action", mapOf("action" to "start")))
        assertFalse(executor.executeIfPossible("work-order-action", mapOf("wo_id" to "wo-1", "action" to "explode")))
    }

    @Test
    fun `work-order-assign assigns the user`() = runTest {
        assertTrue(executor.executeIfPossible("work-order-assign", mapOf("wo_id" to "wo-1", "assigned_to" to "u-9")))
        coVerify { workOrders.assign("wo-1", "u-9", any(), any()) }
        assertFalse(executor.executeIfPossible("work-order-assign", mapOf("wo_id" to "wo-1")))
    }

    @Test
    fun `asset-create creates the asset and sets its parent`() = runTest {
        coEvery { assets.create(any()) } returns "ast-new"
        val fields = mapOf("name" to "forklift", "year" to "2020", "parent_asset_id" to "p-1")
        assertTrue(executor.executeIfPossible("asset-create", fields))
        coVerify { assets.create(match { it.name == "forklift" && it.modelYear == 2020 && it.avatarInitial == "F" }) }
        coVerify { assets.setParent("ast-new", "p-1") }
        assertFalse(executor.executeIfPossible("asset-create", mapOf("name" to "")))
    }

    @Test
    fun `inventory-use issues at the first stocked position`() = runTest {
        every { inventory.observePositions(productId = "p-1") } returns flowOf(
            listOf(StockPosition("pos", "p-1", "loc-1", "Main", "bin-1", "A1", null, StockCondition.GOOD, BigDecimal.TEN, 1)),
        )
        assertTrue(executor.executeIfPossible("inventory-use", mapOf("part_id" to "p-1", "quantity" to "3")))
        coVerify {
            inventory.postStockDocument(match { d ->
                d.kind == MovementKind.ISSUE && d.locationId == "loc-1" &&
                    d.lines.single().quantity == BigDecimal("3") && d.lines.single().binId == "bin-1"
            })
        }
    }

    @Test
    fun `inventory-receive falls back when the part is not stocked anywhere`() = runTest {
        every { inventory.observePositions(productId = "p-2") } returns flowOf(emptyList())
        assertFalse(executor.executeIfPossible("inventory-receive", mapOf("part_id" to "p-2", "quantity" to "3")))
        coVerify(exactly = 0) { inventory.postStockDocument(any()) }
    }

    @Test
    fun `parts-catalog-add books an initial quantity as opening balance`() = runTest {
        coEvery { inventory.createProduct(any()) } returns "prod-1"
        val fields = mapOf("part_name" to "Filter", "quantity_on_hand" to "5", "location_id" to "loc-1")
        assertTrue(executor.executeIfPossible("parts-catalog-add", fields))
        coVerify {
            inventory.postStockDocument(match { it.kind == MovementKind.OPENING_BALANCE && it.lines.single().productId == "prod-1" })
        }
    }

    @Test
    fun `parts-catalog-add with stock but no location falls back before creating`() = runTest {
        assertFalse(executor.executeIfPossible("parts-catalog-add", mapOf("part_name" to "Filter", "quantity_on_hand" to "5")))
        coVerify(exactly = 0) { inventory.createProduct(any()) }
    }

    @Test
    fun `unknown skills and missing accounts fall back`() = runTest {
        assertFalse(executor.executeIfPossible("work-order-recurrence", mapOf("wo_id" to "wo-1", "rrule" to "FREQ=DAILY")))
        every { identity.getActiveAccountId() } returns null
        assertFalse(executor.executeIfPossible("work-order-create", mapOf("title" to "x")))
    }
}
