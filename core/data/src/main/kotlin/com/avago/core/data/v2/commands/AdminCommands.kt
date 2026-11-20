package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Category
import com.avago.core.data.domain.CategoryDraft
import com.avago.core.data.domain.CategoryKind as DomainCategoryKind
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldDefDraft
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldType
import com.avago.core.data.domain.PermissionSet
import com.avago.core.data.domain.PermissionSetDraft
import com.avago.core.data.domain.SavedView
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate

/** Builders for account administration: permission sets, custom fields, categories, saved views. */
object AdminCommands {

    // ------------------------------------------------------------------ permission sets

    fun createPermissionSet(id: String, draft: PermissionSetDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val name = requireNotNull(draft.name.clean()) { "a permission set needs a name" }
        val keys = draft.permissionKeys.map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()
        val description = draft.description.clean()
        val ops = listOf(
            V2RowOp.upsert(
                PERMISSION_SETS, id,
                mapOf("name" to name, "description" to description, "is_system" to false) + created(nowMillis, userId),
            ),
        ) + keys.map { itemRow(id, it) }
        return CorePermissionSetsCreate(id = id, name = name, description = description, permissionKeys = keys.toList())
            .toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /** Null when nothing changed. The keys are replaced as a whole when they differ. */
    fun updatePermissionSet(stored: PermissionSet, draft: PermissionSetDraft, nowMillis: Long): V2NewCommand? {
        require(!stored.isSystem) { "system permission sets cannot be changed" }
        val d = V2UpdateDiff(listOf(CorePermissionSetsUpdate.Clear.DESCRIPTION))
        val name = d.text("name", stored.name, draft.name)
        val description = d.text("description", stored.description, draft.description)
        val keys = draft.permissionKeys.map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()
        val keysChanged = keys != stored.permissionKeys
        d.check()
        if (d.isEmpty && !keysChanged) return null
        val ops = mutableListOf(V2RowOp.upsert(PERMISSION_SETS, stored.id, d.overlay + updated(nowMillis)))
        if (keysChanged) {
            (stored.permissionKeys - keys).forEach { ops += V2RowOp.delete(PERMISSION_SET_ITEMS, itemKey(stored.id, it)) }
            (keys - stored.permissionKeys).forEach { ops += itemRow(stored.id, it) }
        }
        return CorePermissionSetsUpdate(
            id = stored.id,
            name = name,
            description = description,
            permissionKeys = if (keysChanged) keys.toList() else null,
            clear = d.clear,
        ).toNewCommand(baseVersion = stored.version, overlay = V2OverlayPatch(ops))
    }

    fun deletePermissionSet(stored: PermissionSet): V2NewCommand {
        require(!stored.isSystem) { "system permission sets cannot be deleted" }
        val ops = listOf(V2RowOp.delete(PERMISSION_SETS, stored.id)) +
            stored.permissionKeys.map { V2RowOp.delete(PERMISSION_SET_ITEMS, itemKey(stored.id, it)) }
        return CorePermissionSetsDelete(id = stored.id).toNewCommand(baseVersion = stored.version, overlay = V2OverlayPatch(ops))
    }

    fun assignPermissionSet(userId: String, permissionSetId: String?, nowMillis: Long): V2NewCommand =
        CorePermissionSetsAssign(userId = userId, permissionSetId = permissionSetId).toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(ACCOUNT_MEMBERS, mapOf("user_id" to userId), mapOf("permission_set_id" to permissionSetId) + updated(nowMillis)),
            ),
        )

    private fun itemKey(setId: String, key: String) = mapOf("permission_set_id" to setId, "permission_key" to key)

    private fun itemRow(setId: String, key: String) = V2RowOp.upsert(PERMISSION_SET_ITEMS, itemKey(setId, key), emptyMap())

    // ------------------------------------------------------------------ custom fields

    fun createCustomFieldDef(id: String, draft: CustomFieldDefDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val name = requireNotNull(draft.name.clean()) { "a custom field needs a name" }
        val options = cleanOptions(draft.type, draft.options)
        val default = draft.defaultValue.clean()?.let { requireNotNull(wireValue(draft.type, options, it)) { "invalid default value" } }
        require(draft.sortOrder >= 0) { "the sort order cannot be negative" }
        val payload = CoreAccountCustomFieldDefsCreate(
            id = id,
            name = name,
            fieldType = CfFieldType.entries.single { it.wire == draft.type.wire },
            entityType = CfEntityType.entries.single { it.wire == draft.entity.wire },
            options = options.ifEmpty { null },
            required = draft.required,
            defaultValue = default,
            sortOrder = draft.sortOrder,
        )
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    CUSTOM_FIELD_DEFS, id,
                    mapOf(
                        "name" to name, "field_type" to draft.type.wire, "entity_type" to draft.entity.wire,
                        "options" to options.takeIf { it.isNotEmpty() }?.let(::optionsJson), "required" to draft.required,
                        "default_value" to default, "sort_order" to draft.sortOrder.toLong(),
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }

    /** Null when nothing changed. The type and entity of a definition are fixed. */
    fun updateCustomFieldDef(stored: CustomFieldDef, draft: CustomFieldDefDraft, nowMillis: Long): V2NewCommand? {
        require(draft.sortOrder >= 0) { "the sort order cannot be negative" }
        val d = V2UpdateDiff(CoreAccountCustomFieldDefsUpdate.Clear.entries)
        val name = d.text("name", stored.name, draft.name)
        val options = cleanOptions(stored.type, draft.options)
        val newOptions = d.field("options", stored.options.ifEmpty { null }, options.ifEmpty { null }, toColumn = ::optionsJson)
        val default = draft.defaultValue.clean()?.let { requireNotNull(wireValue(stored.type, options, it)) { "invalid default value" } }
        val newDefault = d.text("default_value", stored.defaultValue, default)
        val required = d.field("required", stored.required, draft.required)
        val sortOrder = d.field("sort_order", stored.sortOrder, draft.sortOrder) { it.toLong() }
        d.check()
        if (d.isEmpty) return null
        return CoreAccountCustomFieldDefsUpdate(
            id = stored.id, name = name, options = newOptions, required = required, defaultValue = newDefault,
            sortOrder = sortOrder, clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert(CUSTOM_FIELD_DEFS, stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteCustomFieldDef(stored: CustomFieldDef): V2NewCommand =
        CoreAccountCustomFieldDefsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete(CUSTOM_FIELD_DEFS, stored.id)),
        )

    /**
     * `core.custom_field_values.set`. [storedRowId] is the record's value row when there is one
     * (otherwise [newRowId] keys the overlay row until the server's arrives); the overlay writes
     * the typed column of [def]'s type. A null or blank [value] clears it.
     */
    fun setCustomFieldValue(
        def: CustomFieldDef,
        entityId: String,
        value: String?,
        storedRowId: String?,
        newRowId: String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val wire = value.clean()?.let { requireNotNull(wireValue(def.type, def.options, it)) { "invalid ${def.type.wire} value" } }
        val entityColumn = when (def.entity) {
            CustomFieldEntity.ASSET -> "asset_id"
            CustomFieldEntity.PRODUCT -> "product_id"
            CustomFieldEntity.WORK_ORDER -> "work_order_id"
        }
        val typed = mapOf<String, Any?>(
            "value_text" to wire?.takeIf { def.type == CustomFieldType.TEXT || def.type == CustomFieldType.SELECT },
            "value_number" to wire?.takeIf { def.type == CustomFieldType.NUMBER },
            "value_date" to wire?.takeIf { def.type == CustomFieldType.DATE },
            "value_bool" to wire?.takeIf { def.type == CustomFieldType.CHECKBOX }?.let { it == "true" },
        )
        return CoreCustomFieldValuesSet(
            accountFieldId = def.id,
            entityType = CfEntityType.entries.single { it.wire == def.entity.wire },
            entityId = entityId,
            value = wire.orEmpty(),
        ).toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    CUSTOM_FIELD_VALUES, storedRowId ?: newRowId,
                    mapOf("account_field_id" to def.id, entityColumn to entityId) + typed +
                        if (storedRowId == null) created(nowMillis, userId) else updated(nowMillis),
                ),
            ),
        )
    }

    /** The wire text of [raw] for a field of [type], or null when it is not a valid value. */
    fun wireValue(type: CustomFieldType, options: List<String>, raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return when (type) {
            CustomFieldType.TEXT -> v
            CustomFieldType.NUMBER -> runCatching { BigDecimal(v).toPlainString() }.getOrNull()
            CustomFieldType.DATE -> runCatching { LocalDate.parse(v).toString() }.getOrNull()
            CustomFieldType.CHECKBOX -> when (v.lowercase()) {
                "true" -> "true"
                "false" -> "false"
                else -> null
            }
            CustomFieldType.SELECT -> v.takeIf { it in options }
        }
    }

    private fun cleanOptions(type: CustomFieldType, options: List<String>): List<String> {
        val cleaned = options.mapNotNull { it.clean() }.distinct()
        if (type == CustomFieldType.SELECT) require(cleaned.isNotEmpty()) { "a select field needs options" }
        return if (type == CustomFieldType.SELECT) cleaned else emptyList()
    }

    private fun optionsJson(options: List<String>): String = JsonArray(options.map(::JsonPrimitive)).toString()

    // ------------------------------------------------------------------ categories

    fun createCategory(id: String, kind: DomainCategoryKind, draft: CategoryDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val name = requireNotNull(draft.name.clean()) { "a category needs a name" }
        draft.defaultSlaHours?.let { require(it > 0) { "the SLA must be positive" } }
        val payload = MaintCategoriesCreate(
            id = id,
            kind = CategoryKind.entries.single { it.wire == kind.wire },
            name = name,
            parentId = draft.parentId,
            defaultPriority = draft.defaultPriority?.let { p -> WoPriority.entries.single { it.wire == p.wire } },
            defaultSlaHours = draft.defaultSlaHours,
            color = draft.color.clean(),
            icon = draft.icon.clean(),
        )
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    CATEGORIES, id,
                    mapOf(
                        "kind" to kind.wire, "name" to name, "parent_id" to draft.parentId,
                        "default_priority" to draft.defaultPriority?.wire, "default_sla_hours" to draft.defaultSlaHours?.toLong(),
                        "color" to payload.color, "icon" to payload.icon,
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }

    /** Null when nothing changed. System categories keep their name. */
    fun updateCategory(stored: Category, draft: CategoryDraft, nowMillis: Long): V2NewCommand? {
        require(draft.parentId != stored.id) { "a category cannot be its own parent" }
        draft.defaultSlaHours?.let { require(it > 0) { "the SLA must be positive" } }
        val d = V2UpdateDiff(MaintCategoriesUpdate.Clear.entries)
        val name = d.text("name", stored.name, draft.name)
        require(name == null || stored.systemCode == null) { "system categories cannot be renamed" }
        val parentId = d.text("parent_id", stored.parentId, draft.parentId)
        val priority = d.field("default_priority", stored.defaultPriority, draft.defaultPriority?.wire)
        val sla = d.field("default_sla_hours", stored.defaultSlaHours, draft.defaultSlaHours) { it.toLong() }
        val color = d.text("color", stored.color, draft.color)
        val icon = d.text("icon", stored.icon, draft.icon)
        d.check()
        if (d.isEmpty) return null
        return MaintCategoriesUpdate(
            id = stored.id,
            name = name,
            parentId = parentId,
            defaultPriority = priority?.let { p -> WoPriority.entries.single { it.wire == p } },
            defaultSlaHours = sla,
            color = color,
            icon = icon,
            clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert(CATEGORIES, stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteCategory(stored: Category): V2NewCommand {
        require(stored.systemCode == null) { "system categories cannot be deleted" }
        return MaintCategoriesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete(CATEGORIES, stored.id)),
        )
    }

    // ------------------------------------------------------------------ saved views

    fun createSavedView(id: String, dataset: String, name: String, spec: String, shared: Boolean, nowMillis: Long, userId: String): V2NewCommand {
        val cleanName = requireNotNull(name.clean()) { "a saved view needs a name" }
        val set = requireNotNull(dataset.clean()) { "a saved view needs a dataset" }
        val json = parseSpec(spec)
        val visibility = if (shared) SavedViewVisibility.ACCOUNT else SavedViewVisibility.PRIVATE
        return SpecialistSavedViewsCreate(id = id, dataset = set, name = cleanName, spec = json, visibility = visibility).toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    SAVED_VIEWS, id,
                    mapOf(
                        "owner_user_id" to userId, "dataset" to set, "name" to cleanName, "spec" to json.toString(),
                        "visibility" to visibility.wire,
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }

    /** Null when nothing changed. */
    fun updateSavedView(stored: SavedView, name: String, spec: String, shared: Boolean, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff.none()
        val newName = d.text("name", stored.name, name)
        val json = parseSpec(spec)
        val newSpec = d.field("spec", parseSpec(stored.spec), json) { it.toString() }
        val visibility = d.field("visibility", stored.shared, shared) { if (it) SavedViewVisibility.ACCOUNT.wire else SavedViewVisibility.PRIVATE.wire }
        d.check()
        if (d.isEmpty) return null
        return SpecialistSavedViewsUpdate(
            id = stored.id,
            name = newName,
            spec = newSpec,
            visibility = visibility?.let { if (it) SavedViewVisibility.ACCOUNT else SavedViewVisibility.PRIVATE },
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert(SAVED_VIEWS, stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteSavedView(stored: SavedView): V2NewCommand =
        SpecialistSavedViewsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete(SAVED_VIEWS, stored.id)),
        )

    private fun parseSpec(spec: String): JsonObject =
        requireNotNull(runCatching { Json.parseToJsonElement(spec) as? JsonObject }.getOrNull()) { "a saved view spec must be a JSON object" }

    private const val PERMISSION_SETS = "permission_sets"
    private const val PERMISSION_SET_ITEMS = "permission_set_items"
    private const val ACCOUNT_MEMBERS = "account_members"
    private const val CUSTOM_FIELD_DEFS = "account_custom_field_defs"
    private const val CUSTOM_FIELD_VALUES = "custom_field_values"
    private const val CATEGORIES = "categories"
    private const val SAVED_VIEWS = "saved_views"
}
