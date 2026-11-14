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
    }

    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val ASSET_URL = Regex("/(?:a|assets?)/([0-9a-fA-F-]{36})")
    private val ORDER = Regex("^(?:R|RO|ORD)-?\\d{3,}$", RegexOption.IGNORE_CASE)

    fun classify(raw: String): Kind {
        val v = raw.trim()
        if (Aamva.looksLikeAamva(v)) return Kind.Licence(v)
        ASSET_URL.find(v)?.let { return Kind.AssetId(it.groupValues[1].lowercase()) }
        if (UUID.matches(v)) return Kind.AssetId(v.lowercase())
        if (ORDER.matches(v)) return Kind.OrderNumber(v.uppercase())
        return Kind.Text(v)
    }
}
