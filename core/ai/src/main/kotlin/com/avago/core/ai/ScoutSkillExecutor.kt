package com.avago.core.ai

import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.AssetDraft
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.ProductDraft
import com.avago.core.data.domain.RecurrenceRule
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.domain.WorkOrderDraft
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.domain.toQtyOrNull
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HITL-off execution path for Android Scout skills.
 *
 * When the user has disabled Human-in-the-Loop in preferences, [ScoutViewModel] calls
 * [executeIfPossible] instead of routing to a form. The executor makes the same repository call
 * the form's Save handler makes, so the change is applied optimistically to the v2 store and
 * shipped as a catalog command by the outbox.
 *
 * Returns `true` when execution succeeded, `false` to signal "fall back to HITL form" (skill not
 * supported here, a required field was missing, or the target record does not exist).
 */
@Singleton
class ScoutSkillExecutor @Inject constructor(
    private val identityManager: IdentityManager,
    private val activities: ActivityRepository,
    private val workOrders: WorkOrderRepository,
    private val assets: AssetRepository,
    private val inventory: InventoryRepository,
) {

    suspend fun executeIfPossible(skillName: String, fields: Map<String, String?>): Boolean {
        if (identityManager.getActiveAccountId() == null) return false
        return try {
            when (skillName) {
                "log-entry-create", "fuel-log", "inspection-from-voice" -> executeAddLogEntry(fields)
                "work-order-create" -> executeAddWorkOrder(fields)
                "reschedule" -> executeEditWorkOrder(fields)
                "asset-create" -> executeAddAsset(fields)
                "work-order-action" -> executeWorkOrderAction(fields)
                "work-order-assign" -> executeWorkOrderAssign(fields)
                "inventory-receive" -> executeStockMovement(fields, MovementKind.RECEIPT)
                "inventory-use" -> executeStockMovement(fields, MovementKind.ISSUE)
                "parts-catalog-add" -> executeAddPart(fields)
                "work-order-recurrence" -> executeSetRecurrence(fields)
                else -> false
            }
        } catch (e: Exception) {
            Timber.w(e, "ScoutSkillExecutor: exception executing '$skillName'")
            false
        }
    }

    private suspend fun executeAddLogEntry(fields: Map<String, String?>): Boolean {
        val title = fields.text("title") ?: return false
        val assetId = fields.text("asset_id") ?: return false
        val category = fields.text("category") ?: "service"
        val currency = fields.text("currency")
        val draft = ActivityDraft(
            assetId = assetId,
            type = ActivityType.forCategory(category),
            categoryLabel = category,
            title = title,
            occurredAt = parseDate(fields["log_date"]) ?: System.currentTimeMillis(),
            meterValue = fields.qty("meter") ?: fields.qty("odometer"),
            performedByUserId = fields.text("performed_by_user_id"),
            performedByName = fields.text("performed_by"),
            currency = currency,
            notes = fields.text("notes"),
            attributes = fields.text("attributes"),
        )
        val lines = fields.qty("cost")?.let { listOf(CostLineDraft(kind = CostLineType.MISC, description = title, unitCost = it)) }
        activities.create(draft, lines.orEmpty())
        Timber.d("ScoutSkillExecutor: logged '$title'")
        return true
    }

    private suspend fun executeAddWorkOrder(fields: Map<String, String?>): Boolean {
        val title = fields.text("title") ?: return false
        val priority = WorkOrderPriority.entries.firstOrNull { it.wire == fields["priority"]?.trim()?.lowercase() }
            ?: WorkOrderPriority.MEDIUM
        val draft = WorkOrderDraft(
            title = title,
            description = fields.text("description"),
            assetIds = listOfNotNull(fields.text("asset_id")),
            locationId = fields.text("location_id"),
            category = fields.text("category"),
            priority = priority,
            assignedUserId = fields.text("assigned_to"),
            estimatedEffortMinutes = fields.text("estimated_effort_minutes")?.toIntOrNull(),
            dueAt = parseDate(fields["due_date"]),
        )
        workOrders.create(draft)
        Timber.d("ScoutSkillExecutor: created WO '$title'")
        return true
    }

    private suspend fun executeEditWorkOrder(fields: Map<String, String?>): Boolean {
        val woId = fields.text("wo_id") ?: return false
        if (workOrders.workOrder(woId) == null) return false
        val dueAt = parseDate(fields["due_date"])
        val title = fields.text("title")
        val description = fields["description"]?.trim()
        val priority = fields.text("priority")?.lowercase()?.let { p -> WorkOrderPriority.entries.firstOrNull { it.wire == p } }
        val category = fields.text("category")
        if (dueAt == null && title == null && description == null && priority == null && category == null) return false
        workOrders.edit(woId) { d ->
            d.copy(
                dueAt = dueAt ?: d.dueAt,
                title = title ?: d.title,
                description = description ?: d.description,
                priority = priority ?: d.priority,
                category = category ?: d.category,
            )
        }
        Timber.d("ScoutSkillExecutor: edited WO $woId")
        return true
    }

    private suspend fun executeAddAsset(fields: Map<String, String?>): Boolean {
        val name = fields.text("name") ?: return false
        val id = assets.create(
            AssetDraft(
                name = name,
                assetTypeCode = fields.text("asset_type"),
                make = fields.text("make"),
                model = fields.text("model"),
                modelYear = fields.text("year")?.toIntOrNull(),
                locationId = fields.text("location_id"),
                avatarInitial = name.first().uppercaseChar().toString(),
            ),
        )
        fields.text("parent_asset_id")?.let { assets.setParent(id, it) }
        Timber.d("ScoutSkillExecutor: added asset '$name'")
        return true
    }

    private suspend fun executeWorkOrderAction(fields: Map<String, String?>): Boolean {
        val woId = fields.text("wo_id") ?: return false
        val status = fields.text("action")?.lowercase()?.let { ACTION_STATUS[it] }
            ?: fields.text("status")?.let { s -> WorkOrderStatus.entries.firstOrNull { it.wire == s } }
            ?: return false
        if (workOrders.workOrder(woId) == null) return false
        workOrders.transition(woId, status)
        Timber.d("ScoutSkillExecutor: work-order-action on $woId -> ${status.wire}")
        return true
    }

    private suspend fun executeWorkOrderAssign(fields: Map<String, String?>): Boolean {
        val woId = fields.text("wo_id") ?: return false
        val userId = fields.text("assigned_to") ?: return false
        if (workOrders.workOrder(woId) == null) return false
        workOrders.assign(woId, userId)
        Timber.d("ScoutSkillExecutor: assigned WO $woId to $userId")
        return true
    }

    /**
     * Makes a work order recurring (`maint.work_orders.set_recurrence`): `rrule`, optional
     * `end_type` never | count | date with `end_count` / `end_date`. Incomplete ends fall back to
     * the form.
     */
    private suspend fun executeSetRecurrence(fields: Map<String, String?>): Boolean {
        val woId = fields.text("wo_id") ?: return false
        val rrule = fields.text("rrule") ?: return false
        val endType = fields.text("end_type")?.lowercase() ?: "never"
        val endCount = fields.text("end_count")?.toIntOrNull()
        val endOn = parseDate(fields["end_date"])?.let { Math.floorDiv(it, MILLIS_PER_DAY) }
        val complete = when (endType) {
            "never" -> true
            "count" -> endCount != null && endCount > 0
            "date" -> endOn != null
            else -> false
        }
        if (!complete || workOrders.workOrder(woId) == null) return false
        workOrders.setRecurrence(
            woId,
            RecurrenceRule(rrule = rrule, endType = endType, endCount = endCount, endOnEpochDay = endOn, timezone = TimeZone.getDefault().id),
        )
        Timber.d("ScoutSkillExecutor: recurrence on $woId")
        return true
    }

    /** Receives or issues stock of `part_id` at its first stocked location. */
    private suspend fun executeStockMovement(fields: Map<String, String?>, kind: MovementKind): Boolean {
        val productId = fields.text("part_id") ?: return false
        val qty = (fields.qty("quantity_change") ?: fields.qty("quantity"))?.abs() ?: return false
        if (qty.signum() == 0) return false
        val position = inventory.observePositions(productId = productId).first().firstOrNull() ?: return false
        inventory.postStockDocument(
            StockDocumentDraft(
                kind = kind,
                locationId = position.locationId,
                reference = fields.text("reference"),
                notes = fields.text("notes"),
                lines = listOf(
                    StockLineDraft(
                        productId = productId,
                        quantity = qty,
                        binId = position.binId,
                        unitCost = if (kind == MovementKind.RECEIPT) fields.qty("unit_cost_observed") else null,
                        currency = if (kind == MovementKind.RECEIPT) fields.text("currency") else null,
                    ),
                ),
            ),
        )
        Timber.d("ScoutSkillExecutor: ${kind.wire} $qty of $productId")
        return true
    }

    /** Creates a part; an initial quantity is booked as an opening balance at `location_id`. */
    private suspend fun executeAddPart(fields: Map<String, String?>): Boolean {
        val name = fields.text("part_name") ?: return false
        val initialQty = fields.qty("quantity_on_hand")?.takeIf { it.signum() > 0 }
        val locationId = fields.text("location_id")
        if (initialQty != null && locationId == null) return false
        val unitCost = fields.qty("unit_cost")
        val currency = fields.text("currency")
        val productId = inventory.createProduct(
            ProductDraft(
                name = name,
                sku = fields.text("part_number"),
                manufacturer = fields.text("manufacturer"),
                category = fields.text("category"),
                uomCode = fields.text("unit_of_measure"),
                standardUnitCost = unitCost,
                costCurrency = currency,
                defaultReorderQty = fields.qty("reorder_quantity"),
                notes = fields.text("notes"),
            ),
        )
        if (initialQty != null && locationId != null) {
            inventory.postStockDocument(
                StockDocumentDraft(
                    kind = MovementKind.OPENING_BALANCE,
                    locationId = locationId,
                    lines = listOf(StockLineDraft(productId = productId, quantity = initialQty, unitCost = unitCost, currency = currency)),
                ),
            )
        }
        Timber.d("ScoutSkillExecutor: parts-catalog-add '$name'")
        return true
    }

    private fun Map<String, String?>.text(key: String): String? = this[key]?.trim()?.takeIf(String::isNotEmpty)

    private fun Map<String, String?>.qty(key: String): BigDecimal? = this[key]?.toQtyOrNull()

    private fun parseDate(v: String?): Long? {
        if (v.isNullOrBlank()) return null
        return DATE_FORMATS.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(v)?.time
            }.getOrNull()
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        val DATE_FORMATS = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd")
        val ACTION_STATUS = mapOf(
            "complete" to WorkOrderStatus.COMPLETED,
            "start" to WorkOrderStatus.IN_PROGRESS,
            "pause" to WorkOrderStatus.ON_HOLD,
            "cancel" to WorkOrderStatus.CANCELLED,
            "reopen" to WorkOrderStatus.OPEN,
        )
    }
}
