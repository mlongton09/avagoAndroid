package com.avago.feature.rentals.domain

import com.avago.core.data.sync.QueueRetryPolicy

/**
 * Pure ordering rules for the rentals outbox (spec §15 "upload order"):
 *
 *  * items of one **group** (an order, or a customer) run strictly in order —
 *    customer → customer document → order create → order actions / asset changes →
 *    signature → handover → ledger → invoice — then by creation time;
 *  * a handover waits until its photos are uploaded and its inspection log has synced
 *    (photos → inspection log → signature → handover → ledger);
 *  * an item waits for every `depends_on` client_uuid that is still queued;
 *  * the first item of a group that is blocked, failed or in conflict blocks the rest of
 *    that group (later steps never overtake an earlier one); other groups continue.
 *
 * Every queued item has its own `client_uuid` (= `sync_queue.entity_id` and the
 * `Idempotency-Key`), so retries after a lost response are replays, never duplicates.
 */
object OutboxPlanner {

    const val TYPE_CUSTOMER = "rental_customer"
    const val TYPE_CUSTOMER_DOCUMENT = "rental_customer_document"
    const val TYPE_ORDER = "rental_order"
    const val TYPE_ORDER_ACTION = "rental_order_action"
    const val TYPE_ASSET = "rental_asset_enable"
    const val TYPE_SIGNATURE = "rental_signature"
    const val TYPE_HANDOVER = "rental_handover"
    const val TYPE_LEDGER = "rental_ledger"
    const val TYPE_INVOICE = "rental_invoice"
    /** Readiness checklist decision (POST / PATCH /readiness-checks; group `readiness:<asset>`). */
    const val TYPE_READINESS = "rental_readiness"
    /** Line swap (POST /orders/{o}/lines/{l}/swap; group `order:<id>`, after handovers). */
    const val TYPE_LINE_SWAP = "rental_line_swap"
    /** Wave 2 in-person e-signature capture (POST /signature-requests/{id}/sign; before the check-out). */
    const val TYPE_ESIGN = "rental_esign_sign"
    /** Wave 2 partial bulk return (POST /orders/{o}/lines/{l}/bulk-return; group `order:<id>`). */
    const val TYPE_BULK_RETURN = "rental_bulk_return"

    val TYPES = setOf(
        TYPE_CUSTOMER, TYPE_CUSTOMER_DOCUMENT, TYPE_ORDER, TYPE_ORDER_ACTION, TYPE_ASSET,
        TYPE_SIGNATURE, TYPE_HANDOVER, TYPE_LEDGER, TYPE_INVOICE, TYPE_READINESS, TYPE_LINE_SWAP,
        TYPE_ESIGN, TYPE_BULK_RETURN,
    )

    fun rank(type: String): Int = when (type) {
        TYPE_CUSTOMER -> 0
        TYPE_CUSTOMER_DOCUMENT -> 1
        TYPE_ORDER -> 2
        TYPE_ORDER_ACTION, TYPE_ASSET -> 3
        TYPE_ESIGN -> 4
        TYPE_SIGNATURE -> 5
        TYPE_HANDOVER, TYPE_LINE_SWAP, TYPE_READINESS, TYPE_BULK_RETURN -> 6
        TYPE_LEDGER -> 7
        TYPE_INVOICE -> 8
        else -> 9
    }

    data class Item(
        val queueId: String,
        val type: String,
        /** client_uuid of this action. */
        val clientUuid: String,
        val group: String,
        val createdAt: Long,
        val dependsOn: List<String> = emptyList(),
        val photoIds: List<String> = emptyList(),
        val logId: String? = null,
        /** sync_queue status: pending | error | conflict | failed | in_flight. */
        val status: String = "pending",
        val attempts: Long = 0,
    )

    enum class Wait { DEPENDENCY, PHOTOS, INSPECTION_LOG, EARLIER_STEP, CONFLICT, FAILED, GAVE_UP }

    data class Plan(val runnable: List<Item>, val waiting: Map<String, Wait>)

    /**
     * @param queued client_uuids that are still in the queue (not yet confirmed).
     * @param photoUploaded true when the photo has a storage key.
     * @param logSynced true when the inspection log has no pending push.
     */
    fun plan(
        items: List<Item>,
        queued: Set<String> = items.mapTo(HashSet()) { it.clientUuid },
        photoUploaded: (String) -> Boolean = { true },
        logSynced: (String) -> Boolean = { true },
        maxAttempts: Long = MAX_ATTEMPTS,
    ): Plan {
        // Iterate to a fixpoint so a cross-group dependency is honoured regardless of the order
        // groups are visited in (the runnable set only grows, so this terminates).
        var known = emptySet<String>()
        while (true) {
            val (runnable, waiting) = planPass(items, queued, photoUploaded, logSynced, maxAttempts, known)
            val now = runnable.mapTo(HashSet()) { it.clientUuid }
            if (now == known) return Plan(executionOrder(runnable), waiting)
            known = now
        }
    }

    private fun planPass(
        items: List<Item>,
        queued: Set<String>,
        photoUploaded: (String) -> Boolean,
        logSynced: (String) -> Boolean,
        maxAttempts: Long,
        runnableUuids: Set<String>,
    ): Pair<List<Item>, Map<String, Wait>> {
        val runnable = mutableListOf<Item>()
        val waiting = LinkedHashMap<String, Wait>()
        items.groupBy { it.group }.forEach { (_, groupItems) ->
            var blocked = false
            for (it in groupItems.sortedWith(compareBy<Item>({ rank(it.type) }, { it.createdAt }, { it.queueId }))) {
                if (blocked) {
                    waiting[it.queueId] = Wait.EARLIER_STEP
                    continue
                }
                val wait = when {
                    it.status == "conflict" -> Wait.CONFLICT
                    it.status == STATUS_FAILED -> Wait.FAILED
                    it.attempts >= maxAttempts -> Wait.GAVE_UP
                    it.dependsOn.any { d ->
                        d != it.clientUuid && d in queued && d !in runnableUuids && runnable.none { r -> r.clientUuid == d }
                    } -> Wait.DEPENDENCY
                    it.photoIds.any { p -> !photoUploaded(p) } -> Wait.PHOTOS
                    it.logId != null && !logSynced(it.logId) -> Wait.INSPECTION_LOG
                    else -> null
                }
                if (wait != null) {
                    waiting[it.queueId] = wait
                    blocked = true
                } else {
                    runnable += it
                }
            }
        }
        return runnable to waiting
    }

    /**
     * Orders runnable items so every queued prerequisite runs first: its `depends_on`
     * items (e.g. an order created for an offline customer of another group) and the earlier
     * steps of its own group. Cycles fall back to the original order.
     */
    private fun executionOrder(runnable: List<Item>): List<Item> {
        val pending = runnable.toMutableList()
        val out = ArrayList<Item>(runnable.size)
        while (pending.isNotEmpty()) {
            val next = pending.firstOrNull { p ->
                val groupIdx = pending.indexOf(p)
                pending.none { q ->
                    q !== p && (q.clientUuid in p.dependsOn || (q.group == p.group && pending.indexOf(q) < groupIdx))
                }
            } ?: pending.first()
            out += next
            pending.remove(next)
        }
        return out
    }

    /** Server types that live on the stage B routes (handovers, swaps, readiness, signatures, ledger, invoices). */
    val STAGE_B_TYPES = setOf(TYPE_SIGNATURE, TYPE_HANDOVER, TYPE_LEDGER, TYPE_INVOICE, TYPE_READINESS, TYPE_LINE_SWAP, TYPE_ESIGN, TYPE_BULK_RETURN)

    /** Queue status of a row parked after a 404/405 (visible error, retry by the user). */
    const val STATUS_FAILED = "failed"

    /**
     * RETRY uses an attempt; OFFLINE (no HTTP answer) and LATER (501) do not; FAILED parks the
     * row with a visible error; CONFLICT asks the user; UNAUTHORIZED stops the run.
     */
    enum class Disposition { RETRY, LATER, FAILED, CONFLICT, UNAUTHORIZED, OFFLINE }

    /** A queued payload missing a field the request needs (parked as FAILED, never retried blindly). */
    class MalformedItem(message: String) : IllegalStateException(message)

    /**
     * How a failed push attempt is handled: an HTTP answer goes through [disposition]; an
     * [java.io.IOException] (offline, DNS, socket, timeout) never reached the server, so it is
     * OFFLINE and does not consume the retry budget; a [MalformedItem] is FAILED.
     */
    fun dispositionOf(type: String, e: Throwable): Disposition = when (e) {
        is com.avago.core.network.rentals.RentalsApiException -> disposition(type, e.status, e.code, e.message)
        is java.io.IOException -> Disposition.OFFLINE
        is MalformedItem -> Disposition.FAILED
        else -> Disposition.RETRY
    }

    /**
     * How a rejected request is handled (error contract, docs/rentals-api.md + stage B):
     *  * 401 → stop the run (session refresh);
     *  * 501 / `not_implemented` → stay queued without burning attempts (feature not shipped);
     *  * 405, and a code-less 404 `{"error":"Not found"}` (the route — or the row it addresses —
     *    is missing on this server) → FAILED: parked with a visible error so a handover never
     *    stalls silently; the user can retry once the server is updated, or discard;
     *  * 409 for an in-flight duplicate of the same `Idempotency-Key` (no code) or
     *    `duplicate_client_uuid` (concurrent write; a replay returns it) → retry;
     *  * 408/425/429/5xx → retry;
     *  * anything else (409 version/allocation conflict, `already_checked_out|in`,
     *    invalid_transition, 400, 403, coded 404 such as a missing order) → conflict for review.
     */
    @Suppress("UNUSED_PARAMETER")
    fun disposition(type: String, status: Int, code: String?, message: String?): Disposition = when {
        status == 401 -> Disposition.UNAUTHORIZED
        status == 501 || code == "not_implemented" -> Disposition.LATER
        status == 405 || (status == 404 && code == null) -> Disposition.FAILED
        status == 409 && code == "duplicate_client_uuid" -> Disposition.RETRY
        status == 409 && code == null && (message ?: "").contains("idempotency key", ignoreCase = true) -> Disposition.RETRY
        QueueRetryPolicy.isTransientHttp(status) -> Disposition.RETRY
        else -> Disposition.CONFLICT
    }

    /**
     * Stage B success wrappers carry `warnings:[{code, message?}]` (`needs_review`,
     * `fuel_not_priced`, `accessories_missing`, …; handovers use `reason` for the text).
     * Returns "code: message; …" or null.
     */
    fun warningsText(resp: kotlinx.serialization.json.JsonObject): String? {
        val list = resp["warnings"] as? kotlinx.serialization.json.JsonArray ?: return null
        val parts = list.mapNotNull { w ->
            val o = w as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val code = o.str("code") ?: return@mapNotNull null
            (o.str("message") ?: o.str("reason"))?.let { "$code: $it" } ?: code
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    const val MAX_ATTEMPTS = QueueRetryPolicy.MAX_ATTEMPTS
}
