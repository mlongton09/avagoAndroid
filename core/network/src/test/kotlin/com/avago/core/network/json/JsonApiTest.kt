package com.avago.core.network.json

import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonApiTest {
    @Test
    fun `unwraps a lone data envelope but keeps bare bodies`() {
        assertEquals("a1", JsonApi.parseBody("""{"data":{"id":"a1"}}""").str("id"))
        val bare = JsonApi.parseBody("""{"data":{"id":"a1"},"next_before":"x"}""")
        assertEquals("x", bare.str("next_before"))
        assertTrue(JsonApi.parseBody("").isEmpty())
        assertEquals(2, JsonApi.parseBody("""[{"a":1},{"a":2}]""").objs("items").size)
    }

    @Test
    fun `parses coded errors in flat and nested shapes`() {
        val flat = JsonApi.parseError(409, """{"error":"Conflict","code":"clock_already_open","message":"Already clocked in","details":{"session_id":"s1"}}""")
        assertEquals("clock_already_open", flat.code)
        assertEquals("Already clocked in", flat.message)
        assertEquals("s1", flat.details?.str("session_id"))

        val nested = JsonApi.parseError(503, """{"error":{"code":"connect_not_configured","message":"No keys"}}""")
        assertEquals("connect_not_configured", nested.code)

        val codeOnly = JsonApi.parseError(403, """{"error":"action_disabled"}""")
        assertEquals("action_disabled", codeOnly.code)

        val text = JsonApi.parseError(502, "Bad gateway")
        assertNull(text.code)
        assertFalse(text.offline)
    }

    @Test
    fun `jsonBody drops nulls but keeps explicit JsonNull`() {
        val b = jsonBody("a" to 1, "b" to null, "c" to JsonNull, "d" to listOf("x"))
        assertFalse(b.containsKey("b"))
        assertTrue(b["c"] is JsonNull)
        assertEquals(listOf("x"), b.strs("d"))
    }
}
