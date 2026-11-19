package com.avago.feature.assets.model

import com.avago.core.network.json.bool
import com.avago.core.network.json.int
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

// Feature I (scope) per-asset extras. Vehicle details are read from the v2 store; NHTSA recalls are a
// server query (AvagoSvc `routes/scope.rs`).

/** Calibration / registration / insurance expiry state (`ok | due_soon | overdue`). */
enum class ExpiryStatus {
    Ok, DueSoon, Overdue;

    companion object {
        fun from(s: String?): ExpiryStatus? = when (s) {
            "ok" -> Ok
            "due_soon" -> DueSoon
            "overdue" -> Overdue
            else -> null
        }

        /** Local expiry state of an ISO date: overdue before [today], due soon within [soonDays]. */
        fun of(date: String?, today: LocalDate = LocalDate.now(), soonDays: Long = 30): ExpiryStatus? {
            val d = date?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
            return when {
                d.isBefore(today) -> Overdue
                !d.isAfter(today.plusDays(soonDays)) -> DueSoon
                else -> Ok
            }
        }
    }
}

private fun JsonObject.data(): JsonObject = obj("data") ?: this

data class Recall(
    val campaignNumber: String,
    val component: String?,
    val summary: String?,
    val consequence: String?,
    val remedy: String?,
    val reportReceivedDate: String?,
    val parkIt: Boolean,
    val parkOutside: Boolean,
    val remediedAt: String?,
    val remedyNote: String?,
) {
    val open: Boolean get() = remediedAt == null

    companion object {
        fun from(o: JsonObject): Recall? = o.str("campaign_number")?.let {
            Recall(
                campaignNumber = it,
                component = o.str("component"),
                summary = o.str("summary"),
                consequence = o.str("consequence"),
                remedy = o.str("remedy"),
                reportReceivedDate = o.str("report_received_date"),
                parkIt = o.bool("park_it") ?: false,
                parkOutside = o.bool("park_outside") ?: false,
                remediedAt = o.str("remedied_at"),
                remedyNote = o.str("remedy_note"),
            )
        }
    }
}

data class RecallList(
    /** False when the asset is not a road vehicle or lacks make/model/year. */
    val supported: Boolean,
    val stale: Boolean,
    val unavailable: Boolean,
    val fetchedAt: String?,
    val openCount: Int,
    val blocksRentals: Boolean,
    val recalls: List<Recall>,
) {
    companion object {
        fun from(root: JsonObject): RecallList {
            val o = root.data()
            val recalls = o.objs("recalls").mapNotNull { Recall.from(it) }
            return RecallList(
                supported = o.bool("supported") ?: false,
                stale = o.bool("stale") ?: false,
                unavailable = o.bool("unavailable") ?: false,
                fetchedAt = o.str("fetched_at"),
                openCount = o.int("open_count") ?: recalls.count { it.open },
                blocksRentals = o.bool("block_unremedied_recalls") ?: false,
                recalls = recalls,
            )
        }
    }
}
