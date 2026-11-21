package com.avago.feature.rentals.ops.domain

/**
 * Delivery / pickup / transfer task lifecycle — client mirror of AvagoSvc stage C
 * `src/domain/rental_delivery.rs` so offline transitions match what the server accepts.
 *
 * ```
 * pending ─assign→ assigned ─start→ en_route ─arrive→ arrived ─complete→ completed
 *                         └──────────── fail (reason) ─────────┘→ failed
 * cancel / assign / unassign / reopen are dispatcher (server-side) actions.
 * ```
 *
 * Client is stricter than the server in one place: a signature only counts as proof when the
 * signer's printed name was captured (the server accepts a bare signature).
 */
enum class TaskStatus(val wire: String) {
    PLANNED("planned"),
    ASSIGNED("assigned"),
    EN_ROUTE("en_route"),
    ARRIVED("arrived"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    val isOpen: Boolean get() = this != COMPLETED && this != FAILED && this != CANCELLED

    companion object {
        fun parse(s: String?): TaskStatus? = entries.firstOrNull { it.wire == s }
    }
}

enum class TaskKind(val wire: String) {
    DELIVERY("delivery"),
    PICKUP("pickup"),
    TRANSFER("transfer"),
    SWAP("swap");

    companion object {
        fun parse(s: String?): TaskKind? = entries.firstOrNull { it.wire == s }
    }
}

/** Actions a driver may take on their own task (server `TaskAction::driver_allowed`). */
enum class TaskAction(val wire: String) {
    START("start"),
    ARRIVE("arrive"),
    COMPLETE("complete"),
    FAIL("fail");

    companion object {
        fun parse(s: String?): TaskAction? = entries.firstOrNull { it.wire == s }
    }
}

data class Proof(
    val photos: Int = 0,
    val signature: Boolean = false,
    val signedName: String? = null,
    val notes: String? = null,
) {
    val hasNamedSignature: Boolean get() = signature && !signedName.isNullOrBlank()
}

/** Stable codes, identical to the server's `TransitionError::code`. */
sealed class TransitionError(val code: String) {
    data class Invalid(val from: TaskStatus, val action: TaskAction) : TransitionError("invalid_transition")
    data object DriverRequired : TransitionError("driver_required")
    data object ProofRequired : TransitionError("proof_required")
    data object ReasonRequired : TransitionError("reason_required")
}

sealed class TransitionResult {
    data class Ok(val to: TaskStatus) : TransitionResult()
    data class Rejected(val error: TransitionError) : TransitionResult()
}

object DeliveryStateMachine {

    fun transition(
        from: TaskStatus,
        action: TaskAction,
        kind: TaskKind,
        hasDriver: Boolean,
        proof: Proof = Proof(),
        reason: String? = null,
    ): TransitionResult {
        val to = when {
            action == TaskAction.START && from == TaskStatus.ASSIGNED -> TaskStatus.EN_ROUTE
            action == TaskAction.START && from == TaskStatus.PLANNED -> {
                if (!hasDriver) return TransitionResult.Rejected(TransitionError.DriverRequired)
                TaskStatus.EN_ROUTE
            }
            action == TaskAction.ARRIVE && from == TaskStatus.EN_ROUTE -> TaskStatus.ARRIVED
            action == TaskAction.COMPLETE && (from == TaskStatus.EN_ROUTE || from == TaskStatus.ARRIVED) -> {
                if (kind == TaskKind.DELIVERY && proof.photos <= 0 && !proof.hasNamedSignature) {
                    return TransitionResult.Rejected(TransitionError.ProofRequired)
                }
                TaskStatus.COMPLETED
            }
            action == TaskAction.FAIL &&
                (from == TaskStatus.ASSIGNED || from == TaskStatus.EN_ROUTE || from == TaskStatus.ARRIVED) -> {
                if (reason.isNullOrBlank()) return TransitionResult.Rejected(TransitionError.ReasonRequired)
                TaskStatus.FAILED
            }
            else -> return TransitionResult.Rejected(TransitionError.Invalid(from, action))
        }
        return TransitionResult.Ok(to)
    }

    /** Driver actions offered from [status] (UI buttons); FAIL is listed last. */
    fun availableActions(status: TaskStatus, hasDriver: Boolean): List<TaskAction> = when (status) {
        TaskStatus.PLANNED -> if (hasDriver) listOf(TaskAction.START) else emptyList()
        TaskStatus.ASSIGNED -> listOf(TaskAction.START, TaskAction.FAIL)
        TaskStatus.EN_ROUTE -> listOf(TaskAction.ARRIVE, TaskAction.COMPLETE, TaskAction.FAIL)
        TaskStatus.ARRIVED -> listOf(TaskAction.COMPLETE, TaskAction.FAIL)
        TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED -> emptyList()
    }

    /**
     * Optimistic local status: replays the queued (not yet acknowledged) driver events in order
     * over the last server status. Events that no longer apply (e.g. the dispatcher cancelled the
     * task meanwhile) are skipped and reported so the UI can flag them; the server stays the
     * authority and will reject them on push.
     */
    fun fold(server: TaskStatus, kind: TaskKind, hasDriver: Boolean, queued: List<QueuedEvent>): FoldResult {
        var status = server
        val stale = mutableListOf<QueuedEvent>()
        for (e in queued) {
            when (val r = transition(status, e.action, kind, hasDriver, e.proof, e.reason)) {
                is TransitionResult.Ok -> status = r.to
                is TransitionResult.Rejected -> stale += e
            }
        }
        return FoldResult(status, stale)
    }

    data class QueuedEvent(
        val queueId: String,
        val action: TaskAction,
        val proof: Proof = Proof(),
        val reason: String? = null,
    )

    data class FoldResult(val status: TaskStatus, val stale: List<QueuedEvent>)
}
