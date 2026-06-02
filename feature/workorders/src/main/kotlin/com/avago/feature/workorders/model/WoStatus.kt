package com.avago.feature.workorders.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Canonical 7-stage work order status machine.
 *
 * Transition rules are enforced here in the UI layer (server also enforces via HTTP 422).
 * RBAC keys used to gate transitions are checked via [PermissionKey].
 */
enum class WoStatus(val key: String, val displayName: String) {
    DRAFT("draft", "Draft"),
    OPEN("open", "Open"),
    ASSIGNED("assigned", "Assigned"),
    IN_PROGRESS("in_progress", "In Progress"),
    ON_HOLD("on_hold", "On Hold"),
    COMPLETE("complete", "Complete"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun fromKey(key: String): WoStatus =
            entries.firstOrNull { it.key == key } ?: OPEN
    }
}

/** Returns Material 3 color for the given status. */
@Composable
fun WoStatus.statusColor(): Color = when (this) {
    WoStatus.DRAFT -> MaterialTheme.colorScheme.outline
    WoStatus.OPEN -> MaterialTheme.colorScheme.primary
    WoStatus.ASSIGNED -> MaterialTheme.colorScheme.tertiary
    WoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    WoStatus.ON_HOLD -> MaterialTheme.colorScheme.tertiary
    WoStatus.COMPLETE -> MaterialTheme.colorScheme.outline
    WoStatus.CANCELLED -> MaterialTheme.colorScheme.error
}

/**
 * Sealed class representing a transition action available to the user.
 * Each action carries the target status and the RBAC permission key required.
 */
sealed class WoTransition(
    val targetStatus: WoStatus,
    val label: String,
    val permissionKey: String,
) {
    object Assign : WoTransition(WoStatus.ASSIGNED, "Assign", "work_orders.assign")
    object StartWork : WoTransition(WoStatus.IN_PROGRESS, "Start Work", "work_orders.start")
    object PlaceOnHold : WoTransition(WoStatus.ON_HOLD, "Place On Hold", "work_orders.update")
    object Resume : WoTransition(WoStatus.IN_PROGRESS, "Resume", "work_orders.update")
    object Complete : WoTransition(WoStatus.COMPLETE, "Complete", "work_orders.update")
    object Cancel : WoTransition(WoStatus.CANCELLED, "Cancel", "work_orders.delete")
}

/**
 * Returns the set of transitions available from this status.
 * The caller must additionally check RBAC before showing the action.
 */
fun WoStatus.availableTransitions(): List<WoTransition> = when (this) {
    WoStatus.DRAFT -> listOf(WoTransition.Assign, WoTransition.Cancel)
    WoStatus.OPEN -> listOf(WoTransition.Assign, WoTransition.Cancel)
    WoStatus.ASSIGNED -> listOf(WoTransition.StartWork, WoTransition.Cancel)
    WoStatus.IN_PROGRESS -> listOf(
        WoTransition.PlaceOnHold,
        WoTransition.Complete,
        WoTransition.Cancel,
    )
    WoStatus.ON_HOLD -> listOf(WoTransition.Resume, WoTransition.Cancel)
    WoStatus.COMPLETE -> emptyList()
    WoStatus.CANCELLED -> emptyList()
}
