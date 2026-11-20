package com.avago.core.data.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConflictDiffTest {
    private fun conflict(payload: String, snapshot: String?) = SyncConflict(
        id = "c", commandId = "k", entityType = "work_orders", entityKey = "w", baseVersion = 2, serverVersion = 3,
        clientPayload = payload, serverSnapshot = snapshot, status = "open", version = 1,
    )

    @Test
    fun `an update payload lists set and cleared fields against the server row`() {
        val diff = conflict(
            """{"id":"w","set":{"title":"Mine","priority":2},"clear":["due_at"]}""",
            """{"id":"w","title":"Theirs","priority":null,"due_at":1700000000000}""",
        ).fieldDiffs()

        assertEquals(
            listOf(
                ConflictField("title", "Mine", "Theirs"),
                ConflictField("priority", "2", null),
                ConflictField("due_at", null, "1700000000000"),
            ),
            diff,
        )
    }

    @Test
    fun `an action payload lists its fields except the id, without a snapshot`() {
        assertEquals(
            listOf(ConflictField("status", "done", null)),
            conflict("""{"id":"w","status":"done"}""", null).fieldDiffs(),
        )
    }

    @Test
    fun `an unreadable payload yields no fields`() {
        assertEquals(emptyList<ConflictField>(), conflict("not json", null).fieldDiffs())
    }
}
