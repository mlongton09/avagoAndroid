package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Category
import com.avago.core.data.domain.CategoryDraft
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldDefDraft
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldType
import com.avago.core.data.domain.FieldNotClearableException
import com.avago.core.data.domain.PermissionSet
import com.avago.core.data.domain.PermissionSetDraft
import com.avago.core.data.domain.SavedView
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminCommandsTest {
    private val set = PermissionSet("s1", 3, "Techs", "Field", isSystem = false, permissionKeys = sortedSetOf("a.x", "b.y"), memberCount = 0)

    private fun ops(command: com.avago.core.data.v2.V2NewCommand) = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops
    private fun keys(payload: kotlinx.serialization.json.JsonElement?) = payload!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `a permission set update replaces the keys and moves the items in the overlay`() {
        val command = AdminCommands.updatePermissionSet(set, PermissionSetDraft("Techs", null, setOf("b.y", "c.z")), 9)!!
        val payload = command.payload.jsonObject
        assertEquals(3L, command.baseVersion)
        assertEquals(listOf("b.y", "c.z"), keys(payload["set"]!!.jsonObject["permission_keys"]))
        assertEquals(listOf("description"), keys(payload["clear"]))
        val itemOps = ops(command).filter { it.table == "permission_set_items" }
        assertEquals(
            setOf(V2RowOp.Kind.DELETE to "a.x", V2RowOp.Kind.UPSERT to "c.z"),
            itemOps.map { it.kind to it.key["permission_key"] }.toSet(),
        )
    }

    @Test
    fun `unchanged permission sets queue nothing and system sets are fixed`() {
        assertNull(AdminCommands.updatePermissionSet(set, PermissionSetDraft("Techs ", "Field", setOf("a.x", "b.y")), 1))
        val system = set.copy(isSystem = true)
        assertThrows(IllegalArgumentException::class.java) { AdminCommands.updatePermissionSet(system, PermissionSetDraft("X"), 1) }
        assertThrows(IllegalArgumentException::class.java) { AdminCommands.deletePermissionSet(system) }
    }

    @Test
    fun `custom field values are sent as wire text and written to the typed column`() {
        val def = CustomFieldDef("f1", 1, "Hours", CustomFieldType.NUMBER, CustomFieldEntity.ASSET, emptyList(), false, null, 0)
        val command = AdminCommands.setCustomFieldValue(def, "a1", " 12.50 ", null, "row", 5, null)
        val payload = command.payload.jsonObject
        assertEquals("12.50", payload["value"]!!.jsonPrimitive.content)
        assertEquals("asset", payload["entity_type"]!!.jsonPrimitive.content)
        val op = ops(command).single()
        assertEquals(mapOf("id" to "row"), op.key)
        assertEquals("12.50", op.set["value_number"])
        assertEquals("a1", op.set["asset_id"])

        val cleared = AdminCommands.setCustomFieldValue(def, "a1", "", "stored", "row", 6, null)
        assertEquals("", cleared.payload.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals(mapOf("id" to "stored"), ops(cleared).single().key)
        assertNull(ops(cleared).single().set["value_number"])

        assertThrows(IllegalArgumentException::class.java) { AdminCommands.setCustomFieldValue(def, "a1", "twelve", null, "r", 1, null) }
        val select = def.copy(type = CustomFieldType.SELECT, options = listOf("A", "B"))
        assertThrows(IllegalArgumentException::class.java) { AdminCommands.setCustomFieldValue(select, "a1", "C", null, "r", 1, null) }
    }

    @Test
    fun `custom field definitions need options for selects and clear emptied fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdminCommands.createCustomFieldDef("f", CustomFieldDefDraft("Size", CustomFieldType.SELECT, CustomFieldEntity.PRODUCT), 1, null)
        }
        val stored = CustomFieldDef("f1", 2, "Size", CustomFieldType.SELECT, CustomFieldEntity.PRODUCT, listOf("S", "M"), true, "S", 0)
        val command = AdminCommands.updateCustomFieldDef(
            stored, CustomFieldDefDraft("Size", CustomFieldType.SELECT, CustomFieldEntity.PRODUCT, listOf("S", "M", "L"), true, null, 0), 1,
        )!!
        val payload = command.payload.jsonObject
        assertEquals(listOf("S", "M", "L"), keys(payload["set"]!!.jsonObject["options"]))
        assertEquals(listOf("default_value"), keys(payload["clear"]))
        assertEquals("[\"S\",\"M\",\"L\"]", ops(command).single().set["options"])
    }

    @Test
    fun `categories clear emptied fields and system categories keep their name`() {
        val stored = Category("c1", 4, "work_order", "Electrical", null, "#ff0000", null, "high", 24, null)
        val command = AdminCommands.updateCategory(stored, CategoryDraft("Electrical", null, WorkOrderPriority.LOW, null, "#ff0000"), 1)!!
        val payload = command.payload.jsonObject
        assertEquals("low", payload["set"]!!.jsonObject["default_priority"]!!.jsonPrimitive.content)
        assertEquals(listOf("default_sla_hours"), keys(payload["clear"]))
        assertThrows(IllegalArgumentException::class.java) {
            AdminCommands.updateCategory(stored.copy(systemCode = "electrical"), CategoryDraft("Power"), 1)
        }
        assertThrows(IllegalArgumentException::class.java) { AdminCommands.deleteCategory(stored.copy(systemCode = "electrical")) }
    }

    @Test
    fun `saved views send the spec as JSON and visibility as a code`() {
        val created = AdminCommands.createSavedView("v1", "work_orders", " Mine ", """{"scope":"mine"}""", true, 1, "u1")
        val payload = created.payload.jsonObject
        assertEquals("Mine", payload["name"]!!.jsonPrimitive.content)
        assertEquals("mine", payload["spec"]!!.jsonObject["scope"]!!.jsonPrimitive.content)
        assertEquals("account", payload["visibility"]!!.jsonPrimitive.content)
        assertEquals("u1", ops(created).single().set["owner_user_id"])

        val stored = SavedView("v1", 1, "work_orders", "Mine", """{"scope":"mine"}""", shared = true, mine = true)
        assertNull(AdminCommands.updateSavedView(stored, "Mine", """{ "scope": "mine" }""", true, 2))
        assertThrows(IllegalArgumentException::class.java) { AdminCommands.createSavedView("v", "wo", "X", "[]", false, 1, "u1") }
        assertThrows(FieldNotClearableException::class.java) { AdminCommands.updateSavedView(stored, " ", stored.spec, true, 2) }
    }

    @Test
    fun `assigning a permission set updates the member row`() {
        val op = ops(AdminCommands.assignPermissionSet("u2", null, 3)).single()
        assertEquals(mapOf("user_id" to "u2"), op.key)
        assertTrue(op.set.containsKey("permission_set_id") && op.set["permission_set_id"] == null)
    }
}
