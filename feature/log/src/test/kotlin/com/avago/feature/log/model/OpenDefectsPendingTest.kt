package com.avago.feature.log.model

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.DefectState
import com.avago.core.data.domain.InspectionDefect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OpenDefectsPendingTest {

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

    private val payload = """{"values":{},"defects":[
        {"itemId":"brakes","key":"brakes","label":"Brakes","answer":"fail","bucket":"NR","severity":"major"},
        {"itemId":"tyre","key":"tyre:fl","label":"Tyre","answer":"warn","bucket":"M","severity":"minor"}
    ]}"""

    private fun register(key: String, source: String?, status: DefectState = DefectState.OPEN) = InspectionDefect(
        id = "r-$key", assetId = "asset", itemId = key, itemKey = key, label = key, cornerLabel = null, sectionTitle = null,
        bucket = "nr", severity = "major", note = null, locationLabel = null, status = status, sourceActivityId = source,
        lastSeenActivityId = source, reportedAt = 0, lastSeenAt = null, resolvedAt = null, resolutionNote = null,
        workOrderId = null, version = 1,
    )

    @Test
    fun `an unsynced inspection's defects are pending until the register records it`() {
        val latest = activity(id = "insp", type = ActivityType.INSPECTION, payload = payload)
        val pending = OpenDefects.pendingFromInspection(emptyList(), latest)
        assertEquals(listOf("pending:insp:brakes", "pending:insp:tyre:fl"), pending.map { it.id })
        assertEquals(listOf("open", "monitoring"), pending.map { it.status })
        assertTrue(pending.all(OpenDefects::isPending))
    }

    @Test
    fun `keys the register tracks as active are not repeated`() {
        val latest = activity(id = "insp", type = ActivityType.INSPECTION, payload = payload)
        val pending = OpenDefects.pendingFromInspection(
            listOf(register("brakes", source = "older"), register("tyre:fl", source = "older", status = DefectState.REPAIRED)),
            latest,
        )
        assertEquals(listOf("tyre:fl"), pending.map { it.key })
    }

    @Test
    fun `nothing is derived once the inspection synced or for other kinds`() {
        val latest = activity(id = "insp", type = ActivityType.INSPECTION, payload = payload)
        assertTrue(OpenDefects.pendingFromInspection(listOf(register("other", source = "insp")), latest).isEmpty())
        assertTrue(OpenDefects.pendingFromInspection(emptyList(), latest.copy(type = ActivityType.SERVICE)).isEmpty())
        assertTrue(OpenDefects.pendingFromInspection(emptyList(), null).isEmpty())
    }
}
