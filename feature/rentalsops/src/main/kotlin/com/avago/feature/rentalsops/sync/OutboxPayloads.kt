package com.avago.feature.rentalsops.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `sync_queue.entity_type` values owned by [RentalOpsOutbox]. */
object RentalOpsQueueTypes {
    const val TASK_EVENT = "rentalops_task_event"
    const val ASSIGN = "rentalops_assign"
    const val TRANSFER = "rentalops_transfer"
    val ALL = setOf(TASK_EVENT, ASSIGN, TRANSFER)
}

internal val outboxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/** Driver status update (entityId = task id). Proof media rows point at this queue id. */
@Serializable
data class TaskEventPayload(
    val taskId: String,
    val action: String,
    val at: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val reason: String? = null,
    val signedName: String? = null,
    val notes: String? = null,
)

/** Class line → unit (entityId = line id). */
@Serializable
data class AssignPayload(
    val orderId: String,
    val lineId: String,
    val assetId: String,
    val assetName: String? = null,
    /**
     * `order_version` from `unassigned-lines` when the dispatcher picked the unit: sent as
     * `version` so an order edited meanwhile answers 409 instead of being silently changed.
     */
    val orderVersion: Long? = null,
)

/** Branch transfer step (entityId = local transfer id). */
@Serializable
data class TransferPayload(
    /** `create`, `ship` or `receive`. */
    val op: String,
    val transferId: String,
    val assetId: String,
    val assetName: String? = null,
    val fromLocationId: String? = null,
    val toLocationId: String? = null,
    val arriveBy: String? = null,
    val reason: String? = null,
    val at: String,
)
