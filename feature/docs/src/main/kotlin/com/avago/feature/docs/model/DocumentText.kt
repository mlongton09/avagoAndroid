package com.avago.feature.docs.model

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** A document amount in its currency, e.g. "?120.50"; the plain number when the currency is unknown. */
fun formatAmount(total: BigDecimal, currency: String?, locale: Locale = Locale.getDefault()): String {
    val known = currency?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        ?: return total.stripTrailingZeros().toPlainString().let { if (currency != null) "$it $currency" else it }
    return NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = known
        maximumFractionDigits = known.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits = known.defaultFractionDigits.coerceAtLeast(0)
    }.format(total)
}

/** "2.4 MB"-style file size. */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
