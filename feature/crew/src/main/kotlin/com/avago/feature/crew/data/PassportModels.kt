package com.avago.feature.crew.data

import com.avago.core.network.json.bool
import com.avago.core.network.json.int
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import kotlinx.serialization.json.JsonObject
import java.net.URI

// QR asset passport (feature B): public view, pre-use inspection, fault report, transfer claim.

/** What a scanned QR code or opened link points at. */
sealed interface PassportLink {
    data class Passport(val tokenId: String) : PassportLink
    data class Claim(val token: String) : PassportLink

    companion object {
        private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val TOKEN_RE = Regex("^[A-Za-z0-9_-]{16,128}$")

        /**
         * Asset QR labels encode `https://www.avagomate.com/a/{token_id}`; the passport page and
         * `avago://passport/{token_id}` are accepted too, as is a bare token id. Transfer links are
         * `{web}/passport/claim/{token}`.
         */
        fun parse(raw: String?): PassportLink? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (UUID_RE.matches(text)) return Passport(text.lowercase())
            if (!text.contains("://")) return null
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            val segs = buildList {
                if (uri.scheme.equals("avago", true)) uri.host?.let(::add)
                addAll(uri.path.orEmpty().split('/').filter { it.isNotEmpty() })
            }
            val claim = segs.indexOf("claim")
            if (claim > 0 && segs[claim - 1] == "passport") {
                return segs.getOrNull(claim + 1)?.takeIf { TOKEN_RE.matches(it) }?.let(::Claim)
            }
            val i = segs.indexOfFirst { it == "a" || it == "passport" || it == "p" }
            if (i < 0) return null
            return segs.getOrNull(i + 1)?.takeIf { UUID_RE.matches(it) }?.let { Passport(it.lowercase()) }
        }
    }
}

data class PassportAsset(val name: String?, val make: String?, val model: String?, val year: Int?, val assetType: String?, val vinMasked: String?) {
    val title: String get() = name ?: listOfNotNull(make, model).joinToString(" ").ifBlank { "—" }

    companion object {
        fun from(o: JsonObject?): PassportAsset? = o?.let {
            PassportAsset(it.str("name"), it.str("make"), it.str("model"), it.int("year"), it.str("asset_type"), it.str("vin_masked"))
        }
    }
}

data class ServiceEvent(val date: String?, val title: String?, val category: String?)
data class InspectionEvent(val date: String?, val result: String?)

data class PreuseItem(val id: String, val text: String, val type: String, val options: List<String>, val section: String?)

data class Passport(
    val tokenId: String,
    val visibility: String,
    val asset: PassportAsset?,
    val lastServiceDate: String?,
    val serviceEvents: List<ServiceEvent>,
    val inspections: List<InspectionEvent>,
    val canInspect: Boolean,
    val canReportFault: Boolean,
    val rentUrl: String?,
    val recallOpen: Int?,
    val checklistTitle: String?,
    val checklist: List<PreuseItem>,
) {
    companion object {
        fun from(o: JsonObject, tokenId: String): Passport {
            val actions = o.obj("actions") ?: JsonObject(emptyMap())
            val cl = o.obj("preuse_checklist")
            val items = cl?.objs("groups").orEmpty().flatMap { g ->
                g.objs("sections").flatMap { s ->
                    val title = s.str("title")
                    s.objs("items").mapNotNull { i ->
                        val id = i.str("id") ?: return@mapNotNull null
                        PreuseItem(id, i.str("text") ?: id, i.str("type") ?: "select", i.strs("options"), title)
                    }
                }
            }
            return Passport(
                tokenId = o.str("token_id") ?: tokenId,
                visibility = o.str("visibility") ?: "off",
                asset = PassportAsset.from(o.obj("asset")),
                lastServiceDate = o.str("last_service_date"),
                serviceEvents = o.objs("service_events").map { ServiceEvent(it.str("date"), it.str("title"), it.str("category")) },
                inspections = o.objs("inspections").map { InspectionEvent(it.str("date"), it.str("result")) },
                canInspect = actions.bool("preuse_inspection") == true && items.isNotEmpty(),
                canReportFault = actions.bool("fault_report") == true,
                rentUrl = actions.str("rent_url"),
                recallOpen = o.obj("recall")?.int("open_count"),
                checklistTitle = cl?.str("title"),
                checklist = items,
            )
        }

        /** Template labels may be i18n keys (`insp.item.brakes`, `insp.opt.needs_repair`); show a readable fallback. */
        fun humanize(key: String): String {
            if (!key.startsWith("insp.")) return key
            val tail = key.substringAfterLast('.').replace('_', ' ')
            return tail.replaceFirstChar { it.uppercase() }
        }
    }
}

data class TransferPreview(
    val status: String,
    val expiresAt: String?,
    val sellerName: String?,
    val asset: PassportAsset?,
    val serviceEvents: Int,
    val inspections: Int,
) {
    val claimable: Boolean get() = status == "pending" || status == "open"

    companion object {
        fun from(o: JsonObject): TransferPreview {
            val counts = o.obj("counts") ?: JsonObject(emptyMap())
            return TransferPreview(
                o.str("status") ?: "pending", o.str("expires_at"), o.str("seller_account_name"), PassportAsset.from(o.obj("asset")),
                counts.int("service_events") ?: 0, counts.int("inspections") ?: 0,
            )
        }
    }
}

data class ClaimResult(val assetId: String?, val logsCopied: Int) {
    companion object {
        fun from(o: JsonObject) = ClaimResult(o.str("asset_id"), o.obj("copied")?.int("logs") ?: 0)
    }
}
