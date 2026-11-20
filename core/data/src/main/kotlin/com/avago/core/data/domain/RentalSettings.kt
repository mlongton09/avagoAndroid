package com.avago.core.data.domain

import com.avago.core.data.rental.RatePolicy
import com.avago.core.data.rental.RateValues
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.ZoneId

/**
 * Account rental settings: the account-scope `config_overrides` payload of domain `rental`
 * (server `common.rs` `Settings::from_payload`); missing keys take the server defaults.
 */
data class RentalSettings(
    val defaultCurrency: String = "USD",
    val dayMode: String = "24h",
    val graceMinutes: Int = 59,
    val weekend: RatePolicy.WeekendWindow? = null,
    val monthMode: String = "4week",
    val quoteValidDays: Int = 14,
    val holdMinutes: Int = 1440,
    val maxDiscountBp: Int = 1500,
    val defaultBufferMinutes: Int = 0,
) {
    /** The pricing policy in [zone] (the location's time zone, UTC without one). */
    fun pricingPolicy(zone: ZoneId): RatePolicy = RatePolicy(
        calendarDays = dayMode == "calendar",
        graceMinutes = graceMinutes.toLong(),
        calendarMonths = monthMode == "calendar",
        weekend = weekend,
        zone = zone,
    )

    companion object {
        const val DOMAIN = "rental"

        fun parse(payload: String?): RentalSettings {
            val p = SettingsPayload.parse(payload)
            val d = RentalSettings()
            return RentalSettings(
                defaultCurrency = p.str("default_currency")?.uppercase() ?: d.defaultCurrency,
                dayMode = p.str("day_mode") ?: d.dayMode,
                graceMinutes = p.int("grace_minutes") ?: d.graceMinutes,
                weekend = (p["weekend_window"] as? JsonObject)?.let(RatePolicy.WeekendWindow::parse),
                monthMode = p.str("month_mode") ?: d.monthMode,
                quoteValidDays = p.int("quote_valid_days") ?: d.quoteValidDays,
                holdMinutes = p.int("hold_minutes") ?: d.holdMinutes,
                maxDiscountBp = p.int("max_discount_bp") ?: d.maxDiscountBp,
                defaultBufferMinutes = p.int("default_buffer_minutes") ?: d.defaultBufferMinutes,
            )
        }
    }
}

/** Owner scope policy: domain `scope_policy` (server `scope.rs` `ScopeSettings`). */
data class ScopePolicy(
    val verifiedRenterAboveMinor: Long? = null,
    val blockUnremediedRecalls: Boolean = true,
    /** Post-rental charges are accepted up to this many days after the return. */
    val postChargeWindowDays: Int = 90,
    val expiryAlertDays: Int = 30,
) {
    companion object {
        const val DOMAIN = "scope_policy"

        fun parse(payload: String?): ScopePolicy {
            val p = SettingsPayload.parse(payload)
            val d = ScopePolicy()
            return ScopePolicy(
                verifiedRenterAboveMinor = p.long("verified_renter_above_minor"),
                blockUnremediedRecalls = p.bool("block_unremedied_recalls") ?: d.blockUnremediedRecalls,
                postChargeWindowDays = p.int("post_charge_window_days") ?: d.postChargeWindowDays,
                expiryAlertDays = p.int("expiry_alert_days") ?: d.expiryAlertDays,
            )
        }
    }
}

/** Collection / gate settings: domain `rental_collections` (server `credit.rs`). */
data class CollectionSettings(
    val creditCheckReserve: Boolean = true,
    val creditCheckCheckout: Boolean = true,
    val docsCheckCheckout: Boolean = true,
    /** Customer documents expiring within this many days are flagged (and e-mailed by the server). */
    val docExpiryAlertDays: Int = 30,
    val requiredDocKinds: List<String> = emptyList(),
) {
    /** Whether a document expiring on [expiresOn] is expired or due within the alert window on [today]. */
    fun expiringSoon(expiresOn: LocalDate?, today: LocalDate): Boolean =
        expiresOn != null && !expiresOn.isAfter(today.plusDays(docExpiryAlertDays.toLong()))

    companion object {
        const val DOMAIN = "rental_collections"

        fun parse(payload: String?): CollectionSettings {
            val p = SettingsPayload.parse(payload)
            val d = CollectionSettings()
            return CollectionSettings(
                creditCheckReserve = p.bool("credit_check_reserve") ?: d.creditCheckReserve,
                creditCheckCheckout = p.bool("credit_check_checkout") ?: d.creditCheckCheckout,
                docsCheckCheckout = p.bool("docs_check_checkout") ?: d.docsCheckCheckout,
                docExpiryAlertDays = p.int("doc_expiry_alert_days") ?: d.docExpiryAlertDays,
                requiredDocKinds = (p["required_doc_kinds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { v -> v.isString }?.content }
                    .orEmpty(),
            )
        }
    }
}

/** A synced rate card (`rental_rate_cards` with its `rental_rate_tiers` folded into [rates]). */
data class RateCardRecord(
    val id: String,
    val classId: String?,
    val assetId: String?,
    val customerPartyId: String?,
    val locationId: String?,
    val currency: String,
    val active: Boolean,
    /** Inclusive first day of validity, or null for open. */
    val validFrom: LocalDate?,
    /** Exclusive end day of validity, or null for open. */
    val validUntil: LocalDate?,
    val priority: Int,
    val createdAtMillis: Long,
    val rates: RateValues,
)

interface RentalSettingsRepository {
    fun observeSettings(): Flow<RentalSettings>
    fun observeScopePolicy(): Flow<ScopePolicy>
    fun observeCollectionSettings(): Flow<CollectionSettings>
    fun observeRateCards(): Flow<List<RateCardRecord>>
}

/** Lenient reader of a settings payload: blank strings and wrong types count as missing. */
internal class SettingsPayload private constructor(private val o: JsonObject) {
    operator fun get(key: String): JsonElement? = o[key]?.takeIf { it !is JsonNull }

    fun str(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.ifEmpty { null }

    fun long(key: String): Long? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    fun int(key: String): Int? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    fun bool(key: String): Boolean? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    companion object {
        fun parse(payload: String?): SettingsPayload =
            SettingsPayload(payload?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: JsonObject(emptyMap()))
    }
}
