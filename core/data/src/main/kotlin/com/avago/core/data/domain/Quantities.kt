package com.avago.core.data.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Quantity for display: at most [maxFractionDigits] decimals, no trailing zeros, no exponent. */
fun BigDecimal.formatQty(maxFractionDigits: Int = 3): String =
    setScale(maxFractionDigits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Money amount for display with two decimals (or the currency's own digits when given). */
fun BigDecimal.formatMoney(currency: String? = null): String {
    val digits = currency?.let {
        runCatching { java.util.Currency.getInstance(it.uppercase()).defaultFractionDigits }.getOrNull()
    }?.takeIf { it >= 0 } ?: 2
    val amount = setScale(digits, RoundingMode.HALF_UP).toPlainString()
    return if (currency.isNullOrBlank()) amount else "$amount ${currency.uppercase()}"
}

/** Parses user-entered quantity text ("1,5" or "1.5"); null when blank or invalid. */
fun String.toQtyOrNull(): BigDecimal? = trim().replace(',', '.').takeIf(String::isNotEmpty)?.toBigDecimalOrNull()
