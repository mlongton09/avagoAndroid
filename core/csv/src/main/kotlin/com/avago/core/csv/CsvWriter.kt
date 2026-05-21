package com.avago.core.csv

/**
 * RFC-4180 compliant CSV writer with UTF-8 BOM for Excel compatibility.
 *
 * - Fields containing commas, double-quotes, or newlines are wrapped in double-quotes.
 * - Internal double-quotes are escaped by doubling them ("").
 * - Lines are terminated with CRLF (\r\n) per the RFC.
 * - Output is prefixed with a UTF-8 BOM (EF BB BF) so Excel auto-detects the encoding.
 */
object CsvWriter {

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * Build a CSV byte array from [headers] and [rows].
     *
     * @param headers Column header strings.
     * @param rows    Each row is a list of cell values (will be stringified and escaped).
     * @return UTF-8 encoded CSV bytes with a leading BOM.
     */
    fun write(headers: List<String>, rows: List<List<String>>): ByteArray {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { it.csvEscape() })
        sb.append("\r\n")
        for (row in rows) {
            sb.append(row.joinToString(",") { it.csvEscape() })
            sb.append("\r\n")
        }
        return BOM + sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Escape a single cell value per RFC-4180:
     * - If the value contains a comma, double-quote, newline, or carriage-return,
     *   wrap it in double-quotes and escape any existing double-quotes by doubling them.
     * - Otherwise, return the value unchanged.
     */
    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n') || contains('\r')) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }
}
