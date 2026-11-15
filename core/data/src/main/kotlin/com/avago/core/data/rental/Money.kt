package com.avago.core.data.rental

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * An amount of money in **minor units** (cents, yen, fils …) of an ISO 4217 [currency]
 * (spec §2: money is always `*_minor` BIGINT + `currency`, never floating point).
 *
 * Kotlin `@JvmInline` value classes can only wrap a single property, so this is an
 * immutable data class with value semantics. Arithmetic is only defined between amounts of
 * the same currency — mixing currencies is a programming error.
 */
data class Money(val minor: Long, val currency: String) : Comparable<Money> {

    init {
        require(currency.length == 3) { "currency must be an ISO 4217 code, was '$currency'" }
    }

    operator fun plus(other: Money): Money {
        same(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        same(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor), currency)
    operator fun times(qty: Int): Money = Money(Math.multiplyExact(minor, qty.toLong()), currency)

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L

    override fun compareTo(other: Money): Int {
        same(other)
        return minor.compareTo(other.minor)
    }

    private fun same(other: Money) {
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }
    }

    companion object {
        fun zero(currency: String) = Money(0L, currency.uppercase(Locale.ROOT))
        fun of(minor: Long?, currency: String?): Money? =
            if (minor == null || currency.isNullOrBlank() || currency.length != 3) null
            else Money(minor, currency.uppercase(Locale.ROOT))
    }
}

/**
 * Formats and parses [Money] using the currency's own minor-unit count
 * ([Currency.getDefaultFractionDigits]: JPY 0, BHD/KWD/OMR 3, CLF 4, most others 2).
 * Rounding when parsing user input is half away from zero (spec §2.4).
 */
object MoneyFormatter {

    /** Number of decimal places of [currency]; 2 for unknown codes. */
    fun fractionDigits(currency: String): Int {
        val digits = runCatching { Currency.getInstance(currency.uppercase(Locale.ROOT)).defaultFractionDigits }.getOrNull()
        return when {
            digits == null -> 2
            digits < 0 -> 0 // pseudo-currencies such as XAU
            else -> digits
        }
    }

    /** Major-unit decimal of [money] (exact, no rounding). */
    fun toMajor(money: Money): BigDecimal = BigDecimal.valueOf(money.minor, fractionDigits(money.currency))

    /** Locale-aware currency string, e.g. `$1,234.50`, `1.234,50 €`, `¥1,235`, `BHD 1.250`. */
    fun format(money: Money, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getCurrencyInstance(locale)
        val digits = fractionDigits(money.currency)
        runCatching { nf.currency = Currency.getInstance(money.currency) }
        nf.minimumFractionDigits = digits
        nf.maximumFractionDigits = digits
        return nf.format(toMajor(money))
    }

    /** Formats `minor` + `currency` straight from API fields; "—" when either is missing. */
    fun format(minor: Long?, currency: String?, locale: Locale = Locale.getDefault()): String =
        Money.of(minor, currency)?.let { format(it, locale) } ?: "—"

    /** Plain number for an editable text field (`1234.50`, locale decimal separator). */
    fun toInput(money: Money, locale: Locale = Locale.getDefault()): String {
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return toMajor(money).toPlainString().replace('.', sep)
    }

    /**
     * Parses user input (`12`, `12.5`, `12,50`, `1 234,5`, `$12.50`) into minor units,
     * rounding half away from zero to the currency's precision. Returns null for
     * unparseable input. Both `.` and `,` are accepted as the decimal separator; when both
     * appear the right-most one is the decimal separator. A single separator followed by
     * exactly three digits is a thousands separator for currencies with fewer than three
     * decimals (`1,234` USD = 1234.00) and a decimal separator otherwise (`1.234` BHD).
     */
    fun parse(input: String, currency: String): Money? {
        val negative = input.trim().startsWith("-")
        val cleaned = input.filter { it.isDigit() || it == '.' || it == ',' }
        if (cleaned.isEmpty() || cleaned.none { it.isDigit() }) return null
        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val decimalIdx = maxOf(lastDot, lastComma)
        val digits = fractionDigits(currency)
        val normalized = if (decimalIdx < 0) {
            cleaned
        } else {
            val intPart = cleaned.substring(0, decimalIdx).filter { it.isDigit() }
            val fracPart = cleaned.substring(decimalIdx + 1).filter { it.isDigit() }
            val separators = cleaned.count { it == '.' || it == ',' }
            // "1,234" / "1.234" read as grouping, but a group can't start with 0 ("0.005").
            val thousands = separators == 1 && fracPart.length == 3 && digits < 3 &&
                intPart.isNotEmpty() && intPart.trimStart('0').isNotEmpty()
            val multiSame = separators > 1 && (lastDot < 0 || lastComma < 0) // 1.234.567 or 1,234,567
            when {
                thousands || multiSame -> intPart + fracPart
                else -> "${intPart.ifEmpty { "0" }}.$fracPart"
            }
        }
        val major = normalized.toBigDecimalOrNull() ?: return null
        val minor = major.setScale(digits, RoundingMode.HALF_UP).unscaledValue()
        if (minor.bitLength() > 63) return null
        val value = minor.toLong()
        return Money(if (negative) -value else value, currency.uppercase(Locale.ROOT))
    }

    /**
     * Strict plain-decimal input (`1234.5`, `1,234.50`; `,` and spaces are grouping only) into
     * minor units of [currency], without rounding. Null when blank, negative, not a number,
     * finer than the currency's minor unit (`1.234` USD, `12.5` JPY) or out of range. A blank
     * or unknown [currency] uses 2 digits, like [fractionDigits].
     */
    fun parsePlainMinor(input: String, currency: String): Long? {
        val d = input.trim().replace(",", "").replace(" ", "").toBigDecimalOrNull() ?: return null
        val digits = fractionDigits(currency.trim())
        if (d.signum() < 0 || d.scale() > digits) return null
        return runCatching { d.movePointRight(digits).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
    }

    /** [minor] as a plain `.`-separated decimal for an editable field (`1234.50`, `1234` JPY). */
    fun toPlain(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, fractionDigits(currency.trim())).toPlainString()
}
