package com.avago.core.data.v2

import java.math.BigDecimal

/**
 * Typed accessors over a [V2Row] (bundled SQLite returns Long, Double, String, ByteArray or null).
 * Required columns throw when missing so a contract drift fails loudly in tests instead of
 * rendering blanks.
 */
fun V2Row.str(column: String): String = this[column] as? String ?: error("v2 row: $column is null or not text")

fun V2Row.strOrNull(column: String): String? = this[column] as? String

fun V2Row.long(column: String): Long = (this[column] as? Number)?.toLong() ?: error("v2 row: $column is null or not integer")

fun V2Row.longOrNull(column: String): Long? = (this[column] as? Number)?.toLong()

fun V2Row.int(column: String): Int = long(column).toInt()

fun V2Row.intOrNull(column: String): Int? = longOrNull(column)?.toInt()

fun V2Row.bool(column: String): Boolean = longOrNull(column) == 1L

/** A decimal stored as text (money/unit costs, coordinates) per the contract's numeric-as-text rule. */
fun V2Row.decimalOrNull(column: String): BigDecimal? = strOrNull(column)?.toBigDecimalOrNull()

/** Quantities are fixed-point micros (`*_qty_micros`, `value_micros`): 1 unit = 1 000 000. */
fun V2Row.microsOrNull(column: String): BigDecimal? = longOrNull(column)?.let(V2Micros::toDecimal)

fun V2Row.micros(column: String): BigDecimal = V2Micros.toDecimal(long(column))

object V2Micros {
    private val SCALE = BigDecimal(1_000_000)

    fun toDecimal(micros: Long): BigDecimal = BigDecimal(micros).divide(SCALE).stripTrailingZeros().let {
        if (it.scale() < 0) it.setScale(0) else it
    }

    fun fromDecimal(value: BigDecimal): Long = value.multiply(SCALE).setScale(0, java.math.RoundingMode.HALF_EVEN).longValueExact()

    fun fromDouble(value: Double): Long = fromDecimal(BigDecimal.valueOf(value))
}

/** Minor currency units (`*_minor`) ↔ major-unit decimals using the currency's ISO 4217 fraction digits. */
object V2Money {
    fun fractionDigits(currency: String?): Int =
        currency?.let { runCatching { java.util.Currency.getInstance(it.uppercase()).defaultFractionDigits }.getOrNull() }
            ?.takeIf { it >= 0 } ?: 2

    fun toMajor(minor: Long, currency: String? = null): BigDecimal = BigDecimal.valueOf(minor, fractionDigits(currency))

    fun toMinor(major: BigDecimal, currency: String? = null): Long =
        major.setScale(fractionDigits(currency), java.math.RoundingMode.HALF_EVEN).unscaledValue().longValueExact()
}
