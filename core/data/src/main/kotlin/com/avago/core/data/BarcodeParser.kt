package com.avago.core.data

import org.json.JSONObject

/**
 * Parses decoded barcode strings into Part model field values.
 *
 * Supports:
 * - GS1 Application Identifiers in parenthesised notation  (01)...(10)...
 * - GS1 Application Identifiers in concatenated notation   01...10... (with or without GS separators)
 * - EAN-13 / EAN-8 / UPC-A / UPC-E  →  GTIN
 * - Interleaved 2 of 5  →  GTIN (14-digit ITF-14) or part number
 * - Code 39  →  part number
 * - Code 128 plain  →  GTIN or part number
 * - QR Code with JSON payload  →  mapped fields
 * - QR Code plain text  →  part number
 *
 * GS1 AI → Part field mapping
 *   01 (GTIN-14)               → gtin
 *   10 (Batch/Lot)             → lotNumber
 *   11 (Production date YYMMDD)→ productionDate  (stored as YYYY-MM-DD)
 *   21 (Serial number)         → serialNumber
 *   30 (Variable count)        → quantity
 *  240 (Additional product ID) → partNumber
 *  241 (Customer part number)  → partNumber   (only if 240 absent)
 *  242 (Made-to-order var.)    → modelNumber
 *  412 (Purchased-from GLN)    → manufacturerId
 *  422 (Country of origin)     → country      (ISO 3166-1 numeric or alpha-3)
 * 8010 (Component/Part ID)     → partNumber   (only if 240/241 absent)
 * 8011 (CPID serial)           → serialNumber (only if 21 absent)
 * 8012 (Software version)      → revision
 *
 * Mirrors iOS BarcodeParser.
 */
object BarcodeParser {

    private const val GS1_GS = ''
    private const val GS1_RS = ''

    // fixed < 0 means variable-length (terminated by GS or look-ahead to next AI)
    private data class AIEntry(val ai: String, val fixed: Int, val key: String?)

    private val AI_TABLE = listOf(
        // 2-digit fixed
        AIEntry("00",   18, null),
        AIEntry("01",   14, "gtin"),
        AIEntry("02",   14, null),
        AIEntry("11",    6, "productionDate"),
        AIEntry("12",    6, null),
        AIEntry("13",    6, null),
        AIEntry("15",    6, null),
        AIEntry("16",    6, null),
        AIEntry("17",    6, null),
        AIEntry("18",    6, null),
        AIEntry("20",    2, null),
        // 2-digit variable
        AIEntry("10",   -1, "lotNumber"),
        AIEntry("21",   -1, "serialNumber"),
        AIEntry("22",   -1, null),
        AIEntry("30",   -1, "quantity"),
        AIEntry("37",   -1, null),
        // 3-digit variable
        AIEntry("240",  -1, "partNumber"),
        AIEntry("241",  -1, "partNumber"),   // fallback; handled in applyAI
        AIEntry("242",  -1, "modelNumber"),
        AIEntry("243",  -1, null),
        AIEntry("250",  -1, null),
        AIEntry("251",  -1, "revision"),
        AIEntry("400",  -1, null),
        AIEntry("401",  -1, null),
        AIEntry("403",  -1, null),
        AIEntry("420",  -1, null),
        AIEntry("421",  -1, null),
        // 3-digit fixed
        AIEntry("410",  13, null),
        AIEntry("411",  13, null),
        AIEntry("412",  13, "manufacturerId"),
        AIEntry("413",  13, null),
        AIEntry("414",  13, null),
        AIEntry("415",  13, null),
        AIEntry("416",  13, null),
        AIEntry("417",  13, null),
        AIEntry("422",   3, "country"),
        AIEntry("423",  -1, null),
        // 4-digit fixed
        AIEntry("8001",  4, null),
        AIEntry("8006", 18, null),
        AIEntry("8017", 18, null),
        AIEntry("8018", 18, null),
        // 4-digit variable
        AIEntry("7001", -1, null),
        AIEntry("8002", -1, null),
        AIEntry("8003", -1, null),
        AIEntry("8004", -1, null),
        AIEntry("8010", -1, "partNumber"),    // CPID — Component/Part Identifier
        AIEntry("8011", -1, "serialNumber"),  // CPID serial; fallback
        AIEntry("8012", -1, "revision"),
        AIEntry("8013", -1, null),
        AIEntry("8019", -1, null),
        AIEntry("8020", -1, null),
        AIEntry("90",   -1, null),
        AIEntry("91",   -1, null),
        AIEntry("92",   -1, null),
        AIEntry("93",   -1, null),
        AIEntry("94",   -1, null),
        AIEntry("95",   -1, null),
        AIEntry("96",   -1, null),
        AIEntry("97",   -1, null),
        AIEntry("98",   -1, null),
        AIEntry("99",   -1, null),
    )

    private val COUNTRY_ALPHA3 = mapOf(
        "036" to "AUS", "040" to "AUT", "056" to "BEL", "076" to "BRA",
        "124" to "CAN", "152" to "CHL", "156" to "CHN", "203" to "CZE",
        "208" to "DNK", "246" to "FIN", "250" to "FRA", "276" to "DEU",
        "300" to "GRC", "344" to "HKG", "348" to "HUN", "356" to "IND",
        "360" to "IDN", "372" to "IRL", "376" to "ISR", "380" to "ITA",
        "392" to "JPN", "410" to "KOR", "458" to "MYS", "484" to "MEX",
        "528" to "NLD", "554" to "NZL", "578" to "NOR", "616" to "POL",
        "620" to "PRT", "643" to "RUS", "702" to "SGP", "710" to "ZAF",
        "724" to "ESP", "752" to "SWE", "756" to "CHE", "158" to "TWN",
        "764" to "THA", "792" to "TUR", "826" to "GBR", "840" to "USA",
        "858" to "URY", "704" to "VNM",
    )

    private val JSON_KEY_ALIASES = mapOf(
        "name" to "name", "part_name" to "name", "partname" to "name",
        "part_number" to "partNumber", "partnumber" to "partNumber", "part" to "partNumber", "pn" to "partNumber",
        "gtin" to "gtin", "ean" to "gtin", "upc" to "gtin", "barcode" to "gtin",
        "serial" to "serialNumber", "serial_number" to "serialNumber", "serialnumber" to "serialNumber", "sn" to "serialNumber",
        "lot" to "lotNumber", "batch" to "lotNumber", "lot_number" to "lotNumber", "lotnumber" to "lotNumber",
        "qty" to "quantity", "quantity" to "quantity", "count" to "quantity",
        "prod_date" to "productionDate", "productiondate" to "productionDate", "production_date" to "productionDate",
        "mfr" to "manufacturerId", "manufacturer" to "manufacturerId", "supplier" to "manufacturerId",
        "manufacturerid" to "manufacturerId", "supplier_id" to "manufacturerId",
        "revision" to "revision", "version" to "revision", "rev" to "revision",
        "model" to "modelNumber", "modelnumber" to "modelNumber", "model_number" to "modelNumber",
        "country" to "country", "origin" to "country",
    )

    /**
     * Returns a map keyed by Part property names populated from the barcode.
     *
     * Keys: name, partNumber, gtin, serialNumber, lotNumber, quantity,
     *       productionDate, manufacturerId, modelNumber, revision, country
     *
     * @param rawValue  Raw string decoded from the barcode scanner.
     * @param symbology Symbology hint (e.g. "EAN-13", "Code 128", "QR Code").
     */
    fun parseBarcode(rawValue: String, symbology: String): Map<String, String> {
        if (rawValue.isEmpty()) return emptyMap()

        val clean = stripAIMIdentifier(rawValue)

        // Symbology fast paths
        if (symbology.contains("EAN")) return mapOf("gtin" to clean)

        if (symbology.contains("UPC-E")) {
            return mapOf("gtin" to (expandUPCE(clean) ?: clean))
        }

        if (symbology.contains("Interleaved") || symbology.contains("I25")) {
            return if (clean.all { it.isDigit() } &&
                clean.length in setOf(8, 12, 13, 14)) {
                mapOf("gtin" to clean)
            } else {
                mapOf("partNumber" to clean)
            }
        }

        // GS1 parenthesized
        if (clean.startsWith("(")) {
            val gs1 = parseGS1Parenthesized(clean)
            if (gs1.isNotEmpty()) return gs1
        }

        // GS1 concatenated
        val hasGS = clean.contains(GS1_GS) || clean.contains(GS1_RS)
        if (hasGS || looksLikeGS1Start(clean)) {
            val gs1 = parseGS1Concatenated(clean)
            if (gs1.isNotEmpty()) return gs1
        }

        // Code 39
        if (symbology.contains("Code 39")) {
            var stripped = clean
            if (stripped.startsWith("*") && stripped.endsWith("*") && stripped.length > 2) {
                stripped = stripped.drop(1).dropLast(1)
            }
            return mapOf("partNumber" to stripped)
        }

        // Code 128
        if (symbology.contains("Code 128")) {
            return if (clean.all { it.isDigit() } && clean.length in setOf(12, 13, 14)) {
                mapOf("gtin" to clean)
            } else {
                mapOf("partNumber" to clean)
            }
        }

        // QR Code
        if (symbology.contains("QR")) {
            runCatching {
                val json = JSONObject(clean)
                val parsed = parseJSONPayload(json)
                if (parsed.isNotEmpty()) return parsed
            }
            if (looksLikeGS1Start(clean)) {
                val gs1 = parseGS1Concatenated(clean)
                if (gs1.isNotEmpty()) return gs1
            }
            return mapOf("partNumber" to clean)
        }

        return mapOf("partNumber" to clean)
    }

    // ── GS1 parenthesized (01)12345678901234(10)LOT ──────────────────────────

    private fun parseGS1Parenthesized(str: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var pos = 0

        while (pos < str.length) {
            if (str[pos] != '(') break
            val closeIdx = str.indexOf(')', pos + 1)
            if (closeIdx < 0) break
            val ai = str.substring(pos + 1, closeIdx)
            val dataStart = closeIdx + 1
            val nextOpen = str.indexOf('(', dataStart)
            val dataEnd = if (nextOpen < 0) str.length else nextOpen
            val value = str.substring(dataStart, dataEnd)
            applyAI(ai, value, result)
            pos = dataEnd
        }

        return result.filter { it.value.isNotEmpty() }
    }

    // ── GS1 concatenated 01[14-digits]10LOT21SN... ────────────────────

    private fun parseGS1Concatenated(str: String): Map<String, String> {
        val s = str.trimStart(GS1_GS)
        val result = mutableMapOf<String, String>()
        var pos = 0

        while (pos < s.length) {
            while (pos < s.length && s[pos] == GS1_GS) pos++
            if (pos >= s.length) break

            var matchedAI: String? = null
            var aiLen = 0
            var fixedDataLen = 0

            for (tryLen in 4 downTo 2) {
                if (pos + tryLen > s.length) continue
                val candidate = s.substring(pos, pos + tryLen)
                val fl = fixedLengthForAI(candidate)
                if (fl != null) {
                    matchedAI = candidate
                    aiLen = tryLen
                    fixedDataLen = fl
                    break
                }
            }

            if (matchedAI == null) {
                val gsIdx = s.indexOf(GS1_GS, pos)
                pos = if (gsIdx >= 0) gsIdx + 1 else s.length
                continue
            }

            pos += aiLen

            val value: String
            if (fixedDataLen > 0) {
                if (pos + fixedDataLen > s.length) break
                value = s.substring(pos, pos + fixedDataLen)
                pos += fixedDataLen
            } else {
                val start = pos
                while (pos < s.length) {
                    if (s[pos] == GS1_GS) break
                    if (pos > start && hasKnownAI(s, pos)) break
                    pos++
                }
                value = s.substring(start, pos)
            }

            if (value.isNotEmpty()) applyAI(matchedAI, value, result)
        }

        return result.filter { it.value.isNotEmpty() }
    }

    // ── JSON QR payload ───────────────────────────────────────────────────────

    private fun parseJSONPayload(json: JSONObject): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (key in json.keys()) {
            val lower = key.lowercase()
            val fieldKey = JSON_KEY_ALIASES[lower] ?: JSON_KEY_ALIASES[key] ?: continue
            val strVal = json.opt(key)?.toString()?.takeIf { it.isNotEmpty() } ?: continue
            if (!result.containsKey(fieldKey)) result[fieldKey] = strVal
        }
        return result
    }

    // ── AI application ────────────────────────────────────────────────────────

    private fun applyAI(ai: String, value: String, result: MutableMap<String, String>) {
        val entry = tableEntry(ai) ?: return
        val key = entry.key ?: return
        val processed = processValue(value, ai)
        if (processed.isEmpty()) return
        val isFallback = ai == "241" || ai == "8010" || ai == "8011"
        if (isFallback && result.containsKey(key)) return
        result[key] = processed
    }

    private fun processValue(value: String, ai: String): String = when (ai) {
        "11", "17", "13", "15" -> formatGS1Date(value)
        "422" -> COUNTRY_ALPHA3[value] ?: value
        "30", "37" -> (value.toIntOrNull() ?: 0).toString()
        else -> value
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun stripAIMIdentifier(str: String): String {
        if (str.length < 3 || str[0] != ']') return str
        return if (str[1].isLetter() && str[2].isDigit()) str.drop(3) else str
    }

    private fun looksLikeGS1Start(str: String): Boolean {
        if (str.length < 2) return false
        val prefix = str.take(2)
        val common = setOf("01", "00", "10", "11", "21", "30", "02", "37", "17")
        if (!common.contains(prefix)) return false
        if (prefix == "01" && str.length >= 16) {
            return str.substring(2, 16).all { it.isDigit() }
        }
        return true
    }

    private fun hasKnownAI(str: String, pos: Int): Boolean {
        for (tryLen in 4 downTo 2) {
            if (pos + tryLen > str.length) continue
            if (fixedLengthForAI(str.substring(pos, pos + tryLen)) != null) return true
        }
        return false
    }

    private fun fixedLengthForAI(ai: String): Int? = tableEntry(ai)?.fixed

    private fun tableEntry(ai: String): AIEntry? = AI_TABLE.firstOrNull { it.ai == ai }

    /** Converts GS1 date YYMMDD → YYYY-MM-DD. GS1 rule: 00-49 → 20xx; 50-99 → 19xx. */
    private fun formatGS1Date(yymmdd: String): String {
        if (yymmdd.length != 6) return yymmdd
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return yymmdd
        val mm = yymmdd.substring(2, 4)
        val dd = yymmdd.substring(4, 6)
        val yyyy = if (yy < 50) 2000 + yy else 1900 + yy
        return "%04d-%s-%s".format(yyyy, mm, dd)
    }

    /** Expands a 6-digit (or 8-char with system/check) UPC-E to 12-digit UPC-A string. */
    private fun expandUPCE(upce: String): String? {
        var s = upce
        if (s.length == 8) s = s.substring(1, 7)
        if (s.length == 7) s = s.take(6)
        if (s.length != 6 || !s.all { it.isDigit() }) return null
        val last = s[5]
        val manuf: String
        val item: String
        when (last) {
            '0', '1', '2' -> { manuf = "${s[0]}${s[1]}${last}00"; item = "00${s[2]}${s[3]}${s[4]}" }
            '3' -> { manuf = "${s[0]}${s[1]}${s[2]}00"; item = "00${s[3]}${s[4]}" }
            '4' -> { manuf = "${s[0]}${s[1]}${s[2]}${s[3]}0"; item = "0000${s[4]}" }
            else -> { manuf = "${s[0]}${s[1]}${s[2]}${s[3]}${s[4]}"; item = "0000$last" }
        }
        return "0$manuf$item"
    }
}
