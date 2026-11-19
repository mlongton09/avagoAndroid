package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** What an activity record documents (`activity_records.kind`). */
enum class ActivityType(val wire: String) {
    SERVICE("service"), INSPECTION("inspection"), REPAIR("repair"), FUEL("fuel"), HANDOVER("handover"),
    NOTE("note"), OTHER("other");

    companion object {
        fun fromWire(raw: String?): ActivityType = entries.firstOrNull { it.wire == raw } ?: OTHER

        /** Kind for a log category key (`oil_change`, `inspection`, `fuel`...); categories default to service. */
        fun forCategory(category: String?): ActivityType {
            val key = category?.trim()?.lowercase().orEmpty()
            return when {
                key.isEmpty() -> SERVICE
                key == "inspection" || key.endsWith("_inspection") || key.startsWith("inspection_") -> INSPECTION
                key == "fuel" || key.startsWith("fuel_") -> FUEL
                key == "repair" || key.endsWith("_repair") -> REPAIR
                key == "note" || key == "notes" -> NOTE
                key == "handover" -> HANDOVER
                else -> SERVICE
            }
        }
    }
}

/** A log entry: service, inspection, repair, fuel, note... (`activity_records`). */
data class Activity(
    val id: String,
    val assetId: String?,
    val assetName: String?,
    val workOrderId: String?,
    val parentActivityId: String?,
    val type: ActivityType,
    val categoryLabel: String?,
    val title: String?,
    val occurredAt: Long,
    val performedByUserId: String?,
    val performedByName: String?,
    val providerName: String?,
    val currency: String?,
    val overtimeMultiplier: BigDecimal?,
    val notes: String?,
    /** Kind-specific content, e.g. inspection answers (JSON object text). */
    val payload: String,
    /** Account-defined fields (JSON object text). */
    val attributes: String,
    /** Meter value recorded with the activity (`meter_readings.activity_id`). */
    val meterValue: BigDecimal?,
    val totalCost: BigDecimal,
    val createdAt: Long,
    val version: Long,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: categoryLabel?.takeIf { it.isNotBlank() } ?: type.wire
}

/** Fields the user edits on an activity. */
data class ActivityDraft(
    val assetId: String? = null,
    val workOrderId: String? = null,
    val type: ActivityType = ActivityType.SERVICE,
    val categoryLabel: String? = null,
    val title: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val meterValue: BigDecimal? = null,
    val performedByUserId: String? = null,
    val performedByName: String? = null,
    val providerName: String? = null,
    val currency: String? = null,
    val overtimeMultiplier: BigDecimal? = null,
    val notes: String? = null,
    val payload: String? = null,
    val attributes: String? = null,
)

/** A tracked inspection finding (`inspection_defects`). */
data class InspectionDefect(
    val id: String,
    val assetId: String,
    val itemKey: String,
    val label: String?,
    val cornerLabel: String?,
    val sectionTitle: String?,
    /** `nr` (needs repair) or `m` (monitor). */
    val bucket: String,
    val severity: String?,
    val note: String?,
    val locationLabel: String?,
    val status: DefectState,
    val sourceActivityId: String?,
    val reportedAt: Long,
    val lastSeenAt: Long?,
    val resolvedAt: Long?,
    val resolutionNote: String?,
    val workOrderId: String?,
    val version: Long,
) {
    val displayLabel: String get() = label?.takeIf { it.isNotBlank() } ?: itemKey
}

enum class DefectState(val wire: String) {
    OPEN("open"), MONITORING("monitoring"), REPAIRED("repaired"), DEFERRED("deferred");

    val isOpen: Boolean get() = this == OPEN || this == MONITORING

    companion object {
        fun fromWire(raw: String?): DefectState = entries.firstOrNull { it.wire == raw } ?: OPEN
    }
}

data class ActivityFilter(
    val assetId: String? = null,
    val workOrderId: String? = null,
    val types: Set<ActivityType> = emptySet(),
    val query: String = "",
    val from: Long? = null,
    val to: Long? = null,
)

interface ActivityRepository {
    fun observeActivities(filter: ActivityFilter = ActivityFilter()): Flow<List<Activity>>

    fun observeActivity(id: String): Flow<Activity?>

    suspend fun activity(id: String): Activity?

    fun observeCostLines(activityId: String): Flow<List<CostLine>>

    /** Cost lines of every activity in the window (reports). */
    fun observeAllCostLines(from: Long? = null, to: Long? = null): Flow<List<CostLine>>

    fun observeDefects(assetId: String? = null, openOnly: Boolean = true): Flow<List<InspectionDefect>>

    /**
     * Creates the activity, its cost lines and picture links. Lines and attachments depend on the
     * create command, so the server applies them in order.
     */
    suspend fun create(draft: ActivityDraft, lines: List<CostLineDraft> = emptyList(), files: List<LocalFileRef> = emptyList()): String

    suspend fun update(id: String, draft: ActivityDraft)

    suspend fun delete(id: String)

    suspend fun addCostLine(activityId: String, draft: CostLineDraft): String

    suspend fun updateCostLine(lineId: String, draft: CostLineDraft)

    suspend fun deleteCostLine(lineId: String)

    suspend fun updateDefect(id: String, status: DefectState, resolutionNote: String?)
}
