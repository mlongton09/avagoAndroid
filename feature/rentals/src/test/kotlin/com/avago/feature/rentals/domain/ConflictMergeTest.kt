package com.avago.feature.rentals.domain

import com.avago.feature.rentals.domain.ConflictMerge.Decision
import com.avago.feature.rentals.domain.ConflictMerge.Reason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictMergeTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val base = obj("""{"id":"c1","name":"Ann","email":"a@x.io","phone":"1","notes":null,"server_version":3,"updated_at":"2025-01-01T00:00:00Z"}""")

    @Test
    fun `disjoint changes resend only the local fields with the server version`() {
        // Local edit changed the phone; the form also re-sent the unchanged name.
        val changes = obj("""{"name":"Ann","phone":"2"}""")
        val body = obj("""{"name":"Ann","phone":"2","server_version":3}""")
        val current = obj("""{"id":"c1","name":"Ann","email":"b@x.io","phone":"1","notes":null,"server_version":4,"updated_at":"2025-01-02T00:00:00Z"}""")

        val d = ConflictMerge.resolve(base, changes, current, body, "server_version", alreadyRebased = false)

        d as Decision.Resend
        assertEquals(obj("""{"phone":"2","server_version":4}"""), d.body)
        assertEquals(setOf("email"), d.serverChanged)
    }

    @Test
    fun `overlapping changes park a conflict naming the clashing fields`() {
        val changes = obj("""{"email":"mine@x.io","phone":"2"}""")
        val current = obj("""{"id":"c1","name":"Ann","email":"theirs@x.io","phone":"1","notes":"vip","server_version":4}""")

        val d = ConflictMerge.resolve(base, changes, current, changes, "server_version", alreadyRebased = false)

        d as Decision.Conflict
        assertEquals(Reason.OVERLAP, d.reason)
        assertEquals(setOf("email"), d.fields)
        assertEquals(setOf("email", "notes"), d.serverChanged)
    }

    @Test
    fun `missing base is a conflict`() {
        val current = obj("""{"name":"Bob","server_version":4}""")
        val d = ConflictMerge.resolve(null, obj("""{"name":"Ann"}"""), current, obj("{}"), "server_version", false)
        assertEquals(Decision.Conflict(Reason.NO_BASE, emptySet(), emptySet()), d)
    }

    @Test
    fun `missing current or version is a conflict`() {
        val changes = obj("""{"phone":"2"}""")
        assertEquals(Reason.NO_CURRENT, (ConflictMerge.resolve(base, changes, null, changes, "server_version", false) as Decision.Conflict).reason)
        val noVersion = obj("""{"name":"Ann","server_version":null}""")
        assertEquals(Reason.NO_CURRENT, (ConflictMerge.resolve(base, changes, noVersion, changes, "server_version", false) as Decision.Conflict).reason)
    }

    @Test
    fun `a second 409 after a rebase is a conflict even when disjoint`() {
        val changes = obj("""{"phone":"2"}""")
        val current = obj("""{"id":"c1","name":"Ann","email":"b@x.io","phone":"1","server_version":5}""")
        val d = ConflictMerge.resolve(base, changes, current, changes, "server_version", alreadyRebased = true)
        d as Decision.Conflict
        assertEquals(Reason.REPEATED, d.reason)
        assertTrue(d.fields.isEmpty())
        assertEquals(setOf("email"), d.serverChanged)
    }

    @Test
    fun `nested objects and arrays compare as whole values`() {
        val b = obj("""{"address":{"city":"Oslo","zip":"0150"},"tags":["a","b"],"version":1}""")
        // Server reordered the tags; local changed one sub-field of the address → the whole address.
        val current = obj("""{"address":{"city":"Oslo","zip":"0150"},"tags":["b","a"],"version":2}""")
        val changes = obj("""{"address":{"city":"Bergen","zip":"0150"}}""")
        val d = ConflictMerge.resolve(b, changes, current, changes, "version", false)
        d as Decision.Resend
        assertEquals(setOf("tags"), d.serverChanged)

        val clash = obj("""{"tags":["a","b","c"]}""")
        val c = ConflictMerge.resolve(b, clash, current, clash, "version", false) as Decision.Conflict
        assertEquals(setOf("tags"), c.fields)
    }

    @Test
    fun `a local field the server row lacks is treated as a clash`() {
        val changes = obj("""{"end_at":"2025-02-01T10:00:00Z"}""")
        val current = obj("""{"status":"confirmed","version":2}""")
        val d = ConflictMerge.resolve(obj("""{"status":"confirmed","version":1}"""), changes, current, changes, "version", false)
        assertEquals(setOf("end_at"), (d as Decision.Conflict).fields)
    }

    @Test
    fun `value comparison normalises timestamps numbers and null`() {
        assertTrue(ConflictMerge.same(JsonPrimitive("2025-01-01T10:00:00Z"), JsonPrimitive("2025-01-01T11:00:00+01:00")))
        assertTrue(ConflictMerge.same(JsonPrimitive(5), JsonPrimitive("5")))
        assertTrue(ConflictMerge.same(JsonPrimitive(5), JsonPrimitive(5.0)))
        assertFalse(ConflictMerge.same(JsonPrimitive("0123"), JsonPrimitive("123")))
        assertTrue(ConflictMerge.same(null, Json.parseToJsonElement("null")))
        assertFalse(ConflictMerge.same(JsonPrimitive(""), null))
    }

    @Test
    fun `retry after a conflict keeps only the user's edits on the server version`() {
        // Form re-sent name and email unchanged; user changed phone; server changed email and phone.
        val changes = obj("""{"name":"Ann","email":"a@x.io","phone":"2"}""")
        val body = obj("""{"name":"Ann","email":"a@x.io","phone":"2","server_version":3}""")
        val current = obj("""{"name":"Ann","email":"b@x.io","phone":"9","server_version":4}""")

        val (nextChanges, nextBody) = ConflictMerge.forRetry(base, changes, current, body, "server_version")

        assertEquals(obj("""{"phone":"2"}"""), nextChanges)
        assertEquals(obj("""{"phone":"2","server_version":4}"""), nextBody)
        // The retried edit, now based on current, no longer clashes and resends as-is.
        val d = ConflictMerge.resolve(current, nextChanges, current, nextBody, "server_version", false) as Decision.Resend
        assertEquals(nextBody, d.body)
    }

    @Test
    fun `retry without a base keeps the whole edit and takes the server version`() {
        val body = obj("""{"name":"Ann","server_version":3}""")
        val (c, b) = ConflictMerge.forRetry(null, obj("""{"name":"Ann"}"""), obj("""{"server_version":7}"""), body, "server_version")
        assertEquals(obj("""{"name":"Ann"}"""), c)
        assertEquals(obj("""{"name":"Ann","server_version":7}"""), b)
    }

    @Test
    fun `local cache takes the server's non-clashing fields`() {
        val local = obj("""{"name":"Ann","email":"mine@x.io","notes":null}""")
        val current = obj("""{"name":"Ann","email":"theirs@x.io","notes":"vip"}""")
        assertEquals(
            obj("""{"name":"Ann","email":"mine@x.io","notes":"vip"}"""),
            ConflictMerge.applyServerFields(local, current, setOf("notes", "missing")),
        )
    }
}
