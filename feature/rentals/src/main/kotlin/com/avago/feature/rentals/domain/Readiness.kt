package com.avago.feature.rentals.domain

import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.ReadinessCheckItem

/**
 * Readiness checklist after a check-in. A unit is **Not ready** while its latest check is
 * `pending` or `failed`; it becomes available again once the check is `passed`, or `waived`
 * (needs `rental.override_ready` and a reason).
 */
object Readiness {
    const val PENDING = "pending"
    const val PASSED = "passed"
    const val FAILED = "failed"
    const val WAIVED = "waived"

    /** Permission needed to waive a checklist. */
    const val PERMISSION_WAIVE = "rental.override_ready"

    /** Server default checklist (handovers.rs `default_checklist`). */
    val DEFAULT_ITEMS = listOf(
        ReadinessCheckItem("inspected", "Inspected", false),
        ReadinessCheckItem("cleaned", "Cleaned", false),
        ReadinessCheckItem("refuelled", "Refuelled / charged", false),
        ReadinessCheckItem("accessories", "Accessories complete", false),
    )

    /** The check a unit's readiness screen edits: the newest open check, else the newest check. */
    fun current(checks: List<ReadinessCheck>): ReadinessCheck? =
        checks.filter { it.open }.maxByOrNull { it.at } ?: checks.maxWithOrNull(compareBy<ReadinessCheck> { it.at }.thenBy { it.version })

    /** Allowed status changes (server `check_status_ok`): only an open check can change. */
    fun canTransition(from: String, to: String): Boolean =
        (from == PENDING || from == FAILED) && to in setOf(PENDING, PASSED, FAILED, WAIVED)

    enum class Problem { CLOSED, INCOMPLETE, REASON_REQUIRED, NOT_ALLOWED }

    /**
     * Validates a decision before it is queued. Passing needs every item done; failing needs a
     * reason (what is wrong); waiving needs [canWaive] and a reason (server `reason_required`).
     */
    fun validate(from: String, to: String, items: List<ReadinessCheckItem>, notes: String?, canWaive: Boolean): Problem? = when {
        !canTransition(from, to) -> Problem.CLOSED
        to == WAIVED && !canWaive -> Problem.NOT_ALLOWED
        to == WAIVED && notes.isNullOrBlank() -> Problem.REASON_REQUIRED
        to == FAILED && notes.isNullOrBlank() && items.all { it.done } -> Problem.REASON_REQUIRED
        to == PASSED && items.any { !it.done } -> Problem.INCOMPLETE
        else -> null
    }
}
