package com.avago.core.network.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Fields the document extractor returns (`POST /accounts/{a}/docs/ocr-extract` and
 * `POST /accounts/{a}/docs/{doc_id}/ocr-extract`). Amounts and dates arrive as the model wrote
 * them: callers parse them leniently.
 */
@Serializable
data class DocOcrResponse(
    val title: String? = null,
    val vendor: String? = null,
    val document_date: String? = null,
    val currency: String? = null,
    val total_amount: String? = null,
    val warranty_end_date: String? = null,
) {
    /** [total_amount] as a number: currency symbols dropped, `1.234,56` and `1,234.56` both read. */
    fun totalAmount(): BigDecimal? {
        val digits = total_amount?.filter { it.isDigit() || it == '.' || it == ',' || it == '-' } ?: return null
        val normalized = when {
            ',' in digits && '.' in digits ->
                if (digits.lastIndexOf(',') > digits.lastIndexOf('.')) digits.replace(".", "").replace(',', '.') else digits.replace(",", "")
            ',' in digits -> if (DECIMAL_COMMA.containsMatchIn(digits)) digits.replace(',', '.') else digits.replace(",", "")
            else -> digits
        }
        return normalized.toBigDecimalOrNull()
    }

    fun documentDate(): LocalDate? = day(document_date)

    fun warrantyEndDate(): LocalDate? = day(warranty_end_date)

    private companion object {
        val DECIMAL_COMMA = Regex(",\\d{1,2}$")

        fun day(raw: String?): LocalDate? = raw?.trim()?.take(10)?.let { s ->
            try {
                LocalDate.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/** `POST /accounts/{a}/docs/{doc_id}/ocr-extract`: the re-run extraction (stored on the current revision). */
@Serializable
data class DocOcrReextractResponse(
    val extraction: DocOcrResponse,
)