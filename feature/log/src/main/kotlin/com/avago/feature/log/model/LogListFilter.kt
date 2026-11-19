package com.avago.feature.log.model

import com.avago.core.data.domain.Activity
import java.text.Normalizer

/**
 * Log list search + category filter (web AssetLogTab parity): every whitespace-separated
 * term must appear in the title or notes, case- and diacritic-insensitive.
 */
object LogListFilter {

    private val MARKS = Regex("\\p{M}+")

    fun normalize(s: String?): String =
        Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    fun terms(query: String): List<String> =
        normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(title: String?, notes: String?, query: String): Boolean {
        val ts = terms(query)
        if (ts.isEmpty()) return true
        val hay = normalize(title) + "\n" + normalize(notes)
        return ts.all { hay.contains(it) }
    }

    fun apply(list: List<Activity>, category: String?, query: String): List<Activity> =
        list.filter { log ->
            (category == null || log.categoryLabel == category) && matches(log.displayTitle, log.notes, query)
        }

    /** (category, count) for every non-blank category, most used first then alphabetically. */
    fun categoryCounts(list: List<Activity>): List<Pair<String, Int>> =
        list.mapNotNull { it.categoryLabel?.takeIf { c -> c.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
}
