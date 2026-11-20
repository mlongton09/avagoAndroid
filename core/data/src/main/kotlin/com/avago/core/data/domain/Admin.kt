package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow

/** A named set of permission keys members can be given (`permission_sets` + `permission_set_items`). */
data class PermissionSet(
    val id: String,
    val version: Long,
    val name: String,
    val description: String?,
    val isSystem: Boolean,
    val permissionKeys: Set<String>,
    /** Members holding the set. */
    val memberCount: Int,
)

data class PermissionSetDraft(
    val name: String,
    val description: String? = null,
    val permissionKeys: Set<String> = emptySet(),
)

/** An account member and the permission set they hold. */
data class AccountMember(
    val userId: String,
    /** The member's worker name when known. */
    val displayName: String?,
    val role: String,
    val status: String,
    val permissionSetId: String?,
)

enum class CustomFieldType(val wire: String) {
    TEXT("text"), NUMBER("number"), DATE("date"), SELECT("select"), CHECKBOX("checkbox");

    companion object {
        fun fromWire(raw: String?): CustomFieldType? = entries.firstOrNull { it.wire == raw }
    }
}

/** The kind of record a custom field belongs to. */
enum class CustomFieldEntity(val wire: String) {
    ASSET("asset"), PRODUCT("product"), WORK_ORDER("work_order");

    companion object {
        fun fromWire(raw: String?): CustomFieldEntity? = entries.firstOrNull { it.wire == raw }
    }
}

/** An account's custom field definition (`account_custom_field_defs`). */
data class CustomFieldDef(
    val id: String,
    val version: Long,
    val name: String,
    val type: CustomFieldType,
    val entity: CustomFieldEntity,
    val options: List<String>,
    val required: Boolean,
    val defaultValue: String?,
    val sortOrder: Int,
)

data class CustomFieldDefDraft(
    val name: String,
    val type: CustomFieldType,
    val entity: CustomFieldEntity,
    val options: List<String> = emptyList(),
    val required: Boolean = false,
    val defaultValue: String? = null,
    val sortOrder: Int = 0,
)

/**
 * A custom field with the record's value, in the field type's wire text (number, `YYYY-MM-DD`,
 * `true`/`false`, a select option); null when unset.
 */
data class CustomFieldEntry(val def: CustomFieldDef, val value: String?)

enum class CategoryKind(val wire: String) {
    WORK_ORDER("work_order"), PRODUCT("product"), ASSET("asset"), REQUEST("request");

    companion object {
        fun fromWire(raw: String?): CategoryKind? = entries.firstOrNull { it.wire == raw }
    }
}

data class CategoryDraft(
    val name: String,
    val parentId: String? = null,
    val defaultPriority: WorkOrderPriority? = null,
    val defaultSlaHours: Int? = null,
    val color: String? = null,
    val icon: String? = null,
)

/** A saved list filter (`saved_views`); [spec] is the list's own filter JSON. */
data class SavedView(
    val id: String,
    val version: Long,
    val dataset: String,
    val name: String,
    val spec: String,
    val shared: Boolean,
    /** True when the caller owns the view (only then, or as a manager, can it be changed). */
    val mine: Boolean,
)

/** Account administration: permission sets, custom fields, categories and saved views. */
interface AdminRepository {
    /** True when the signed-in member holds [permissionKey] (the synced effective permissions). */
    fun observeAllowed(permissionKey: String): Flow<Boolean>

    /** True when the signed-in member's role ranks manager or above (needed to share saved views with the account). */
    fun observeIsManager(): Flow<Boolean>

    fun observePermissionSets(): Flow<List<PermissionSet>>

    suspend fun createPermissionSet(draft: PermissionSetDraft): String

    suspend fun updatePermissionSet(id: String, draft: PermissionSetDraft)

    suspend fun deletePermissionSet(id: String)

    fun observeMembers(): Flow<List<AccountMember>>

    /** Gives [userId] the permission set [permissionSetId], or takes it away with null. */
    suspend fun assignPermissionSet(userId: String, permissionSetId: String?)

    fun observeCustomFieldDefs(entity: CustomFieldEntity? = null): Flow<List<CustomFieldDef>>

    suspend fun createCustomFieldDef(draft: CustomFieldDefDraft): String

    /** The type and entity of a definition are fixed; only the other fields of [draft] are applied. */
    suspend fun updateCustomFieldDef(id: String, draft: CustomFieldDefDraft)

    suspend fun deleteCustomFieldDef(id: String)

    /** The account's fields for [entity] with the values of record [entityId]. */
    fun observeCustomFields(entity: CustomFieldEntity, entityId: String): Flow<List<CustomFieldEntry>>

    /** Sets a field's value on a record; a null or blank [value] clears it. */
    suspend fun setCustomFieldValue(def: CustomFieldDef, entityId: String, value: String?)

    fun observeCategories(kind: CategoryKind): Flow<List<Category>>

    suspend fun createCategory(kind: CategoryKind, draft: CategoryDraft): String

    suspend fun updateCategory(id: String, draft: CategoryDraft)

    suspend fun deleteCategory(id: String)

    fun observeSavedViews(dataset: String): Flow<List<SavedView>>

    suspend fun createSavedView(dataset: String, name: String, spec: String, shared: Boolean): String

    suspend fun updateSavedView(id: String, name: String, spec: String, shared: Boolean)

    suspend fun deleteSavedView(id: String)
}
