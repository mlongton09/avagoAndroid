package com.avago.feature.log.data

import com.avago.core.network.model.InspectionDraftRow
import com.avago.feature.log.model.InspectionDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class InspectionDraftStoreTest {

    private fun row(id: String, logId: String?, at: String, payload: InspectionDraft? = null) = InspectionDraftRow(
        draftId = id, assetId = "asset", logId = logId, updatedAt = at,
        payload = (payload ?: InspectionDraft("acct", "asset", logId, 0, values = mapOf("q" to id))).toJson(),
    )

    @Test
    fun `newest row for exactly this asset and log wins`() {
        val rows = listOf(
            row("old", null, "2026-01-01T00:00:00Z"),
            row("new", null, "2026-02-01T00:00:00Z"),
            row("edit", "log", "2026-03-01T00:00:00Z"),
        )
        assertEquals("new", InspectionDraftStore.newestFor(rows, "asset", null)?.draftId)
        assertEquals("edit", InspectionDraftStore.newestFor(rows, "asset", "log")?.draftId)
        assertNull(InspectionDraftStore.newestFor(rows, "other", null))
    }

    @Test
    fun `a server row becomes a draft saved at the server time`() {
        val at = "2026-02-01T10:00:00Z"
        val draft = InspectionDraftStore.fromServer(row("d", "log", at), "acct2")!!
        assertEquals("acct2", draft.acct)
        assertEquals("log", draft.logId)
        assertEquals(Instant.parse(at).toEpochMilli(), draft.savedAt)
        assertEquals(mapOf("q" to "d"), draft.values)
    }
}
