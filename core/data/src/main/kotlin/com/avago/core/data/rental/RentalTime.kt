package com.avago.core.data.rental

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Time helpers for rental sync rows. Allocation feed rows are `to_jsonb(row)`, so the
 * `during` column arrives as a Postgres `tstzrange` text such as
 * `["2026-05-01 08:00:00+00","2026-05-03 08:00:00+00")`; other endpoints send RFC 3339.
 */
object RentalTime {

    /** Postgres timestamptz text output: `2026-05-01 08:00:00.123456+05:30` (offset may be `+00`). */
    private val PG: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd[ ]['T']HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .optionalStart().appendOffset("+HH:MM:ss", "Z").optionalEnd()
        .optionalStart().appendOffset("+HH:mm", "Z").optionalEnd()
        .optionalStart().appendOffset("+HH", "Z").optionalEnd()
        .toFormatter()

    /** Parses RFC 3339 or Postgres timestamptz text; null for blank/unparseable/infinity. */
    fun parseInstant(raw: String?): Instant? {
        val s = raw?.trim()?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
        if (s.equals("infinity", true) || s.equals("-infinity", true)) return null
        runCatching { return Instant.parse(s) }
        runCatching { return OffsetDateTime.parse(s).toInstant() }
        return runCatching { OffsetDateTime.parse(s, PG).toInstant() }.getOrNull()
    }

    data class Range(val start: Instant?, val end: Instant?, val empty: Boolean = false)

    /**
     * Parses a `tstzrange` literal. Missing bounds (`[x,)`) are open (null). Inclusive /
     * exclusive brackets are accepted; allocations are always `[)` on the server.
     */
    fun parseRange(raw: String?): Range? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (s.equals("empty", true)) return Range(null, null, empty = true)
        if (s.length < 3 || s.first() !in "[(" || s.last() !in "])") return null
        val body = s.substring(1, s.length - 1)
        val comma = splitIndex(body) ?: return null
        val lo = body.substring(0, comma).trim().trim('"')
        val hi = body.substring(comma + 1).trim().trim('"')
        return Range(parseInstant(lo.ifEmpty { null }), parseInstant(hi.ifEmpty { null }))
    }

    /** Index of the bound separator: the first comma outside double quotes. */
    private fun splitIndex(body: String): Int? {
        var quoted = false
        body.forEachIndexed { i, c ->
            when {
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> return i
            }
        }
        return null
    }
}
