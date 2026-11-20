package com.avago.core.data.v2.repo

import com.avago.core.data.domain.AccountMember
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.Category
import com.avago.core.data.domain.CategoryDraft
import com.avago.core.data.domain.CategoryKind
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldDefDraft
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldEntry
import com.avago.core.data.domain.CustomFieldType
import com.avago.core.data.domain.PermissionSet
import com.avago.core.data.domain.PermissionSetDraft
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.SavedView
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.commands.AdminCommands
import com.avago.core.data.v2.long
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2AdminRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : AdminRepository {

    override fun observeAllowed(permissionKey: String): Flow<Boolean> =
        store.observe(setOf(EFFECTIVE_PERMISSIONS), false) { conn -> allowed(conn, permissionKey) }

    override fun observeIsManager(): Flow<Boolean> =
        store.observe(setOf(ACCOUNT_MEMBERS), false) { conn -> isManager(conn, commands.currentUserId()) }

    // ------------------------------------------------------------------ permission sets

    override fun observePermissionSets(): Flow<List<PermissionSet>> =
        store.observe(setOf(PERMISSION_SETS, PERMISSION_SET_ITEMS, ACCOUNT_MEMBERS), emptyList()) { conn -> permissionSets(conn) }

    override suspend fun createPermissionSet(draft: PermissionSetDraft): String {
        val id = UUID.randomUUID().toString()
        commands.submit(AdminCommands.createPermissionSet(id, draft, System.currentTimeMillis(), commands.currentUserId()))
        return id
    }

    override suspend fun updatePermissionSet(id: String, draft: PermissionSetDraft) {
        val stored = store.read(null) { conn -> permissionSets(conn).firstOrNull { it.id == id } }
            ?: throw RecordNotFoundException("permission set", id)
        AdminCommands.updatePermissionSet(stored, draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deletePermissionSet(id: String) {
        val stored = store.read(null) { conn -> permissionSets(conn).firstOrNull { it.id == id } }
            ?: throw RecordNotFoundException("permission set", id)
        commands.submit(AdminCommands.deletePermissionSet(stored))
    }

    override fun observeMembers(): Flow<List<AccountMember>> =
        store.observe(setOf(ACCOUNT_MEMBERS, WORKERS), emptyList()) { conn -> members(conn) }

    override suspend fun assignPermissionSet(userId: String, permissionSetId: String?) {
        commands.submit(AdminCommands.assignPermissionSet(userId, permissionSetId, System.currentTimeMillis()))
    }

    // ------------------------------------------------------------------ custom fields

    override fun observeCustomFieldDefs(entity: CustomFieldEntity?): Flow<List<CustomFieldDef>> =
        store.observe(setOf(CUSTOM_FIELD_DEFS), emptyList()) { conn -> customFieldDefs(conn, entity) }

    override suspend fun createCustomFieldDef(draft: CustomFieldDefDraft): String {
        val id = UUID.randomUUID().toString()
        commands.submit(AdminCommands.createCustomFieldDef(id, draft, System.currentTimeMillis(), commands.currentUserId()))
        return id
    }

    override suspend fun updateCustomFieldDef(id: String, draft: CustomFieldDefDraft) {
        val stored = customFieldDef(id)
        AdminCommands.updateCustomFieldDef(stored, draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteCustomFieldDef(id: String) {
        commands.submit(AdminCommands.deleteCustomFieldDef(customFieldDef(id)))
    }

    override fun observeCustomFields(entity: CustomFieldEntity, entityId: String): Flow<List<CustomFieldEntry>> =
        store.observe(setOf(CUSTOM_FIELD_DEFS, CUSTOM_FIELD_VALUES), emptyList()) { conn -> customFields(conn, entity, entityId) }

    override suspend fun setCustomFieldValue(def: CustomFieldDef, entityId: String, value: String?) {
        val storedRowId = store.read(null) { conn -> valueRow(conn, def, entityId)?.str("id") }
        commands.submit(
            AdminCommands.setCustomFieldValue(
                def, entityId, value, storedRowId, UUID.randomUUID().toString(), System.currentTimeMillis(), commands.currentUserId(),
            ),
        )
    }

    private suspend fun customFieldDef(id: String): CustomFieldDef =
        store.read(null) { conn -> customFieldDefs(conn, null).firstOrNull { it.id == id } }
            ?: throw RecordNotFoundException("custom field", id)

    // ------------------------------------------------------------------ categories

    override fun observeCategories(kind: CategoryKind): Flow<List<Category>> =
        store.observe(setOf(CATEGORIES), emptyList()) { conn -> V2InventoryRepository.categories(conn, kind.wire) }

    override suspend fun createCategory(kind: CategoryKind, draft: CategoryDraft): String {
        val id = UUID.randomUUID().toString()
        commands.submit(AdminCommands.createCategory(id, kind, draft, System.currentTimeMillis(), commands.currentUserId()))
        return id
    }

    override suspend fun updateCategory(id: String, draft: CategoryDraft) {
        AdminCommands.updateCategory(category(id), draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteCategory(id: String) {
        commands.submit(AdminCommands.deleteCategory(category(id)))
    }

    private suspend fun category(id: String): Category =
        store.read(null) { conn ->
            V2OverlayMerge.query(conn, CATEGORIES, "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.str("kind")
                ?.let { kind -> V2InventoryRepository.categories(conn, kind).firstOrNull { it.id == id } }
        } ?: throw RecordNotFoundException("category", id)

    // ------------------------------------------------------------------ saved views

    override fun observeSavedViews(dataset: String): Flow<List<SavedView>> =
        store.observe(setOf(SAVED_VIEWS), emptyList()) { conn -> savedViews(conn, dataset, commands.currentUserId()) }

    override suspend fun createSavedView(dataset: String, name: String, spec: String, shared: Boolean): String {
        val userId = commands.currentUserId() ?: throw StoreNotReadyException()
        val id = UUID.randomUUID().toString()
        commands.submit(AdminCommands.createSavedView(id, dataset, name, spec, shared, System.currentTimeMillis(), userId))
        return id
    }

    override suspend fun updateSavedView(id: String, name: String, spec: String, shared: Boolean) {
        AdminCommands.updateSavedView(savedView(id), name, spec, shared, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteSavedView(id: String) {
        commands.submit(AdminCommands.deleteSavedView(savedView(id)))
    }

    private suspend fun savedView(id: String): SavedView =
        store.read(null) { conn ->
            V2OverlayMerge.query(conn, SAVED_VIEWS, "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()
                ?.let { savedView(it, commands.currentUserId()) }
        } ?: throw RecordNotFoundException("saved view", id)

    companion object {
        private const val EFFECTIVE_PERMISSIONS = "effective_permissions"
        private const val PERMISSION_SETS = "permission_sets"
        private const val PERMISSION_SET_ITEMS = "permission_set_items"
        private const val ACCOUNT_MEMBERS = "account_members"
        private const val WORKERS = "workers"
        private const val CUSTOM_FIELD_DEFS = "account_custom_field_defs"
        private const val CUSTOM_FIELD_VALUES = "custom_field_values"
        private const val CATEGORIES = "categories"
        private const val SAVED_VIEWS = "saved_views"

        fun allowed(conn: V2Connection, permissionKey: String): Boolean =
            conn.query("SELECT allowed FROM effective_permissions WHERE permission_key = ?", listOf(permissionKey))
                .firstOrNull()?.longOrNull("allowed") == 1L

        /** Roles ranked manager or above (the server's `role_rank >= manager`, legacy `owner` included). */
        private val MANAGER_ROLES = setOf("manager", "admin", "root", "owner")

        fun isManager(conn: V2Connection, userId: String?): Boolean {
            userId ?: return false
            return V2OverlayMerge.query(conn, ACCOUNT_MEMBERS)
                .any { it.str("user_id") == userId && it.str("status") == "active" && it.str("role") in MANAGER_ROLES }
        }

        fun permissionSets(conn: V2Connection): List<PermissionSet> {
            val keys = V2OverlayMerge.query(conn, PERMISSION_SET_ITEMS)
                .groupBy({ it.str("permission_set_id") }, { it.str("permission_key") })
            val holders = V2OverlayMerge.query(conn, ACCOUNT_MEMBERS)
                .filter { it.str("status") != "removed" }
                .mapNotNull { it.strOrNull("permission_set_id") }
                .groupingBy { it }.eachCount()
            return V2OverlayMerge.query(conn, PERMISSION_SETS)
                .map { r ->
                    val id = r.str("id")
                    PermissionSet(
                        id = id,
                        version = r.long("version"),
                        name = r.str("name"),
                        description = r.strOrNull("description"),
                        isSystem = r.longOrNull("is_system") == 1L,
                        permissionKeys = keys[id].orEmpty().toSortedSet(),
                        memberCount = holders[id] ?: 0,
                    )
                }
                .sortedWith(compareByDescending<PermissionSet> { it.isSystem }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        fun members(conn: V2Connection): List<AccountMember> {
            val names = V2OverlayMerge.query(conn, WORKERS)
                .mapNotNull { w -> w.strOrNull("user_id")?.let { it to w.strOrNull("display_name") } }
                .toMap()
            return V2OverlayMerge.query(conn, ACCOUNT_MEMBERS)
                .filter { it.str("status") != "removed" }
                .map { r ->
                    val userId = r.str("user_id")
                    AccountMember(
                        userId = userId,
                        displayName = names[userId],
                        role = r.str("role"),
                        status = r.str("status"),
                        permissionSetId = r.strOrNull("permission_set_id"),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName ?: it.userId })
        }

        fun customFieldDefs(conn: V2Connection, entity: CustomFieldEntity?): List<CustomFieldDef> {
            val rows = if (entity == null) {
                V2OverlayMerge.query(conn, CUSTOM_FIELD_DEFS)
            } else {
                V2OverlayMerge.query(conn, CUSTOM_FIELD_DEFS, "entity_type = ?", listOf(entity.wire)) { it["entity_type"] == entity.wire }
            }
            return rows.mapNotNull(::customFieldDef).sortedWith(compareBy({ it.entity }, { it.sortOrder }, { it.name.lowercase() }))
        }

        private fun customFieldDef(r: V2Row): CustomFieldDef? {
            val type = CustomFieldType.fromWire(r.strOrNull("field_type")) ?: return null
            val entity = CustomFieldEntity.fromWire(r.strOrNull("entity_type")) ?: return null
            return CustomFieldDef(
                id = r.str("id"),
                version = r.long("version"),
                name = r.str("name"),
                type = type,
                entity = entity,
                options = r.strOrNull("options")?.let(::parseOptions).orEmpty(),
                required = r.longOrNull("required") == 1L,
                defaultValue = r.strOrNull("default_value"),
                sortOrder = r.longOrNull("sort_order")?.toInt() ?: 0,
            )
        }

        private fun parseOptions(json: String): List<String> =
            runCatching { (Json.parseToJsonElement(json) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content } }
                .getOrDefault(emptyList())

        fun customFields(conn: V2Connection, entity: CustomFieldEntity, entityId: String): List<CustomFieldEntry> =
            customFieldDefs(conn, entity).map { def -> CustomFieldEntry(def, valueRow(conn, def, entityId)?.let { valueText(def.type, it) }) }

        private fun entityColumn(entity: CustomFieldEntity) = when (entity) {
            CustomFieldEntity.ASSET -> "asset_id"
            CustomFieldEntity.PRODUCT -> "product_id"
            CustomFieldEntity.WORK_ORDER -> "work_order_id"
        }

        internal fun valueRow(conn: V2Connection, def: CustomFieldDef, entityId: String): V2Row? {
            val column = entityColumn(def.entity)
            return V2OverlayMerge.query(conn, CUSTOM_FIELD_VALUES, "account_field_id = ? AND $column = ?", listOf(def.id, entityId)) {
                it["account_field_id"] == def.id && it[column] == entityId
            }.firstOrNull()
        }

        private fun valueText(type: CustomFieldType, r: V2Row): String? = when (type) {
            CustomFieldType.TEXT, CustomFieldType.SELECT -> r.strOrNull("value_text")
            CustomFieldType.NUMBER -> r["value_number"]?.toString()?.let { runCatching { BigDecimal(it).toPlainString() }.getOrDefault(it) }
            CustomFieldType.DATE -> r.strOrNull("value_date")
            CustomFieldType.CHECKBOX -> r.longOrNull("value_bool")?.let { (it == 1L).toString() }
        }

        fun savedViews(conn: V2Connection, dataset: String, userId: String?): List<SavedView> =
            V2OverlayMerge.query(conn, SAVED_VIEWS, "dataset = ?", listOf(dataset)) { it["dataset"] == dataset }
                .map { savedView(it, userId) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, SavedView::name))

        private fun savedView(r: V2Row, userId: String?) = SavedView(
            id = r.str("id"),
            version = r.long("version"),
            dataset = r.str("dataset"),
            name = r.str("name"),
            spec = r.str("spec"),
            shared = r.str("visibility") == "account",
            mine = userId != null && r.strOrNull("owner_user_id") == userId,
        )
    }
}
