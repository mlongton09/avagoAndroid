package com.avago.feature.log.viewmodel

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LogAuditTest {

    private fun activity(
        id: String = "log",
        payload: String = "{}",
        attributes: String = "{}",
        type: ActivityType = ActivityType.SERVICE,
        title: String? = "Oil",
        notes: String? = null,
        meter: BigDecimal? = null,
        occurredAt: Long = 1_000,
        version: Long = 1,
    ) = Activity(
        id = id, assetId = "asset", assetName = null, workOrderId = null, parentActivityId = null, type = type,
        categoryLabel = "service", title = title, occurredAt = occurredAt, performedByUserId = null, performedByName = null,
        providerName = null, currency = "EUR", overtimeMultiplier = null, notes = notes, payload = payload,
        attributes = attributes, meterValue = meter, totalCost = BigDecimal.ZERO, createdAt = 0, version = version,
    )

    private val names = mapOf("u1" to "Ann", "u2" to "Bob")

    @Test
    fun `saves within the grace window are not shown as edits`() {
        val log = activity().copy(createdByUserId = "u1", updatedAt = LogAudit.EDIT_GRACE_MS, updatedByUserId = "u1")
        assertEquals(LogAudit(0, "Ann", null, null), LogAudit.of(log, names))
    }

    @Test
    fun `later edits show who and when, unknown users stay unnamed`() {
        val log = activity().copy(createdByUserId = "gone", updatedAt = 120_000, updatedByUserId = "u2")
        assertEquals(LogAudit(0, null, 120_000, "Bob"), LogAudit.of(log, names))
    }
}
