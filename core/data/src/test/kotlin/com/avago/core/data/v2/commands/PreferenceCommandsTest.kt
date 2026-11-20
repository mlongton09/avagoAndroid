package com.avago.core.data.v2.commands

import com.avago.core.data.domain.NotificationChannel
import com.avago.core.data.domain.UserPreferenceChange
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreferenceCommandsTest {

    @Test
    fun `set values are sent as fields and cleared ones in reset`() {
        val command = PreferenceCommands.updateUserPreferences(
            "u1",
            listOf(
                UserPreferenceChange.Currency(" eur "),
                UserPreferenceChange.DistanceUnit(null),
                UserPreferenceChange.DisableQuotes(true),
            ),
            10_000,
        )
        val payload = command.payload.jsonObject
        assertEquals("core.user_preferences.update", command.operation)
        assertNull(command.baseVersion)
        assertEquals("EUR", payload["currency"]!!.jsonPrimitive.content)
        assertTrue(payload["disable_quotes"]!!.jsonPrimitive.boolean)
        assertEquals(listOf("distance_unit"), payload["reset"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue("distance_unit" !in payload)

        val op = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single()
        assertEquals("user_preferences", op.table)
        assertEquals(mapOf("user_id" to "u1"), op.key)
        assertEquals("EUR", op.set["currency"])
        assertTrue(op.set.containsKey("distance_unit") && op.set["distance_unit"] == null)
        assertEquals(10_000L, op.set["updated_at"])
    }

    @Test
    fun `units must use the server codes and a field is changed once`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreferenceCommands.updateUserPreferences("u1", listOf(UserPreferenceChange.DistanceUnit("mi")), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreferenceCommands.updateUserPreferences(
                "u1", listOf(UserPreferenceChange.Currency("USD"), UserPreferenceChange.Currency("EUR")), 1,
            )
        }
    }

    @Test
    fun `a notification switch is keyed by user, event and channel`() {
        val command = PreferenceCommands.setNotification("u1", " rental.overdue ", NotificationChannel.PUSH, false, 5)
        val payload = command.payload.jsonObject
        assertEquals("specialist.notification_preferences.set", command.operation)
        assertEquals("rental.overdue", payload["event_key"]!!.jsonPrimitive.content)
        assertEquals("push", payload["channel"]!!.jsonPrimitive.content)
        assertEquals(false, payload["enabled"]!!.jsonPrimitive.boolean)
        val op = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single()
        assertEquals(mapOf("user_id" to "u1", "event_key" to "rental.overdue", "channel" to "push"), op.key)
    }
}
