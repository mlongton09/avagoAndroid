package com.avago.core.data.rental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Locale

class MoneyTest {

    @Test
    fun `fraction digits follow ISO 4217`() {
        assertEquals(2, MoneyFormatter.fractionDigits("USD"))
        assertEquals(2, MoneyFormatter.fractionDigits("eur"))
        assertEquals(0, MoneyFormatter.fractionDigits("JPY"))
        assertEquals(3, MoneyFormatter.fractionDigits("BHD"))
        assertEquals(3, MoneyFormatter.fractionDigits("KWD"))
        assertEquals(3, MoneyFormatter.fractionDigits("OMR"))
        assertEquals(4, MoneyFormatter.fractionDigits("CLF"))
        assertEquals(2, MoneyFormatter.fractionDigits("ZZZ"), "unknown code falls back to 2")
    }

    @Test
    fun `formats with currency precision`() {
        assertEquals("$1,234.50", MoneyFormatter.format(Money(123450, "USD"), Locale.US))
        val jpy = MoneyFormatter.format(Money(1235, "JPY"), Locale.JAPAN)
        assertEquals(true, jpy.endsWith("1,235") && !jpy.contains("."), jpy) // ¥ or full-width ￥ depending on CLDR
        val de = MoneyFormatter.format(Money(123450, "EUR"), Locale.GERMANY)
        assertEquals("1.234,50\u00a0€", de)
        val bhd = MoneyFormatter.format(Money(1250, "BHD"), Locale.US)
        assertEquals(true, bhd.endsWith("1.250"), bhd)
        assertEquals("—", MoneyFormatter.format(null, "USD"))
        assertEquals("—", MoneyFormatter.format(100, null))
    }

    @Test
    fun `negative amounts keep the sign`() {
        val s = MoneyFormatter.format(Money(-500, "USD"), Locale.US)
        assertEquals(true, s.contains("-") || s.contains("("), s)
    }

    @Test
    fun `parses user input with half away from zero rounding`() {
        assertEquals(Money(1250, "USD"), MoneyFormatter.parse("12.5", "USD"))
        assertEquals(Money(1250, "EUR"), MoneyFormatter.parse("12,50", "EUR"))
        assertEquals(Money(123450, "EUR"), MoneyFormatter.parse("1.234,50 €", "EUR"))
        assertEquals(Money(123450, "USD"), MoneyFormatter.parse("$1,234.50", "USD"))
        assertEquals(Money(123400, "USD"), MoneyFormatter.parse("1,234", "USD"), "thousands separator")
        assertEquals(Money(1234, "BHD"), MoneyFormatter.parse("1.234", "BHD"), "3-decimal currency")
        assertEquals(Money(1235, "JPY"), MoneyFormatter.parse("1234.5", "JPY"), "half rounds up")
        assertEquals(Money(1, "USD"), MoneyFormatter.parse("0.005", "USD"), "half away from zero")
        assertEquals(Money(-1, "USD"), MoneyFormatter.parse("-0.005", "USD"), "half away from zero (negative)")
        assertEquals(Money(100000000, "USD"), MoneyFormatter.parse("1.000.000", "USD"))
        assertNull(MoneyFormatter.parse("", "USD"))
        assertNull(MoneyFormatter.parse("abc", "USD"))
    }

    @Test
    fun `round trips through the input field`() {
        val m = Money(98765, "EUR")
        assertEquals(m, MoneyFormatter.parse(MoneyFormatter.toInput(m, Locale.GERMANY), "EUR"))
        val j = Money(5000, "JPY")
        assertEquals(j, MoneyFormatter.parse(MoneyFormatter.toInput(j, Locale.US), "JPY"))
    }

    @Test
    fun `arithmetic requires matching currency`() {
        assertEquals(Money(300, "USD"), Money(100, "USD") + Money(200, "USD"))
        assertEquals(Money(-100, "USD"), Money(100, "USD") - Money(200, "USD"))
        assertEquals(Money(600, "USD"), Money(200, "USD") * 3)
        assertThrows<IllegalArgumentException> { Money(1, "USD") + Money(1, "EUR") }
        assertThrows<IllegalArgumentException> { Money(1, "US") }
        assertThrows<ArithmeticException> { Money(Long.MAX_VALUE, "USD") + Money(1, "USD") }
    }

    @Test
    fun `of tolerates missing api fields`() {
        assertNull(Money.of(null, "USD"))
        assertNull(Money.of(1, ""))
        assertEquals(Money(1, "USD"), Money.of(1, "usd"))
    }
}
