package com.avago.feature.log.model

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LogRebaseTest {

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

    private fun draftOf(a: Activity) = ActivityDraft(
        assetId = a.assetId, workOrderId = a.workOrderId, type = a.type, categoryLabel = a.categoryLabel, title = a.title,
        occurredAt = a.occurredAt, meterValue = a.meterValue, currency = a.currency, notes = a.notes,
        payload = a.payload, attributes = a.attributes,
    )

    private fun rebase(original: Activity, latest: Activity, draft: ActivityDraft) =
        LogRebase.rebase(original, latest, draft) { it.toString() }

    @Test
    fun `fields only someone else changed keep their value`() {
        val original = activity()
        val latest = activity(notes = "theirs", meter = BigDecimal("120"), version = 2)
        val result = rebase(original, latest, draftOf(original).copy(title = "Mine"))
        assertTrue(result.conflicts.isEmpty())
        assertEquals("Mine", result.draft.title)
        assertEquals("theirs", result.draft.notes)
        assertEquals(BigDecimal("120"), result.draft.meterValue)
    }

    @Test
    fun `fields both sides changed differently are reported and keep mine`() {
        val original = activity(notes = "a")
        val latest = activity(notes = "theirs", meter = BigDecimal("5.0"), version = 2)
        val result = rebase(original, latest, draftOf(original).copy(notes = "mine", meterValue = BigDecimal("5")))
        assertEquals(listOf(LogEditConflict("field.notes", "mine", "theirs")), result.conflicts)
        assertEquals("mine", result.draft.notes)
    }

    @Test
    fun `attribute keys merge independently`() {
        val original = activity(attributes = """{"a":1,"b":1,"c":1}""")
        val latest = activity(attributes = """{"a":2,"b":1,"c":3}""", version = 2)
        val result = rebase(original, latest, draftOf(original).copy(attributes = """{"a":1,"b":9,"c":4}"""))
        val merged = Json.parseToJsonElement(result.draft.attributes!!).jsonObject
        assertEquals(JsonPrimitive(2), merged["a"])
        assertEquals(JsonPrimitive(9), merged["b"])
        assertEquals(JsonPrimitive(4), merged["c"])
        assertEquals(listOf(LogEditConflict("attr.c", "4", "3")), result.conflicts)
    }

    @Test
    fun `a key removed locally is removed from the merge`() {
        fun o(s: String) = Json.parseToJsonElement(s).jsonObject
        val merged = LogRebase.rebaseJson(o("""{"x":1,"y":1}"""), o("""{"y":1}"""), o("""{"x":1,"y":2,"z":3}"""))
        assertEquals(o("""{"y":2,"z":3}"""), merged.value)
        assertTrue(merged.conflicts.isEmpty())
    }
}
