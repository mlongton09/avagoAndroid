package com.avago.feature.workorders.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.avago.core.data.domain.WorkOrderStatus

/** Presentation of the v2 work-order status machine ([WorkOrderStatus]). */
enum class WoStatus(val domain: WorkOrderStatus) {
    DRAFT(WorkOrderStatus.DRAFT),
    PENDING_APPROVAL(WorkOrderStatus.PENDING_APPROVAL),
    OPEN(WorkOrderStatus.OPEN),
    SCHEDULED(WorkOrderStatus.SCHEDULED),
    ASSIGNED(WorkOrderStatus.ASSIGNED),
    IN_PROGRESS(WorkOrderStatus.IN_PROGRESS),
    ON_HOLD(WorkOrderStatus.ON_HOLD),
    COMPLETE(WorkOrderStatus.COMPLETED),
    CLOSED(WorkOrderStatus.CLOSED),
    CANCELLED(WorkOrderStatus.CANCELLED),
    SKIPPED(WorkOrderStatus.SKIPPED);

    val key: String get() = domain.wire

    companion object {
        fun fromDomain(status: WorkOrderStatus): WoStatus = entries.first { it.domain == status }

        fun fromKey(key: String?): WoStatus = fromDomain(WorkOrderStatus.fromWire(key))
    }
}

/** Returns Material 3 color for the given status. */
@Composable
fun WoStatus.statusColor(): Color = when (this) {
    WoStatus.DRAFT, WoStatus.PENDING_APPROVAL -> MaterialTheme.colorScheme.outline
    WoStatus.OPEN, WoStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    WoStatus.ASSIGNED, WoStatus.ON_HOLD -> MaterialTheme.colorScheme.tertiary
    WoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    WoStatus.COMPLETE, WoStatus.CLOSED, WoStatus.SKIPPED -> MaterialTheme.colorScheme.outline
    WoStatus.CANCELLED -> MaterialTheme.colorScheme.error
}

/** A status change offered to the user; the server enforces the same machine. */
sealed class WoTransition(val targetStatus: WoStatus) {
    object Assign : WoTransition(WoStatus.ASSIGNED)
    object StartWork : WoTransition(WoStatus.IN_PROGRESS)
    object PlaceOnHold : WoTransition(WoStatus.ON_HOLD)
    object Resume : WoTransition(WoStatus.IN_PROGRESS)
    object Complete : WoTransition(WoStatus.COMPLETE)
    object Cancel : WoTransition(WoStatus.CANCELLED)
}

/** Transitions available from this status; the caller additionally checks permissions. */
fun WoStatus.availableTransitions(): List<WoTransition> = when (this) {
    WoStatus.DRAFT, WoStatus.PENDING_APPROVAL, WoStatus.OPEN, WoStatus.SCHEDULED ->
        listOf(WoTransition.Assign, WoTransition.Cancel)
    WoStatus.ASSIGNED -> listOf(WoTransition.StartWork, WoTransition.Cancel)
    WoStatus.IN_PROGRESS -> listOf(WoTransition.PlaceOnHold, WoTransition.Complete, WoTransition.Cancel)
    WoStatus.ON_HOLD -> listOf(WoTransition.Resume, WoTransition.Cancel)
    WoStatus.COMPLETE, WoStatus.CLOSED, WoStatus.CANCELLED, WoStatus.SKIPPED -> emptyList()
}
