package com.avago.core.data.v2.repo

import com.avago.core.data.domain.CategoryDraft
import com.avago.core.data.domain.CategoryKind
import com.avago.core.data.domain.CustomFieldDefDraft
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldType
import com.avago.core.data.domain.PermissionSetDraft
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Stores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V2AdminRepositoryTest {
    private val business = V2Fixtures.business()
    private val active = V2ActiveStores().apply { publish(V2Stores(V2Fixtures.identity, business, V2Fixtures.chat())) }
    private val repo = V2AdminRepository(V2StoreAccess(active), V2CommandService(active) {})
    private val asset = "00000000-0000-4000-8000-0000000000a1"

    private suspend fun operations() = business.read { conn ->
        conn.query("SELECT operation FROM pending_commands ORDER BY causal_order").map { it["operation"] }
    }

    @Test
    fun `a new permission set shows with its keys and edits replace them`() = runTest {
        val id = repo.createPermissionSet(PermissionSetDraft(" Field techs ", null, setOf("work_orders.view", "assets.view")))
        val created = repo.observePermissionSets().first().single()
        assertEquals("Field techs", created.name)
        assertEquals(setOf("assets.view", "work_orders.view"), created.permissionKeys)

        repo.updatePermissionSet(id, PermissionSetDraft("Field techs", "Mobile crew", setOf("work_orders.view")))
        val edited = repo.observePermissionSets().first().single()
        assertEquals("Mobile crew", edited.description)
        assertEquals(setOf("work_orders.view"), edited.permissionKeys)

        repo.deletePermissionSet(id)
        assertTrue(repo.observePermissionSets().first().isEmpty())
        assertEquals(
            listOf("core.permission_sets.create", "core.permission_sets.update", "core.permission_sets.delete"),
            operations(),
        )
    }

    @Test
    fun `custom field values are read back in the field's wire text`() = runTest {
        repo.createCustomFieldDef(CustomFieldDefDraft("Hours", CustomFieldType.NUMBER, CustomFieldEntity.ASSET))
        repo.createCustomFieldDef(CustomFieldDefDraft("Leased", CustomFieldType.CHECKBOX, CustomFieldEntity.ASSET, sortOrder = 1))
        val (hours, leased) = repo.observeCustomFields(CustomFieldEntity.ASSET, asset).first().map { it.def }

        repo.setCustomFieldValue(hours, asset, "120.5")
        repo.setCustomFieldValue(leased, asset, "true")
        assertEquals(listOf("120.5", "true"), repo.observeCustomFields(CustomFieldEntity.ASSET, asset).first().map { it.value })

        repo.setCustomFieldValue(hours, asset, null)
        assertEquals(listOf(null, "true"), repo.observeCustomFields(CustomFieldEntity.ASSET, asset).first().map { it.value })
        assertTrue(repo.observeCustomFieldDefs(CustomFieldEntity.PRODUCT).first().isEmpty())
    }

    @Test
    fun `categories and saved views are kept per kind and dataset`() = runTest {
        val id = repo.createCategory(CategoryKind.ASSET, CategoryDraft("Pumps", defaultSlaHours = 8))
        assertEquals(listOf("Pumps"), repo.observeCategories(CategoryKind.ASSET).first().map { it.name })
        assertTrue(repo.observeCategories(CategoryKind.PRODUCT).first().isEmpty())
        repo.updateCategory(id, CategoryDraft("Pumps & valves", defaultSlaHours = 8))
        assertEquals(listOf("Pumps & valves"), repo.observeCategories(CategoryKind.ASSET).first().map { it.name })

        repo.createSavedView("work_orders", "Overdue", """{"horizon":"now"}""", shared = false)
        val view = repo.observeSavedViews("work_orders").first().single()
        assertTrue(view.mine)
        assertFalse(view.shared)
        assertTrue(repo.observeSavedViews("assets").first().isEmpty())
    }

    @Test
    fun `allowed follows the synced effective permissions`() = runTest {
        assertFalse(repo.observeAllowed("admin.manage_permission_sets").first())
        business.writeWithoutForeignKeys { tx ->
            tx.execute("INSERT INTO effective_permissions (permission_key, allowed) VALUES ('admin.manage_permission_sets', 1)")
        }
        assertTrue(repo.observeAllowed("admin.manage_permission_sets").first())
    }

    @Test
    fun `only active members ranked manager or above count as managers`() = runTest {
        assertFalse(repo.observeIsManager().first())
        suspend fun member(role: String, status: String) = business.writeWithoutForeignKeys { tx ->
            tx.execute("DELETE FROM account_members")
            tx.execute(
                "INSERT INTO account_members (user_id, version, role, status, created_at, updated_at) VALUES (?, 1, ?, ?, 0, 0)",
                listOf(V2Fixtures.identity.userId, role, status),
            )
        }
        member("dispatcher", "active")
        assertFalse(repo.observeIsManager().first())
        member("owner", "active")
        assertTrue(repo.observeIsManager().first())
        member("manager", "suspended")
        assertFalse(repo.observeIsManager().first())
    }
}
