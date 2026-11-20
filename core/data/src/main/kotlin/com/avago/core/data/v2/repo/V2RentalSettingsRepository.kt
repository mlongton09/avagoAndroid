package com.avago.core.data.v2.repo

import com.avago.core.data.domain.CollectionSettings
import com.avago.core.data.domain.RateCardRecord
import com.avago.core.data.domain.RentalSettings
import com.avago.core.data.domain.RentalSettingsRepository
import com.avago.core.data.domain.ScopePolicy
import com.avago.core.data.rental.RateValues
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Account rental settings, owner scope policy, collection settings and rate cards from the synced projections. */
@Singleton
class V2RentalSettingsRepository @Inject constructor(
    private val store: V2StoreAccess,
) : RentalSettingsRepository {

    override fun observeSettings(): Flow<RentalSettings> =
        store.observe(CONFIG, RentalSettings()) { conn -> RentalSettings.parse(accountPayload(conn, RentalSettings.DOMAIN)) }

    override fun observeScopePolicy(): Flow<ScopePolicy> =
        store.observe(CONFIG, ScopePolicy()) { conn -> ScopePolicy.parse(accountPayload(conn, ScopePolicy.DOMAIN)) }

    override fun observeCollectionSettings(): Flow<CollectionSettings> =
        store.observe(CONFIG, CollectionSettings()) { conn -> CollectionSettings.parse(accountPayload(conn, CollectionSettings.DOMAIN)) }

    override fun observeRateCards(): Flow<List<RateCardRecord>> =
        store.observe(setOf("rental_rate_cards", "rental_rate_tiers"), emptyList()) { conn -> rateCards(conn) }

    companion object {
        private val CONFIG = setOf("config_overrides")

        /** The account-wide settings row of [domain] (no subtype, type, locale, user or asset qualifier). */
        fun accountPayload(conn: V2Connection, domain: String): String? = conn.query(
            "SELECT payload FROM config_overrides WHERE domain = ? AND scope = 'account' AND subtype IS NULL " +
                "AND asset_type IS NULL AND locale IS NULL AND user_id IS NULL AND asset_id IS NULL " +
                "ORDER BY updated_at DESC LIMIT 1",
            listOf(domain),
        ).firstOrNull()?.strOrNull("payload")

        fun rateCards(conn: V2Connection): List<RateCardRecord> {
            val tiers = conn.query(
                "SELECT rate_card_id, period_code, amount_minor FROM rental_rate_tiers WHERE rate_card_id IS NOT NULL",
                emptyList(),
            ).groupBy({ it.str("rate_card_id") }, { it.str("period_code") to (it.longOrNull("amount_minor") ?: 0L) })
            return conn.query("SELECT * FROM rental_rate_cards", emptyList()).map { r ->
                val id = r.str("id")
                RateCardRecord(
                    id = id,
                    classId = r.strOrNull("class_id"),
                    assetId = r.strOrNull("asset_id"),
                    customerPartyId = r.strOrNull("customer_party_id"),
                    locationId = r.strOrNull("location_id"),
                    currency = r.str("currency"),
                    active = r.bool("active"),
                    validFrom = r.strOrNull("valid_during_start")?.let(LocalDate::parse),
                    validUntil = r.strOrNull("valid_during_end")?.let(LocalDate::parse),
                    priority = r.intOrNull("priority") ?: 0,
                    createdAtMillis = r.longOrNull("created_at") ?: 0L,
                    rates = RateValues.fromTiers(tiers[id].orEmpty()),
                )
            }
        }
    }
}
