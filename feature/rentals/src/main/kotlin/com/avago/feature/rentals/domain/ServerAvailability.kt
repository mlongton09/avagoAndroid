package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * `GET /rentals/availability/search` response (fleet.rs `availability_search`):
 * `{from, to, qty, currency, confirmed, classes:[{class_id, class_name, total_units, free_units,
 * enough, price, price_error}], units:[{asset_id, name, …, available, ready, readiness[],
 * maintenance_open, conflicts[], next_free_at}]}`.
 *
 * `price` is the serialised pricing `Priced` (`{currency, lines, subtotal_minor, discount_minor,
 * tax_minor, total_minor, tax_summary, tax_exemption, deposit_required_minor}`), or `null` with a
 * `price_error` message. (The stage A doc example shows `{lines, totals}`; the Rust struct is
 * authoritative and both are accepted.)
 */
object ServerAvailability {

    data class ServerClass(
        val classId: String?,
        val name: String,
        val total: Int,
        val free: Int,
        val enough: Boolean,
        val price: Money?,
        val priceError: String?,
    ) {
        val priceMinor: Long? get() = price?.minor
        val currency: String? get() = price?.currency
    }

    data class ServerUnit(
        val assetId: String,
        val name: String,
        val classId: String?,
        val available: Boolean,
        val ready: Boolean,
        val readiness: List<String>,
        val conflicts: Int,
        val nextFreeAt: Instant?,
    )

    data class Result(val currency: String?, val qty: Int, val classes: List<ServerClass>, val units: List<ServerUnit>)

    fun parse(res: JsonObject): Result {
        val currency = res.str("currency")?.trim()
        val classes = res.objs("classes").map { c ->
            val price = c.obj("price")
            val cur = price?.str("currency")?.trim() ?: currency
            val total = price?.long("total_minor") ?: price?.obj("totals")?.long("total_minor")
            ServerClass(
                classId = c.str("class_id"),
                name = c.str("class_name") ?: "",
                total = c.long("total_units")?.toInt() ?: 0,
                free = c.long("free_units")?.toInt() ?: 0,
                enough = c.bool("enough") ?: false,
                price = Money.of(total, cur),
                priceError = c.str("price_error"),
            )
        }
        val units = res.objs("units").mapNotNull { u ->
            ServerUnit(
                assetId = u.str("asset_id") ?: return@mapNotNull null,
                name = u.str("name") ?: "",
                classId = u.str("rental_class_id"),
                available = u.bool("available") ?: false,
                ready = u.bool("ready") ?: true,
                readiness = u.strings("readiness"),
                conflicts = u.objs("conflicts").size,
                nextFreeAt = u.instant("next_free_at"),
            )
        }
        return Result(currency, res.long("qty")?.toInt() ?: 1, classes, units)
    }
}
