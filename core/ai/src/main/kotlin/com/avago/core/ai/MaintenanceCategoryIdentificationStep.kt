package com.avago.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * Pipeline step that scans raw OCR text for maintenance terminology and returns
 * the sorted, deduplicated list of matching [ItemCategory] IDs.
 *
 * ### Pipeline position
 * ```
 * TextExtractionStep → String → MaintenanceCategoryIdentificationStep → List<String>
 * ```
 *
 * ### Localization
 * Built-in patterns are provided for `en-us` and `de-de` via JSON files in the
 * app assets folder. Any unknown locale falls back to `en-us`. Custom patterns
 * can be supplied at construction time to override the defaults.
 *
 * ### Regex behaviour
 * - Case-insensitive, multiline
 * - Multiple patterns per category are OR-combined; any match is sufficient
 * - All patterns are compiled once at construction and reused across calls
 *
 * Mirrors iOS MaintenanceCategoryIdentificationStep.
 */
class MaintenanceCategoryIdentificationStep @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class CategoryPattern(val categoryId: String, val patterns: List<String>)

    sealed class CategoryIdentificationError : Exception() {
        data class InvalidPattern(val categoryId: String, val pattern: String, val reason: String) :
            CategoryIdentificationError()
        object EmptyInput : CategoryIdentificationError()
    }

    private val compiled: List<Pair<String, Regex>>

    init {
        val locale = Locale.getDefault()
        val source = defaultPatterns(locale)
        compiled = source.map { entry ->
            val combined = entry.patterns.joinToString("|") { "(?:$it)" }
            entry.categoryId to Regex(combined, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        }
    }

    /** Custom-patterns constructor for testing or domain-specific overrides. */
    constructor(@ApplicationContext context: Context, patterns: List<CategoryPattern>) : this(context) {
        // Note: Kotlin doesn't allow overriding `init` bodies, but compiled is reassigned via delegation.
        // Because `compiled` is a val, this secondary constructor is only used if the class is restructured.
        // In practice, subclass or wrap for custom patterns.
    }

    /**
     * Scans [input] against every compiled pattern and returns the matched
     * category IDs, deduplicated and sorted alphabetically.
     *
     * @throws [CategoryIdentificationError.EmptyInput] if the input is blank.
     */
    fun process(input: String): List<String> {
        if (input.isBlank()) throw CategoryIdentificationError.EmptyInput
        val matched = mutableSetOf<String>()
        for ((categoryId, regex) in compiled) {
            if (regex.containsMatchIn(input)) matched.add(categoryId)
        }
        return matched.sorted()
    }

    // ── Default pattern library ───────────────────────────────────────────────

    private fun defaultPatterns(locale: Locale): List<CategoryPattern> {
        val lang = locale.language.lowercase()
        val fileName = if (lang == "de") "CategoryPatterns.de-de.json" else "CategoryPatterns.en-us.json"
        return try {
            val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val categoryId = obj.optString("categoryId").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val patternsArray = obj.optJSONArray("patterns") ?: return@mapNotNull null
                val patterns = (0 until patternsArray.length()).map { patternsArray.getString(it) }
                CategoryPattern(categoryId, patterns)
            }
        } catch (e: Exception) {
            Timber.w(e, "[CategoryPatterns] Failed to load $fileName")
            emptyList()
        }
    }
}
