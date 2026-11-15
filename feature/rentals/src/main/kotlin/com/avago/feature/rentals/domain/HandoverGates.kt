package com.avago.feature.rentals.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Hand-over gates of wave 2 (docs/rentals-api-wave2-esign-scan-meter.md §1 checkout gate, §3
 * meter rules; docs/rentals-api-wave2-credit-collections-bank.md checkout credit/doc gate). A queued
 * check-out / check-in refused by one of them is parked as a conflict whose notice is
 * `gate:<code>|<override permission>|<server message>`; the order screen explains it and offers
 * the fix (sign now, enter the reading, or an override with a reason when the user holds the
 * permission). [patch] rewrites the queued body before it is retried.
 */
object HandoverGates {

    const val NOTICE_PREFIX = "gate:"

    const val SIGNATURE_REQUIRED = "signature_required"
    const val METER_REQUIRED = "meter_required"
    const val METER_BELOW_CHECKOUT = "meter_below_checkout"
    const val METER_DELTA_EXCEEDED = "meter_delta_exceeded"
    const val REASON_REQUIRED = "reason_required"

    const val PERMISSION_METER_OVERRIDE = "rental.meter_override"

    /**
     * Credit / documents gate (G2, HTTP 409 block codes). Queued replays carrying a `client_uuid`
     * are never blocked (the server answers `credit_gate_bypassed_offline`), but an online send can be.
     */
    val CREDIT_CODES = setOf(
        "customer_blocked", "customer_on_hold", "customer_cash_only", "credit_limit_exceeded",
        "required_documents_missing", "required_documents_expired",
    )

    const val PERMISSION_CREDIT_OVERRIDE = "rental.credit_override"

    val METER_CODES = setOf(METER_REQUIRED, METER_BELOW_CHECKOUT, METER_DELTA_EXCEEDED, REASON_REQUIRED)

    fun isGate(code: String?): Boolean =
        code != null && (code == SIGNATURE_REQUIRED || code in METER_CODES || code in CREDIT_CODES)

    data class Gate(val code: String, val permission: String?, val message: String?) {
        val isMeter: Boolean get() = code in METER_CODES
        val isCredit: Boolean get() = code in CREDIT_CODES
        /** The user fixes it by entering a value (no override needed). */
        val needsReading: Boolean get() = code == METER_REQUIRED
        /** Overriding needs a written reason (meter + credit; the signature override does not). */
        val needsReason: Boolean get() = code != SIGNATURE_REQUIRED && !needsReading
    }

    /** Permission that overrides [code] when the server did not name one. */
    fun defaultPermission(code: String): String? = when (code) {
        SIGNATURE_REQUIRED -> Esign.PERMISSION_OVERRIDE
        METER_BELOW_CHECKOUT, METER_DELTA_EXCEEDED, REASON_REQUIRED -> PERMISSION_METER_OVERRIDE
        in CREDIT_CODES -> PERMISSION_CREDIT_OVERRIDE
        else -> null
    }

    /** Notice for a refused hand-over, from the error `code`, `message` and `details`. */
    fun notice(code: String, message: String?, details: JsonObject?): String {
        val perm = details?.str("override_permission") ?: defaultPermission(code)
        val canOverride = details?.bool("can_override")
        val p = if (canOverride == false) "" else perm.orEmpty()
        return NOTICE_PREFIX + code + "|" + p + "|" + (message ?: "").replace('\n', ' ').take(300)
    }

    fun parse(notice: String?): Gate? {
        if (notice == null || !notice.startsWith(NOTICE_PREFIX)) return null
        val parts = notice.removePrefix(NOTICE_PREFIX).split('|', limit = 3)
        val code = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return Gate(code, parts.getOrNull(1)?.takeIf { it.isNotBlank() }, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }

    /**
     * The queued hand-over body after the user's fix: a meter reading for `meter_required`, else
     * the override flag of the gate (+ reason). Returns null when the fix is incomplete.
     */
    fun patch(body: JsonObject, gate: Gate, reason: String?, meterValue: String?): JsonObject? {
        val extra: Map<String, JsonPrimitive> = when {
            gate.needsReading -> {
                val v = meterValue?.replace(',', '.')?.trim()?.toBigDecimalOrNull() ?: return null
                if (v.signum() < 0) return null
                mapOf("meter_value" to JsonPrimitive(v.stripTrailingZeros().toPlainString()))
            }
            gate.code == SIGNATURE_REQUIRED -> mapOf("override_signature" to JsonPrimitive(true))
            gate.isMeter -> {
                val r = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                mapOf("meter_override" to JsonPrimitive(true), "meter_override_reason" to JsonPrimitive(r))
            }
            gate.isCredit -> {
                val r = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                // One approval covers both the credit and the document block, so the retry is not
                // refused again by the other half of the same gate.
                mapOf(
                    "override_credit" to JsonPrimitive(true),
                    "override_docs" to JsonPrimitive(true),
                    "override_reason" to JsonPrimitive(r),
                )
            }
            else -> return null
        }
        // Earlier approvals (signature / meter / credit) already in [body] are kept: a check-out can
        // be refused by several gates in turn and every retry carries all approved overrides.
        return JsonObject(body + extra)
    }
}

/**
 * Meter of a unit (`GET /assets/{id}/meter-readings` → `meter`, or `scan/resolve` asset fields)
 * and the client-side checks run before a hand-over is queued.
 */
data class MeterInfo(
    /** hours | distance | null (no meter) */
    val kind: String?,
    /** required | optional | none */
    val requirement: String,
    /** hours | km | mi */
    val unit: String?,
    val maxPerDay: Double?,
    /** Latest reading (value, source) — check-in compares with the check-out reading. */
    val lastValue: BigDecimal?,
    val lastSource: String?,
) {
    val hasMeter: Boolean get() = kind != null && kind != "none" && requirement != "none"
    val required: Boolean get() = hasMeter && requirement == "required"

    enum class Check { OK, REQUIRED, INVALID, BELOW_CHECKOUT, BELOW_PREVIOUS }

    /**
     * [direction] out | in. Check-in below the latest check-out reading is refused by the server
     * (409 `meter_below_checkout`, override with reason); check-out below the last reading is only
     * a warning (meters can be replaced).
     */
    fun check(input: String, direction: String): Check {
        val t = input.replace(',', '.').trim()
        if (t.isEmpty()) return if (required) Check.REQUIRED else Check.OK
        val v = t.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return Check.INVALID
        val last = lastValue ?: return Check.OK
        if (v >= last) return Check.OK
        return if (direction == "in" && lastSource == "checkout") Check.BELOW_CHECKOUT else Check.BELOW_PREVIOUS
    }

    companion object {
        fun parse(o: JsonObject?): MeterInfo? {
            val m = o?.obj("meter") ?: return null
            val last = o.objs("items").firstOrNull()
            return MeterInfo(
                kind = m.str("kind"),
                requirement = m.str("requirement") ?: if (m.str("kind") != null) "optional" else "none",
                unit = m.str("unit"),
                maxPerDay = m.double("max_per_day"),
                lastValue = last?.str("value")?.toBigDecimalOrNull(),
                lastSource = last?.str("source"),
            )
        }

        /** From a `scan/resolve` asset (no history). */
        fun ofAsset(a: JsonObject?): MeterInfo? {
            a ?: return null
            val kind = a.str("meter_kind") ?: return null
            return MeterInfo(kind, a.str("meter_requirement") ?: "optional", a.str("meter_unit"), null, null, null)
        }
    }
}

/**
 * Pre-save checks of the hand-over screen (wave 2). The server stays authoritative: these only
 * stop a hand-over that is known to be refused, using the last *fresh* online answers (an unknown
 * or stale status never blocks an offline hand-over; the queued item then reports any gate).
 */
object HandoverChecks {

    enum class Blocker {
        /** The unit requires a reading and none was entered. */
        METER_REQUIRED,
        /** Not a non-negative number. */
        METER_INVALID,
        /** Check-in reading below the check-out reading: needs an override reason. */
        METER_NEEDS_REASON,
        /** … and the user may not override it. */
        METER_NOT_PERMITTED,
        /** The agreement must be signed before check-out (sign now or override). */
        SIGNATURE,
        /** Credit / documents block: needs an override reason. */
        CREDIT_NEEDS_REASON,
        /** … and the user may not override it. */
        CREDIT_NOT_PERMITTED,
    }

    data class Input(
        val direction: String,
        val meter: MeterInfo?,
        val meterText: String,
        val meterOverrideReason: String,
        val canOverrideMeter: Boolean,
        /** null = unknown / stale. */
        val esign: Esign.Status?,
        val overrideSignature: Boolean,
        /** null = unknown / stale (offline). */
        val credit: CreditCheck?,
        val creditOverrideReason: String,
        val canOverrideCredit: Boolean,
    )

    /** How long an online answer counts as current for blocking. */
    const val FRESH_MS = 15 * 60_000L

    fun fresh(at: Long?, now: Long): Boolean = at != null && now - at in 0..FRESH_MS

    fun blockers(i: Input): List<Blocker> = buildList {
        i.meter?.takeIf { it.hasMeter }?.let { m ->
            when (m.check(i.meterText, i.direction)) {
                MeterInfo.Check.REQUIRED -> add(Blocker.METER_REQUIRED)
                MeterInfo.Check.INVALID -> add(Blocker.METER_INVALID)
                MeterInfo.Check.BELOW_CHECKOUT -> when {
                    !i.canOverrideMeter -> add(Blocker.METER_NOT_PERMITTED)
                    i.meterOverrideReason.isBlank() -> add(Blocker.METER_NEEDS_REASON)
                }
                else -> Unit
            }
        } ?: run {
            val t = i.meterText.replace(',', '.').trim()
            if (t.isNotEmpty() && t.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } == null) add(Blocker.METER_INVALID)
        }
        if (i.direction == "out") {
            val e = i.esign
            if (e != null && e.blocksCheckout && !i.overrideSignature &&
                e.requests.none { r -> r.signers.any { it.status == Esign.STATUS_SIGNED_LOCAL } }
            ) add(Blocker.SIGNATURE)
            val c = i.credit
            if (c != null && !c.allowed) when {
                !(i.canOverrideCredit && c.canOverride) -> add(Blocker.CREDIT_NOT_PERMITTED)
                i.creditOverrideReason.isBlank() -> add(Blocker.CREDIT_NEEDS_REASON)
            }
        }
    }

    /** The meter override is sent only when the reading actually needs it. */
    fun meterOverrideNeeded(i: Input): Boolean =
        i.meter?.check(i.meterText, i.direction) == MeterInfo.Check.BELOW_CHECKOUT
}
