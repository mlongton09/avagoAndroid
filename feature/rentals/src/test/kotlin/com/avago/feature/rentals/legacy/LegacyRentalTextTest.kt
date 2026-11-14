package com.avago.feature.rentals.legacy

import com.avago.feature.assets.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.util.Locale

class LegacyRentalTextTest {

    private val nbsp = '\u00A0'

    @Test
    fun `money uses the user's locale for separators and the rental's currency for the symbol`() {
        assertEquals("$1,234.50", LegacyRentalText.formatMoney(1234.5, "USD", Locale.US))
        assertEquals("1.234,50${nbsp}€", LegacyRentalText.formatMoney(1234.5, "EUR", Locale.GERMANY))
        // Previously a German user saw a USD rental formatted with en-US rules.
        assertEquals("1.234,50${nbsp}$", LegacyRentalText.formatMoney(1234.5, "usd", Locale.GERMANY))
        // Previously any currency other than USD/EUR/GBP was shown with the device currency's symbol.
        assertEquals("CA$12.00", LegacyRentalText.formatMoney(12.0, "CAD", Locale.US))
        assertEquals("¥1,235", LegacyRentalText.formatMoney(1234.5, "JPY", Locale.US))
    }

    @Test
    fun `money with an unknown currency code still formats the number for the locale`() {
        assertEquals("1.234,50 XYZ1", LegacyRentalText.formatMoney(1234.5, "XYZ1", Locale.GERMANY))
        assertEquals("1,234.50", LegacyRentalText.formatMoney(1234.5, null, Locale.US))
    }

    @Test
    fun `dates use the locale's medium date pattern and keep unparseable input`() {
        val iso = "2026-03-05T10:15:30Z"
        assertEquals("Mar 5, 2026", LegacyRentalText.formatDate(iso, Locale.US, ZoneOffset.UTC))
        assertEquals("05.03.2026", LegacyRentalText.formatDate(iso, Locale.GERMANY, ZoneOffset.UTC))
        assertEquals("not-a-date", LegacyRentalText.formatDate("not-a-date", Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun `meter readings use locale decimal separators`() {
        assertEquals("1,234.5", LegacyRentalText.formatMeter(1234.5, Locale.US))
        assertEquals("1.234,5", LegacyRentalText.formatMeter(1234.5, Locale.GERMANY))
    }

    @Test
    fun `every wire value offered by the legacy screens has a localized label`() {
        LegacyRentalText.CONDITIONS.forEach { assertNotNull(LegacyRentalText.conditionLabel(it), it) }
        LegacyRentalText.METER_UNITS.forEach { assertNotNull(LegacyRentalText.meterUnitLabel(it), it) }
        LegacyRentalText.PAYMENT_METHODS.forEach { assertNotNull(LegacyRentalText.paymentMethodLabel(it), it) }
        listOf("hourly", "daily", "weekly", "monthly").forEach {
            assertNotNull(LegacyRentalText.rateUnitLabel(it), it)
            assertNotNull(LegacyRentalText.ratePerUnitFormat(it), it)
        }
    }

    @Test
    fun `rate unit accepts both chip and API spellings and leaves unknown units raw`() {
        assertEquals(R.string.rental_rate_per_day, LegacyRentalText.ratePerUnitFormat("day"))
        assertEquals(R.string.rental_rate_per_day, LegacyRentalText.ratePerUnitFormat("Daily"))
        assertNull(LegacyRentalText.ratePerUnitFormat("fortnightly"))
    }

    @Test
    fun `payment method labels map stored values case-insensitively without changing the wire value`() {
        assertEquals(R.string.rental_payment_method_credit_card, LegacyRentalText.paymentMethodLabel("Credit Card"))
        assertEquals(R.string.rental_payment_method_credit_card, LegacyRentalText.paymentMethodLabel("credit_card"))
        assertEquals(listOf("Cash", "Credit Card", "Bank Transfer", "Check", "Other"), LegacyRentalText.PAYMENT_METHODS)
        assertNull(LegacyRentalText.paymentMethodLabel("Crypto"))
    }

    @Test
    fun `error codes resolve to localized messages while server messages pass through`() {
        assertEquals(R.string.rental_error_unauthorized, LegacyRentalError.messageRes(LegacyRentalError.UNAUTHORIZED))
        assertEquals(R.string.rental_error_no_account, LegacyRentalError.messageRes(LegacyRentalError.NO_ACCOUNT))
        assertEquals(LegacyRentalError.UNKNOWN, LegacyRentalError.from(RuntimeException()))
        assertEquals(LegacyRentalError.UNKNOWN, LegacyRentalError.from(RuntimeException(" ")))
        assertEquals("Asset is already rented", LegacyRentalError.from(RuntimeException("Asset is already rented")))
        assertNull(LegacyRentalError.messageRes("Asset is already rented"))
    }
}
