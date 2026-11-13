package com.avago.feature.workorders.model

import java.text.Normalizer

/**
 * Client-side work-order text search — port of web `woSearch.ts` (W8).
 *
 * Every whitespace-separated term must match at least one searchable field
 * (case- and diacritic-insensitive). A leading `#` is stripped so "#1234"
 * matches a WO id fragment.
 */
object WoSearch {
    private val COMBINING = Regex("\\p{Mn}+")
    private val WS = Regex("\\s+")

    fun normalize(s: String): String =
        COMBINING.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

    fun terms(query: String?): List<String> {
        if (query.isNullOrBlank()) return emptyList()
        return normalize(query).split(WS).map { it.removePrefix("#") }.filter { it.isNotEmpty() }
    }

    fun matches(fields: List<String?>, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val hay = normalize(fields.filterNot { it.isNullOrBlank() }.joinToString("\n"))
        return terms.all { hay.contains(it) }
    }

    /** Filters [rows]; [project] returns the searchable fields of a row. */
    fun <T> filter(rows: List<T>, query: String?, project: (T) -> List<String?>): List<T> {
        val t = terms(query)
        if (t.isEmpty()) return rows
        return rows.filter { matches(project(it), t) }
    }
}
