package com.avago.feature.rentals.onboarding

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

private const val MAX_CURRENCY_DIGITS = 4
private const val DEFAULT_CURRENCY_DIGITS = 2
private const val MAX_MONEY_INPUT_CHARS = 32

fun currencyDigits(currency: String): Int? = runCatching {
    Currency.getInstance(currency).defaultFractionDigits.takeIf { it in 0..MAX_CURRENCY_DIGITS }
}.getOrNull()

/** Plain decimal input only. Never converts money through Float/Double, even on resume. */
fun parseMinor(text: String, currency: String, allowZero: Boolean = false): Long? {
    val digits = currencyDigits(currency)
    val value = text.trim()
    val plain = value.length <= MAX_MONEY_INPUT_CHARS && Regex("[0-9]+(?:\\.[0-9]+)?").matches(value)
    if (digits == null || !plain || value.substringAfter('.', "").length > digits) return null
    return runCatching {
        BigDecimal(value).movePointRight(digits).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()?.takeIf { it in (if (allowZero) 0L else 1L)..SAFE_MINOR }
}

fun minorInput(minor: Long, currency: String): String =
    BigDecimal.valueOf(minor, currencyDigits(currency) ?: DEFAULT_CURRENCY_DIGITS).toPlainString()
