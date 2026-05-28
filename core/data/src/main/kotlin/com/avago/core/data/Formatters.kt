package com.avago.core.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Named date, number, and currency formatter factories.
 * Mirrors iOS Formatters.swift — provides consistent formatting patterns
 * across the app without scattering format strings through UI code.
 *
 * Each accessor creates a new instance. SimpleDateFormat is not thread-safe;
 * callers on background threads should use these factories rather than
 * caching the result. Compose callers should use `remember { Formatters.X }`.
 */
object Formatters {

    // ── Date formatters ───────────────────────────────────────────────────────

    val dateShort: SimpleDateFormat
        get() = SimpleDateFormat("M/d/yy", Locale.getDefault())

    val dateMedium: SimpleDateFormat
        get() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    val dateLong: SimpleDateFormat
        get() = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    val dateMediumTime: SimpleDateFormat
        get() = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    val dateTime: SimpleDateFormat
        get() = SimpleDateFormat("h:mm a", Locale.getDefault())

    val dateCompact: SimpleDateFormat
        get() = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    val iso8601: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }

    // ── Number formatters ─────────────────────────────────────────────────────

    val integer: NumberFormat
        get() = NumberFormat.getIntegerInstance(Locale.getDefault())

    val percent: NumberFormat
        get() = NumberFormat.getPercentInstance(Locale.getDefault()).also {
            it.maximumFractionDigits = 1
        }

    val currency: NumberFormat
        get() = NumberFormat.getCurrencyInstance(Locale.getDefault())

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parse an ISO-8601 or yyyy-MM-dd string to Unix epoch milliseconds.
     * Returns null when the string is blank or unparseable.
     * Mirrors iOS Formatters.isoToEpoch().
     */
    fun isoToEpoch(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .also { it.timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(iso)?.time
            } catch (_: Exception) { null }
        }
    }

    /**
     * Format a numeric odometer/hours value with smart decimal places.
     * Mirrors iOS Formatters.meter(value:unit:).
     */
    fun formatOdometer(value: Double, unit: String): String {
        val formatted = if (value % 1.0 == 0.0) integer.format(value.toLong())
                        else String.format(Locale.getDefault(), "%.1f", value)
        return "$formatted $unit"
    }
}
