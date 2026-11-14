package com.avago.feature.rentals.legacy

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.avago.feature.assets.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Display helpers shared by the legacy (asset-scoped) rental screens.
 *
 * API/wire values (condition, meter unit, rate unit, payment method, status) stay unchanged;
 * only their on-screen labels are resolved here so every legacy screen shows the same
 * localized text. Unknown wire values fall back to the raw value rather than guessing.
 */
internal object LegacyRentalText {

    /** Condition values the API accepts, in display order. */
    val CONDITIONS = listOf("excellent", "good", "fair", "poor")

    /** Meter unit values the API accepts, in display order. */
    val METER_UNITS = listOf("hours", "miles", "km")

    /** Payment method values sent to `POST /invoices/{id}/pay`; stored and shown back verbatim. */
    val PAYMENT_METHODS = listOf("Cash", "Credit Card", "Bank Transfer", "Check", "Other")

    @StringRes
    fun conditionLabel(value: String): Int? = when (value.lowercase(Locale.ROOT)) {
        "excellent" -> R.string.rental_condition_excellent
        "good" -> R.string.rental_condition_good
        "fair" -> R.string.rental_condition_fair
        "poor" -> R.string.rental_condition_poor
        else -> null
    }

    @StringRes
    fun meterUnitLabel(value: String): Int? = when (value.lowercase(Locale.ROOT)) {
        "hours", "hour", "hrs", "h" -> R.string.rental_meter_unit_hours
        "miles", "mile", "mi" -> R.string.rental_meter_unit_miles
        "km", "kilometers", "kilometres" -> R.string.rental_meter_unit_km
        else -> null
    }

    /** Label for a rate-unit chip; accepts both the chip values (`daily`) and API values (`day`). */
    @StringRes
    fun rateUnitLabel(value: String): Int? = when (value.lowercase(Locale.ROOT)) {
        "hourly", "hour" -> R.string.rental_rate_unit_hourly
        "daily", "day" -> R.string.rental_rate_unit_daily
        "weekly", "week" -> R.string.rental_rate_unit_weekly
        "monthly", "month" -> R.string.rental_rate_unit_monthly
        else -> null
    }

    /** `"%1$s / day"`-style format for an amount per [value] rate unit. */
    @StringRes
    fun ratePerUnitFormat(value: String): Int? = when (value.lowercase(Locale.ROOT)) {
        "hourly", "hour" -> R.string.rental_rate_per_hour
        "daily", "day" -> R.string.rental_rate_per_day
        "weekly", "week" -> R.string.rental_rate_per_week
        "monthly", "month" -> R.string.rental_rate_per_month
        else -> null
    }

    @StringRes
    fun paymentMethodLabel(value: String): Int? = when (value.lowercase(Locale.ROOT).replace('_', ' ')) {
        "cash" -> R.string.rental_payment_method_cash
        "credit card", "card" -> R.string.rental_payment_method_credit_card
        "bank transfer" -> R.string.rental_payment_method_bank_transfer
        "check", "cheque" -> R.string.rental_payment_method_check
        "other" -> R.string.rental_payment_method_other
        else -> null
    }

    /** Medium localized date (`Jan 5, 2026` / `05.01.2026`) of an ISO-8601 instant; raw input if unparseable. */
    fun formatDate(iso: String, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }.getOrDefault(iso)

    /** Same medium localized date pattern for an already-resolved local date (date pickers, summaries). */
    fun dateFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    /**
     * Amount in [currency] formatted for the user's [locale] — the locale decides separators and
     * symbol placement, the currency decides the symbol and decimals (`$1,234.50`, `1.234,50 €`,
     * `CA$12.00`, `¥1,235`). An unknown/blank currency code falls back to a plain localized number
     * followed by the code.
     */
    fun formatMoney(amount: Double, currency: String?, locale: Locale = Locale.getDefault()): String {
        val code = currency?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val cur = runCatching { Currency.getInstance(code) }.getOrNull()
        if (cur == null) {
            val plain = NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(amount)
            return if (code.isEmpty()) plain else "$plain $code"
        }
        val digits = cur.defaultFractionDigits.coerceAtLeast(0)
        return NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = cur
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(BigDecimal.valueOf(amount).setScale(digits, RoundingMode.HALF_UP))
    }

    /** Localized meter reading / usage number (`1,234.5` / `1.234,5`). */
    fun formatMeter(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(value)
}

/**
 * Internal error codes the legacy rental view models expose instead of English text; the
 * screens resolve them with [legacyRentalErrorText]. Any other value is a server/transport
 * message and is shown as-is.
 */
internal object LegacyRentalError {
    const val UNAUTHORIZED = "legacy_rental_error.unauthorized"
    const val UNKNOWN = "legacy_rental_error.unknown"
    const val NO_ACCOUNT = "legacy_rental_error.no_account"

    /** The exception's own message when it has one, otherwise [UNKNOWN]. */
    fun from(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: UNKNOWN

    @StringRes
    fun messageRes(code: String): Int? = when (code) {
        UNAUTHORIZED -> R.string.rental_error_unauthorized
        UNKNOWN -> R.string.rental_error_unknown
        NO_ACCOUNT -> R.string.rental_error_no_account
        else -> null
    }
}

internal fun Resources.legacyRentalErrorText(error: String): String =
    LegacyRentalError.messageRes(error)?.let(::getString) ?: error

/** The localized label for [res], or [raw] (capitalized) when the wire value is not a known one. */
@Composable
internal fun labelOrRaw(@StringRes res: Int?, raw: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (res != null) stringResource(res) else raw.replaceFirstChar { it.titlecase(locale) }
}
