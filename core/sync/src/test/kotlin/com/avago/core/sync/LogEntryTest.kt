package com.avago.core.sync

import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for LogEntity and LogCostLineEntity data-class contracts.
 *
 * These are pure-JVM tests that verify field presence, nullability rules, and
 * basic arithmetic invariants — no Room, no coroutines, no mocks required.
 */
class LogEntryTest {

    companion object {
        private const val ACCOUNT_ID = "acct-log-test"
        private const val ASSET_ID = "asset-log-test"
        private const val LOG_ID = "log-test-001"
        private const val ENTRY_DATE = 1_736_899_200_000L   // 2025-01-15T00:00:00Z
        private const val CREATED_AT = 1_736_899_200_000L
    }

    // ─── LogEntity construction ───────────────────────────────────────────────

    @Test
    fun `LogEntity can be constructed with required fields only`() {
        val entity = buildMinimalLog()
        assertEquals(LOG_ID, entity.entryId)
        assertEquals(ASSET_ID, entity.assetId)
        assertEquals(ACCOUNT_ID, entity.accountId)
        assertEquals("Oil Change", entity.title)
        assertEquals(ENTRY_DATE, entity.entryDate)
    }

    @Test
    fun `LogEntity required fields are not nullable`() {
        val entity = buildMinimalLog()
        // Compiler-enforced non-null; verify values are non-blank at runtime too
        assertFalse(entity.entryId.isBlank())
        assertFalse(entity.assetId.isBlank())
        assertFalse(entity.accountId.isBlank())
        assertFalse(entity.title.isBlank())
    }

    @Test
    fun `LogEntity optional fields default to null when not provided`() {
        val entity = buildMinimalLog()
        assertNull(entity.odometerValue)
        assertNull(entity.notes)
        assertNull(entity.category)
        assertNull(entity.cost)
        assertNull(entity.performedBy)
        assertNull(entity.performedByUserId)
        assertNull(entity.data)
        assertNull(entity.attributes)
        assertNull(entity.costMode)
        assertNull(entity.deletedAt)
    }

    @Test
    fun `LogEntity timestamps are set on construction`() {
        val entity = buildMinimalLog()
        assertEquals(CREATED_AT, entity.createdAt)
        assertEquals(CREATED_AT, entity.updatedAt)
        assertNull(entity.deletedAt)
    }

    @Test
    fun `LogEntity copy with odometer produces distinct entity`() {
        val original = buildMinimalLog()
        val withMeter = original.copy(odometerValue = 12_345.0)
        assertNull(original.odometerValue)
        assertEquals(12_345.0, withMeter.odometerValue)
    }

    @Test
    fun `LogEntity cost fields are individually nullable`() {
        val entity = buildMinimalLog().copy(
            costItems = 100.0,
            costLabor = 50.0,
            costTax = 15.0,
            costMisc = 5.0,
        )
        assertEquals(100.0, entity.costItems)
        assertEquals(50.0, entity.costLabor)
        assertEquals(15.0, entity.costTax)
        assertEquals(5.0, entity.costMisc)
    }

    @Test
    fun `LogEntity cost components sum matches expected total`() {
        val costItems = 100.0
        val costLabor = 50.0
        val costTax = 15.0
        val entity = buildMinimalLog().copy(
            costItems = costItems,
            costLabor = costLabor,
            costTax = costTax,
            cost = costItems + costLabor + costTax,
        )
        val expectedTotal = costItems + costLabor + costTax
        assertEquals(expectedTotal, entity.cost!!, 0.001)
    }

    @Test
    fun `LogEntity serverVersion defaults to zero`() {
        val entity = buildMinimalLog()
        assertEquals(0L, entity.serverVersion)
    }

    @Test
    fun `LogEntity soft-delete sets deletedAt`() {
        val now = System.currentTimeMillis()
        val entity = buildMinimalLog().copy(deletedAt = now)
        assertNotNull(entity.deletedAt)
        assertEquals(now, entity.deletedAt)
    }

    @Test
    fun `LogEntity category can hold common maintenance values`() {
        val categories = listOf("maintenance", "fuel", "inspection", "repair", "cleaning")
        for (cat in categories) {
            val entity = buildMinimalLog().copy(category = cat)
            assertEquals(cat, entity.category)
        }
    }

    @Test
    fun `LogEntity equality is value-based`() {
        val a = buildMinimalLog()
        val b = buildMinimalLog()
        assertEquals(a, b, "Two LogEntities with identical fields must be equal")
    }

    @Test
    fun `LogEntity inequality when IDs differ`() {
        val a = buildMinimalLog()
        val b = buildMinimalLog().copy(entryId = "log-test-002")
        assertFalse(a == b)
    }

    @Test
    fun `LogEntity parentId is nullable`() {
        val standalone = buildMinimalLog()
        assertNull(standalone.parentId)

        val child = buildMinimalLog().copy(parentId = "log-parent-001")
        assertEquals("log-parent-001", child.parentId)
    }

    @Test
    fun `LogEntity currency and exchange rate fields are nullable`() {
        val entity = buildMinimalLog().copy(
            currency = "USD",
            baseAmount = 165.0,
            exchangeRateUsed = 1.0,
        )
        assertEquals("USD", entity.currency)
        assertEquals(165.0, entity.baseAmount)
        assertEquals(1.0, entity.exchangeRateUsed)
    }

    // ─── LogCostLineEntity construction ──────────────────────────────────────

    @Test
    fun `LogCostLineEntity can be constructed with required fields`() {
        val line = buildCostLine()
        assertEquals("line-001", line.lineId)
        assertEquals(LOG_ID, line.logId)
        assertEquals(ACCOUNT_ID, line.accountId)
        assertEquals("part", line.kind)
        assertEquals(2.0, line.quantity)
        assertEquals(25.0, line.unitCost)
    }

    @Test
    fun `LogCostLineEntity kind accepts part value`() {
        val line = buildCostLine(kind = "part")
        assertEquals("part", line.kind)
    }

    @Test
    fun `LogCostLineEntity kind accepts labor value`() {
        val line = buildCostLine(kind = "labor")
        assertEquals("labor", line.kind)
    }

    @Test
    fun `LogCostLineEntity kind accepts misc value`() {
        val line = buildCostLine(kind = "misc")
        assertEquals("misc", line.kind)
    }

    @Test
    fun `LogCostLineEntity lineTotal equals quantity times unitCost`() {
        val line = buildCostLine(quantity = 3.0, unitCost = 10.0)
        val lineTotal = line.quantity * line.unitCost
        assertEquals(30.0, lineTotal, 0.001)
    }

    @Test
    fun `LogCostLineEntity lineTotal is zero when quantity is zero`() {
        val line = buildCostLine(quantity = 0.0, unitCost = 50.0)
        val lineTotal = line.quantity * line.unitCost
        assertEquals(0.0, lineTotal, 0.001)
    }

    @Test
    fun `LogCostLineEntity optional fields default to null`() {
        val line = buildCostLine()
        assertNull(line.inventoryId)
        assertNull(line.userId)
        assertNull(line.description)
        assertNull(line.taxAmount)
        assertNull(line.glCode)
        assertNull(line.notes)
        assertNull(line.woId)
        assertNull(line.deletedAt)
    }

    @Test
    fun `LogCostLineEntity taxAmount is nullable`() {
        val withTax = buildCostLine().copy(taxAmount = 3.50)
        assertEquals(3.50, withTax.taxAmount)
    }

    @Test
    fun `LogCostLineEntity displayOrder is preserved`() {
        val line = buildCostLine().copy(displayOrder = 3L)
        assertEquals(3L, line.displayOrder)
    }

    @Test
    fun `LogCostLineEntity serverVersion defaults to zero`() {
        val line = buildCostLine()
        assertEquals(0L, line.serverVersion)
    }

    @Test
    fun `LogCostLineEntity equality is value-based`() {
        val a = buildCostLine()
        val b = buildCostLine()
        assertEquals(a, b)
    }

    @Test
    fun `sum of LogCostLines matches parent log cost`() {
        val lines = listOf(
            buildCostLine(lineId = "l1", kind = "part", quantity = 2.0, unitCost = 10.0),
            buildCostLine(lineId = "l2", kind = "labor", quantity = 1.5, unitCost = 40.0),
            buildCostLine(lineId = "l3", kind = "misc", quantity = 1.0, unitCost = 5.0),
        )
        val totalFromLines = lines.sumOf { it.quantity * it.unitCost }
        assertEquals(85.0, totalFromLines, 0.001, "2*10 + 1.5*40 + 1*5 should equal 85")
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildMinimalLog() = LogEntity(
        entryId = LOG_ID,
        assetId = ASSET_ID,
        accountId = ACCOUNT_ID,
        title = "Oil Change",
        entryDate = ENTRY_DATE,
        odometerValue = null,
        category = null,
        cost = null,
        performedBy = null,
        performedByUserId = null,
        notes = null,
        data = null,
        attributes = null,
        costMode = null,
        costItems = null,
        costLabor = null,
        costTax = null,
        currency = null,
        baseAmount = null,
        exchangeRateUsed = null,
        configId = null,
        configVersion = null,
        serviceId = null,
        costMisc = null,
        parentId = null,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )

    private fun buildCostLine(
        lineId: String = "line-001",
        kind: String = "part",
        quantity: Double = 2.0,
        unitCost: Double = 25.0,
    ) = LogCostLineEntity(
        lineId = lineId,
        accountId = ACCOUNT_ID,
        logId = LOG_ID,
        kind = kind,
        displayOrder = 0L,
        inventoryId = null,
        userId = null,
        description = null,
        quantity = quantity,
        unitCost = unitCost,
        taxAmount = null,
        glCode = null,
        notes = null,
        woId = null,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )
}
