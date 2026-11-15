package com.avago.feature.rentals.domain

/**
 * Classifies a scanned code (QR, barcode, NFC tag text, OCR text) for the rental Scan screen.
 * Asset lookup itself happens against Room (asset id, barcode, VIN/serial).
 */
object ScanCode {

    sealed interface Kind {
        data class AssetId(val id: String) : Kind
        data class OrderNumber(val number: String) : Kind
        data class Licence(val raw: String) : Kind
        data class Text(val value: String) : Kind

        /** Wave 2 label short code (`U` + hex for units, `P` + hex for bulk parts); resolved online. */
        data class ShortCode(val code: String) : Kind
    }

    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val ASSET_URL = Regex("/(?:a|assets?)/([0-9a-fA-F-]{36})")
    private val SHORT_CODE = Regex("^[UP][0-9A-F]{8,16}$", RegexOption.IGNORE_CASE)
    private val ORDER = Regex("^(?:R|RO|ORD)-?\\d{3,}$", RegexOption.IGNORE_CASE)

    fun classify(raw: String): Kind {
        val v = raw.trim()
        if (Aamva.looksLikeAamva(v)) return Kind.Licence(v)
        ASSET_URL.find(v)?.let { return Kind.AssetId(it.groupValues[1].lowercase()) }
        if (UUID.matches(v)) return Kind.AssetId(v.lowercase())
        if (ORDER.matches(v)) return Kind.OrderNumber(v.uppercase())
        if (SHORT_CODE.matches(v)) return Kind.ShortCode(v.uppercase())
        return Kind.Text(v)
    }
}

/** `GET /scan/resolve` answer (G1 §2): a unit or a bulk part, with the unit's current / next order. */
data class ScanResolution(
    val type: String,
    val code: String?,
    val assetId: String?,
    val assetName: String?,
    val rentalClassId: String?,
    val currentOrderId: String?,
    val currentOrderNumber: String?,
    val partId: String?,
    val partName: String?,
    val partNumber: String?,
) {
    val isAsset: Boolean get() = type == "asset" && assetId != null
    val isPart: Boolean get() = type == "part" && partId != null

    companion object {
        fun parse(o: kotlinx.serialization.json.JsonObject?): ScanResolution? {
            o ?: return null
            val a = o.obj("asset")
            val p = o.obj("part")
            val type = o.str("type") ?: if (a != null) "asset" else if (p != null) "part" else return null
            return ScanResolution(
                type = type,
                code = o.str("code"),
                assetId = a?.str("asset_id") ?: a?.str("id"),
                assetName = a?.str("name"),
                rentalClassId = a?.str("rental_class_id"),
                currentOrderId = a?.obj("current")?.str("order_id"),
                currentOrderNumber = a?.obj("current")?.str("order_number"),
                partId = p?.str("part_id") ?: p?.str("id"),
                partName = p?.str("name"),
                partNumber = p?.str("part_number"),
            )
        }
    }
}
