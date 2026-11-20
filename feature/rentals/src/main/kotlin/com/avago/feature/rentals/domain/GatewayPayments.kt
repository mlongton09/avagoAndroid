package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * G6 staff gateway actions on an order (AvagoSvc docs/rentals-api-wave2-stripe.md): charge the
 * saved card, capture/release a deposit hold and refund a gateway payment. Everything here is
 * pure; nothing is shown unless the server reports `online_payments_enabled` (the gateway is
 * built but off by default, and the app never configures it).
 */
object GatewayPayments {

    /** Stripe's 8-digit amount ceiling; anything larger is rejected before a request is made. */
    const val MAX_MINOR: Long = 99_999_999L

    const val PURPOSE_PAYMENT = "payment"
    const val PURPOSE_DEPOSIT = "deposit"

    data class Card(val brand: String?, val last4: String?, val exp: String?)

    data class Authorization(
        val id: String,
        val amount: Money,
        val expiresAt: Instant?,
        val cardBrand: String?,
        val cardLast4: String?,
        val expiringSoon: Boolean,
    )

    data class Refundable(
        val id: String,
        val kind: String?,
        val amount: Money,
        val refundable: Money,
        val disputeStatus: String?,
    ) {
        val canRefund: Boolean get() = refundable.minor > 0
    }

    /** Server-side gateway state of one order (`GET /orders/{id}/payments`). */
    data class Gateway(
        val card: Card?,
        val depositCaptureMode: String?,
        /** `can.charge` (rental.payment_charge): charge saved card, capture holds. Absent → false. */
        val canCharge: Boolean,
        /** `can.refund` (rental.refund): release holds, provider refunds. Absent → false. */
        val canRefund: Boolean,
        val authorizations: List<Authorization>,
        val refundable: List<Refundable>,
    ) {
        val canChargeCard: Boolean get() = canCharge && card != null
        val offersDeposit: Boolean get() = depositCaptureMode == "authorize" || depositCaptureMode == "capture"
        val isEmpty: Boolean get() = !canChargeCard && authorizations.isEmpty() && refundable.none { it.canRefund }

        companion object {
            val NONE = Gateway(null, null, false, false, emptyList(), emptyList())
        }
    }

    /** Money movement fails closed: without `online_payments_enabled` and `can`, nothing is offered. */
    fun parse(o: JsonObject?, currency: String?, enabled: Boolean): Gateway {
        if (o == null || !enabled) return Gateway.NONE
        val can = o.obj("can")
        val card = o.obj("card_on_file")?.let { Card(it.str("brand"), it.str("last4"), it.str("exp")) }
        return Gateway(
            card = card,
            depositCaptureMode = o.str("deposit_capture_mode"),
            canCharge = can?.bool("charge") == true,
            canRefund = can?.bool("refund") == true,
            authorizations = o.objs("authorizations").mapNotNull { a ->
                val cur = a.str("currency")?.trim() ?: currency
                Authorization(
                    id = a.str("id") ?: return@mapNotNull null,
                    amount = a.money("amount_minor", cur)?.takeIf { it.minor > 0 } ?: return@mapNotNull null,
                    expiresAt = a.instant("authorization_expires_at"),
                    cardBrand = a.str("card_brand"),
                    cardLast4 = a.str("card_last4"),
                    expiringSoon = a.bool("expiring_soon") == true,
                )
            },
            refundable = o.objs("refundable").mapNotNull { r ->
                val cur = r.str("currency")?.trim() ?: currency
                val amount = r.money("amount_minor", cur) ?: return@mapNotNull null
                Refundable(
                    id = r.str("id") ?: return@mapNotNull null,
                    kind = r.str("kind"),
                    amount = amount,
                    refundable = r.money("refundable_minor", cur)?.let { if (it.minor < 0) Money(0, it.currency) else it } ?: return@mapNotNull null,
                    disputeStatus = r.str("dispute_status"),
                )
            },
        )
    }

    /**
     * Strict user amount in minor units: positive, no finer than the currency allows, at most
     * [max] (the hold / refundable amount) and [MAX_MINOR]. Null when invalid.
     */
    fun amountMinor(input: String, currency: String, max: Long? = null): Long? {
        val v = MoneyFormatter.parsePlainMinor(input, currency) ?: return null
        val ceiling = minOf(max ?: MAX_MINOR, MAX_MINOR)
        return v.takeIf { it in 1..ceiling }
    }

    fun chargeBody(amountMinor: Long, purpose: String, clientUuid: String): JsonObject = buildJsonObject {
        require(amountMinor in 1..MAX_MINOR) { "amount out of range" }
        require(purpose == PURPOSE_PAYMENT || purpose == PURPOSE_DEPOSIT) { "invalid purpose" }
        put("amount_minor", amountMinor)
        put("purpose", purpose)
        put("client_uuid", clientUuid)
    }

    /** A full capture omits the amount so the server also captures any stored surcharge share. */
    fun captureBody(amountMinor: Long, authorized: Long): JsonObject = buildJsonObject {
        require(amountMinor in 1..authorized) { "amount out of range" }
        if (amountMinor != authorized) put("amount_minor", amountMinor)
    }

    fun refundBody(amountMinor: Long, refundable: Long, reason: String): JsonObject = buildJsonObject {
        require(amountMinor in 1..refundable) { "amount out of range" }
        require(reason.isNotBlank()) { "reason required" }
        put("amount_minor", amountMinor)
        put("reason", reason.trim())
    }

    enum class ChargeStatus { SUCCEEDED, HOLD_PLACED, PROCESSING, REQUIRES_ACTION, UNKNOWN }

    data class ChargeResult(val status: ChargeStatus, val payUrl: String?, val paymentRequestId: String?)

    fun parseCharge(o: JsonObject): ChargeResult = ChargeResult(
        status = when (o.str("status")) {
            "succeeded" -> ChargeStatus.SUCCEEDED
            "requires_capture" -> ChargeStatus.HOLD_PLACED
            "processing" -> ChargeStatus.PROCESSING
            "requires_action" -> ChargeStatus.REQUIRES_ACTION
            else -> ChargeStatus.UNKNOWN
        },
        payUrl = o.str("pay_url")?.takeIf { it.isNotBlank() },
        paymentRequestId = o.str("payment_request_id"),
    )

    /**
     * Whether the money may have moved although no success arrived: no answer at all
     * ([status] null), or 408/425/429/5xx (`provider_unavailable` is documented as safe to retry
     * with the same key). Other refusals (402 declined, 409 state, 403, 400) are definitive.
     */
    fun isUncertain(status: Int?): Boolean =
        status == null || status == 408 || status == 425 || status == 429 || status >= 500

    /**
     * Stable idempotency keys for money-moving calls. A key is reused until a definitive answer
     * arrives, so retrying after a lost response replays the same provider request instead of
     * charging twice. While an attempt is unresolved, a different body for the same scope is
     * [Begin.Blocked] (the same key with other parameters would be rejected by the provider, and
     * a fresh key could double-charge). Scopes include the account id.
     */
    class Attempts(initial: Map<String, Attempt> = emptyMap()) {
        data class Attempt(val key: String, val fingerprint: String, val amountMinor: Long?)

        sealed interface Begin {
            data class Go(val key: String) : Begin
            data class Blocked(val pending: Attempt) : Begin
        }

        private val pending = LinkedHashMap(initial)

        fun begin(scope: String, fingerprint: String, amountMinor: Long?, newKey: () -> String): Begin {
            val p = pending[scope]
            return when {
                p == null -> Attempt(newKey(), fingerprint, amountMinor).also { pending[scope] = it }.let { Begin.Go(it.key) }
                p.fingerprint == fingerprint -> Begin.Go(p.key)
                else -> Begin.Blocked(p)
            }
        }

        /** Keeps the attempt when [uncertain]; otherwise forgets it (only if [key] is still current). */
        fun settle(scope: String, key: String, uncertain: Boolean) {
            if (!uncertain && pending[scope]?.key == key) pending.remove(scope)
        }

        fun pending(scope: String): Attempt? = pending[scope]

        fun encode(): String = RentalJson.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                pending.mapValues { (_, a) ->
                    buildJsonObject {
                        put("key", a.key)
                        put("fp", a.fingerprint)
                        a.amountMinor?.let { put("amount", it) }
                    }
                },
            ),
        )

        companion object {
            fun decode(s: String?): Attempts {
                val o = s?.let { runCatching { parseObj(it) }.getOrNull() } ?: return Attempts()
                return Attempts(
                    o.mapNotNull { (scope, v) ->
                        val a = v as? JsonObject ?: return@mapNotNull null
                        val key = a.str("key") ?: return@mapNotNull null
                        val fp = a.str("fp") ?: return@mapNotNull null
                        scope to Attempt(key, fp, (a["amount"] as? JsonPrimitive)?.content?.toLongOrNull())
                    }.toMap(),
                )
            }

            fun scope(accountId: String, action: String, target: String) = "$accountId|$action|$target"
        }
    }
}
