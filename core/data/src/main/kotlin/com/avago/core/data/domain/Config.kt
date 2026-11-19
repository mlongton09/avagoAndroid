package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.util.Locale

/**
 * One effective configuration document: the published system document (`config_documents`) or the
 * account/user override (`config_overrides`) that replaces it for the same key.
 */
data class ConfigDocument(
    val id: String,
    val domain: String,
    val subtype: String?,
    val assetType: String?,
    val locale: String?,
    /** JSON payload. */
    val payload: String,
    val overridden: Boolean,
)

/** Configuration documents (checklists, categories, attribute definitions, localized strings). */
interface ConfigRepository {
    /** Effective documents of [domain] (overrides already applied). */
    fun observeDocuments(domain: String): Flow<List<ConfigDocument>>

    suspend fun documents(domain: String): List<ConfigDocument>

    /**
     * The document of [domain] for exactly [subtype] and [assetType] (null matches only null), in the
     * best locale for [locale] (see [ConfigDocuments.pick]).
     */
    suspend fun document(domain: String, subtype: String?, assetType: String?, locale: Locale = Locale.getDefault()): ConfigDocument? =
        ConfigDocuments.pick(documents(domain), subtype, assetType, locale)

    companion object {
        const val INSPECTION = "inspection"
        const val ITEM_CATEGORY = "item_category"
        const val ITEM_ATTRIBUTES = "item_attributes"
        const val LOCALE = "locale"

        /** Subtypes of [LOCALE] documents. */
        const val LOCALE_INSPECTION = "Inspection"
        const val LOCALE_ITEM_ATTRIBUTES = "ItemAttributes"
    }
}

object ConfigDocuments {
    /**
     * Picks the document for [subtype]/[assetType] whose locale best matches [locale]: the exact tag,
     * then the same language, then en-US, then a locale-neutral document, then any.
     */
    fun pick(docs: List<ConfigDocument>, subtype: String?, assetType: String?, locale: Locale): ConfigDocument? {
        val candidates = docs.filter { it.subtype == subtype && it.assetType == assetType }
        if (candidates.isEmpty()) return null
        val tag = locale.toLanguageTag()
        return candidates.firstOrNull { it.locale.equals(tag, ignoreCase = true) }
            ?: candidates.firstOrNull { it.locale?.substringBefore('-').equals(locale.language, ignoreCase = true) }
            ?: candidates.firstOrNull { it.locale.equals("en-US", ignoreCase = true) }
            ?: candidates.firstOrNull { it.locale == null }
            ?: candidates.minBy { it.locale.orEmpty() }
    }

    /**
     * Applies overrides to system documents: an override replaces the system document with the same
     * (subtype, asset type, locale) key; a user override wins over an account one.
     */
    fun effective(system: List<ConfigDocument>, accountOverrides: List<ConfigDocument>, userOverrides: List<ConfigDocument>): List<ConfigDocument> {
        fun key(d: ConfigDocument) = Triple(d.subtype, d.assetType, d.locale?.lowercase())
        val merged = LinkedHashMap<Triple<String?, String?, String?>, ConfigDocument>()
        system.forEach { merged[key(it)] = it }
        accountOverrides.forEach { merged[key(it)] = it }
        userOverrides.forEach { merged[key(it)] = it }
        return merged.values.toList()
    }
}
