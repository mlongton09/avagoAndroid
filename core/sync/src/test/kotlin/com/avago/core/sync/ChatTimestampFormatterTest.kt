package com.avago.core.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [ChatTimestampFormatter], mirroring the iOS ChatTimestampFormatter test suite.
 *
 * All tests pin the JVM timezone to UTC for deterministic calendar arithmetic.
 */
class ChatTimestampFormatterTest {

    private lateinit var formatter: ChatTimestampFormatter
    private lateinit var originalTz: TimeZone

    @BeforeEach
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        formatter = ChatTimestampFormatter()
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Builds a [java.util.Date] for the given UTC calendar fields. */
    private fun utcDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): java.util.Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    // ─── Same day ─────────────────────────────────────────────────────────────

    @Test
    fun `same day shows time only without month name`() {
        val now = utcDate(2025, 6, 10, 14, 0)
        val msg = utcDate(2025, 6, 10, 9, 30)
        val result = formatter.format(msg, now)
        // Should look like "9:30 AM" — no month
        assertFalse(result.contains("Jun"),
            "Same-day format must not include month; got: $result")
        assertTrue(result.contains("AM") || result.contains("PM"),
            "Same-day format must include AM/PM; got: $result")
    }

    @Test
    fun `same day at midnight boundary 00_01 shows time only`() {
        val now  = utcDate(2025, 6, 10, 0, 5)
        val msg  = utcDate(2025, 6, 10, 0, 1)
        val result = formatter.format(msg, now)
        assertFalse(result.contains("Jun"),
            "00:01 same day must show time only; got: $result")
        assertTrue(result.contains("12:01 AM"),
            "Expected '12:01 AM'; got: $result")
    }

    @Test
    fun `same instant as reference shows time only not empty`() {
        val ts = utcDate(2025, 3, 15, 11, 22)
        val result = formatter.format(ts, ts)
        assertTrue(result.isNotEmpty(), "Formatting same instant must not return empty string")
        assertFalse(result.contains("Mar"),
            "Same instant must show time only; got: $result")
    }

    // ─── Different day ────────────────────────────────────────────────────────

    @Test
    fun `different day shows month and time`() {
        val now = utcDate(2025, 6, 10, 14, 0)
        val msg = utcDate(2025, 6, 9, 23, 59)
        val result = formatter.format(msg, now)
        assertTrue(result.contains("Jun"),
            "Different-day format must include month; got: $result")
    }

    @Test
    fun `23_59 previous day includes month`() {
        val now = utcDate(2025, 6, 10, 0, 5)
        val msg = utcDate(2025, 6, 9, 23, 59)
        val result = formatter.format(msg, now)
        assertTrue(result.contains("Jun"),
            "23:59 previous day must include month; got: $result")
        assertTrue(result.contains("9"),
            "Must include day number '9'; got: $result")
    }

    @Test
    fun `different month shows correct month name`() {
        val now = utcDate(2025, 8, 1, 12, 0)
        val msg = utcDate(2025, 1, 31, 10, 0)
        val result = formatter.format(msg, now)
        assertTrue(result.contains("Jan"),
            "Message from January must show 'Jan'; got: $result")
    }

    // ─── Future and old dates ─────────────────────────────────────────────────

    @Test
    fun `future date formats without crash`() {
        val now = utcDate(2025, 6, 10, 12, 0)
        val future = utcDate(2026, 12, 31, 23, 59)
        val result = formatter.format(future, now)
        assertTrue(result.isNotEmpty(), "Future date must produce non-empty string")
    }

    @Test
    fun `very old date 2020 formats without crash`() {
        val now = utcDate(2025, 6, 10, 12, 0)
        val old = utcDate(2020, 1, 1, 8, 0)
        val result = formatter.format(old, now)
        assertTrue(result.isNotEmpty(), "Old date must produce non-empty string")
        assertTrue(result.contains("Jan"), "2020-01-01 must show 'Jan'; got: $result")
    }

    // ─── Format shape ─────────────────────────────────────────────────────────

    @Test
    fun `same-day result does not contain a digit followed by a space and another digit (no MMM-d prefix)`() {
        val now = utcDate(2025, 6, 10, 15, 0)
        val msg = utcDate(2025, 6, 10, 8, 5)
        val result = formatter.format(msg, now)
        // Time-only format "h:mm a" — should be e.g. "8:05 AM"
        assertTrue(result.matches(Regex("\\d{1,2}:\\d{2} (AM|PM)")),
            "Same-day result must match 'h:mm AM/PM'; got: $result")
    }

    @Test
    fun `different-day result matches MMM-d-time pattern`() {
        val now = utcDate(2025, 6, 10, 15, 0)
        val msg = utcDate(2025, 5, 7, 8, 5)
        val result = formatter.format(msg, now)
        // e.g. "May 7 8:05 AM"
        assertTrue(result.matches(Regex("[A-Z][a-z]{2} \\d{1,2} \\d{1,2}:\\d{2} (AM|PM)")),
            "Different-day result must match 'MMM d h:mm AM/PM'; got: $result")
    }
}
