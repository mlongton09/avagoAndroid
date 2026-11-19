package com.avago.feature.rentals.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parser for the AAMVA DL/ID card design standard carried in the PDF417 barcode on the back
 * of North-American driving licences (spec §14 DocumentCapture). Only the fields the rental
 * customer form needs are extracted; unknown elements are ignored. Nothing is persisted from
 * here; the staff member reviews every value before saving.
 */
object Aamva {

    data class License(
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val number: String?,
        val dateOfBirth: LocalDate?,
        val expiresOn: LocalDate?,
        val issuedOn: LocalDate?,
        val street: String?,
        val city: String?,
        val region: String?,
        val postalCode: String?,
        val country: String?,
        val classCodes: String?,
    ) {
        val fullName: String
            get() = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        val address: String
            get() = listOfNotNull(street, listOfNotNull(city, region, postalCode).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }, country)
                .filter { it.isNotBlank() }.joinToString(", ")
    }

    /** True when [raw] looks like an AAMVA payload (compliance indicator `@` and `ANSI ` / `AAMVA` header). */
    fun looksLikeAamva(raw: String): Boolean =
        raw.trimStart().startsWith("@") && (raw.contains("ANSI ") || raw.contains("AAMVA"))

    /** ISO 3166-1 alpha-3 → alpha-2 for the AAMVA jurisdictions (`issuing_country` is two letters). */
    fun countryAlpha2(country: String?): String? = when (val c = country?.trim()?.uppercase()) {
        null, "" -> null
        "USA" -> "US"
        "CAN" -> "CA"
        "MEX" -> "MX"
        else -> c.takeIf { it.length == 2 }
    }

    fun parse(raw: String): License? {
        if (!looksLikeAamva(raw)) return null
        val fields = HashMap<String, String>()
        // Elements are 3-char IDs at the start of a line (LF/CR separated); the first element of
        // a subfile follows its "DL"/"ID" type code directly.
        raw.split('\n', '\r').forEach { line0 ->
            var line = line0.trim()
            val dl = line.indexOf("DLDAQ").takeIf { it >= 0 } ?: line.indexOf("IDDAQ")
            if (dl >= 0) line = line.substring(dl + 2)
            if (line.length > 3 && line[0] == 'D' && line.substring(0, 3).all { it.isLetterOrDigit() }) {
                fields.putIfAbsent(line.substring(0, 3), line.substring(3).trim())
            }
        }
        if (fields.isEmpty()) return null
        val country = fields["DCG"]?.takeIf { it.isNotBlank() }
        val canada = country == "CAN"
        fun date(id: String) = fields[id]?.let { parseDate(it, canada) }
        val last = fields["DCS"] ?: fields["DAB"]
        var first = fields["DAC"] ?: fields["DCT"]
        var middle = fields["DAD"]
        // Older versions put "FIRST,MIDDLE" into DCT.
        if (first != null && first.contains(',') && middle == null) {
            val parts = first.split(',')
            first = parts[0].trim()
            middle = parts.getOrNull(1)?.trim()
        }
        return License(
            firstName = first?.clean(),
            middleName = middle?.clean(),
            lastName = last?.clean(),
            number = fields["DAQ"]?.clean(),
            dateOfBirth = date("DBB"),
            expiresOn = date("DBA"),
            issuedOn = date("DBD"),
            street = fields["DAG"]?.clean(),
            city = fields["DAI"]?.clean(),
            region = fields["DAJ"]?.clean(),
            postalCode = fields["DAK"]?.clean()?.let { normalizePostal(it) },
            country = country,
            classCodes = fields["DCA"]?.clean(),
        )
    }

    private fun String.clean(): String? =
        trim().takeIf { it.isNotEmpty() && !it.equals("NONE", true) && !it.equals("unavl", true) }

    /** US 9-digit ZIP "123450000" → "12345"; "123456789" → "12345-6789". */
    private fun normalizePostal(p: String): String {
        val digits = p.trim()
        if (digits.length == 9 && digits.all { it.isDigit() }) {
            val ext = digits.substring(5)
            return if (ext == "0000") digits.substring(0, 5) else "${digits.substring(0, 5)}-$ext"
        }
        return digits
    }

    private val MDY = DateTimeFormatter.ofPattern("MMddyyyy")
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** US cards use MMDDCCYY, Canadian cards CCYYMMDD; fall back to whichever parses. */
    fun parseDate(v: String, canada: Boolean = false): LocalDate? {
        val s = v.trim().take(8)
        if (s.length != 8 || !s.all { it.isDigit() }) return null
        val order = if (canada) listOf(YMD, MDY) else listOf(MDY, YMD)
        for (f in order) {
            runCatching { return LocalDate.parse(s, f) }
        }
        return null
    }
}
