package com.avago.feature.rentals.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Hybrid policy for a queued rental edit answered with 409 version `conflict`
 * (`{error:"conflict", message, current}`), shared with iOS.
 *
 * The outbox item carries the **base** (the synced row the edit was made on) and its
 * **changes** (the field values the edit sets). On a 409:
 *  * local changed fields = changes that differ from the base;
 *  * server changed fields = fields where `current` differs from the base;
 *  * disjoint → [Decision.Resend]: the same request with only the locally changed fields
 *    (so server edits are never overwritten with stale base values) and the version
 *    taken from `current`, sent once with the same idempotency key (the server caches only
 *    2xx answers, so a 409 never replays);
 *  * overlapping, base unknown, no usable `current`, or a 409 after a rebase →
 *    [Decision.Conflict] for the user (retry / discard).
 *
 * Values compare as whole JSON values (nested objects and arrays included); timestamps
 * compare as instants and numbers numerically, so `…Z` vs `…+00:00` or `5` vs `"5"` are equal.
 */
object ConflictMerge {

    /** Outbox payload keys: the synced row the edit was made on, its changes, and the rebase marker. */
    const val KEY_BASE = "merge_base"
    const val KEY_CHANGES = "merge_changes"
    const val KEY_REBASED = "merge_rebased"

    /** Bookkeeping columns that change on every write and are never compared. */
    val META: Set<String> = setOf(
        "version", "server_version", "updated_at", "created_at", "deleted_at",
        "updated_by", "created_by", "client_uuid",
    )

    enum class Reason { OVERLAP, NO_BASE, NO_CURRENT, REPEATED }

    sealed interface Decision {
        /** Fields the server changed since the base (apply them to the local copy). */
        val serverChanged: Set<String>

        data class Resend(val body: JsonObject, override val serverChanged: Set<String>) : Decision

        /** [fields] are the clashing fields (empty when they can't be determined). */
        data class Conflict(val reason: Reason, val fields: Set<String>, override val serverChanged: Set<String>) : Decision
    }

    fun resolve(
        base: JsonObject?,
        changes: JsonObject,
        current: JsonObject?,
        body: JsonObject,
        versionField: String,
        alreadyRebased: Boolean,
    ): Decision {
        val version = current?.get(versionField)?.takeIf { it is JsonPrimitive && it !is JsonNull }
        if (current == null || version == null) return Decision.Conflict(Reason.NO_CURRENT, emptySet(), emptySet())
        if (base == null) return Decision.Conflict(Reason.NO_BASE, emptySet(), emptySet())
        val local = localChanged(base, changes)
        val server = serverChanged(base, current)
        // A locally changed field the server row doesn't carry can't be checked: treat it as a clash.
        val overlap = local.filterTo(sortedSetOf()) { it in server || !current.containsKey(it) }
        return when {
            alreadyRebased -> Decision.Conflict(Reason.REPEATED, overlap, server)
            overlap.isNotEmpty() -> Decision.Conflict(Reason.OVERLAP, overlap, server)
            else -> {
                val unchanged = changes.keys - local
                Decision.Resend(JsonObject(body - unchanged + (versionField to version)), server)
            }
        }
    }

    /**
     * The (changes, body) to keep queued after a [Decision.Conflict], with `current` becoming the
     * new base: only the locally changed fields (relative to the old [base], when known) and the
     * version from `current`. The user's Retry then re-applies exactly their edits ("keep mine")
     * without overwriting the server's other changes with stale values.
     */
    fun forRetry(
        base: JsonObject?,
        changes: JsonObject,
        current: JsonObject,
        body: JsonObject,
        versionField: String,
    ): Pair<JsonObject, JsonObject> {
        val unchanged = if (base == null) emptySet() else changes.keys - localChanged(base, changes)
        val version = current[versionField]?.takeIf { it is JsonPrimitive && it !is JsonNull }
        val nextBody = body - unchanged + listOfNotNull(version?.let { versionField to it })
        return JsonObject(changes - unchanged) to JsonObject(nextBody)
    }

    /** Fields in [changes] whose value differs from [base] (bookkeeping excluded). */
    fun localChanged(base: JsonObject, changes: JsonObject): Set<String> =
        changes.keys.filterTo(sortedSetOf()) { it !in META && !same(changes[it], base[it]) }

    /** Fields present in both rows whose value the server changed (bookkeeping excluded). */
    fun serverChanged(base: JsonObject, current: JsonObject): Set<String> =
        base.keys.filterTo(sortedSetOf()) { it !in META && current.containsKey(it) && !same(base[it], current[it]) }

    /**
     * The local copy after a conflict: the device row with the server's changes to [fields]
     * (the non-conflicting ones) applied, so only the clashing fields still show local values.
     */
    fun applyServerFields(local: JsonObject, current: JsonObject, fields: Set<String>): JsonObject =
        JsonObject(local + fields.mapNotNull { k -> current[k]?.let { k to it } })

    /** Structural equality with null ≡ missing, timestamps as instants, numbers by value. */
    fun same(a: JsonElement?, b: JsonElement?): Boolean {
        val x = a ?: JsonNull
        val y = b ?: JsonNull
        if (x == y) return true
        if (x !is JsonPrimitive || y !is JsonPrimitive || x is JsonNull || y is JsonNull) return false
        if (x.content == y.content) return true
        // Numbers by value only when one side is a JSON number (BIGINTs may arrive as strings);
        // two strings stay textual so "0123" and "123" differ.
        if (!x.isString || !y.isString) {
            val nx = x.content.toBigDecimalOrNull()
            val ny = y.content.toBigDecimalOrNull()
            if (nx != null && ny != null) return nx.compareTo(ny) == 0
        }
        val ix = instant(x.content) ?: return false
        return ix == instant(y.content)
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? =
        if (isEmpty() || (this[0] != '-' && !this[0].isDigit())) null else runCatching { BigDecimal(this) }.getOrNull()

    private fun instant(s: String): Instant? =
        if (s.length < 20 || s[4] != '-') null
        else runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull() ?: runCatching { Instant.parse(s) }.getOrNull()
}
