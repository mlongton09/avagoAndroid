package com.avago.feature.rentals.onboarding

import com.avago.feature.assets.model.AssetTaxonomy
import com.avago.feature.assets.model.AssetTypeCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.Normalizer
import java.util.Locale

const val SAFE_MINOR = 9007199254740991L
const val MAX_INVENTORY = 500
private const val MAX_TEXT_BYTES = 200
private const val MAX_SLUG_BYTES = 63
private const val MAX_CURRENCY_BYTES = 16
private const val MAX_EMAIL_BYTES = 254
private const val MAX_PHONE_BYTES = 80
private const val MAX_TYPE_BYTES = 80
private const val MAX_DRAFT_BYTES = 1024 * 1024
val ONBOARDING_STEPS = listOf("store", "inventory", "rental", "review")
val OPERATOR_MODES = listOf("self_operated", "operator_required", "either")
private val wireJson = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true }

@Serializable
@Suppress("ConstructorParameterNaming") // Property names are the strict, shared Service wire keys.
data class OnboardingItem(
    val name: String = "",
    val asset_type: String = "",
    val marketplace_category: String = "",
    val quantity: Int = 1,
    val daily_rate_minor: Long = 0,
    val operator_mode: String = "self_operated",
    val classification: JsonObject? = null,
    val unit_names: List<String>? = null,
    val serial_numbers: List<String>? = null,
)

@Serializable
@Suppress("ConstructorParameterNaming") // Property names are the strict, shared Service wire keys.
data class OnboardingDraft(
    val step: String = "store",
    val title: String = "",
    val slug: String = "",
    val currency: String = "USD",
    val mode: String = "same",
    val items: List<OnboardingItem> = emptyList(),
    val booking_mode: String = "quote",
    val contact_email: String = "",
    val contact_phone: String = "",
    val location_id: String? = null,
) {
    fun body(): JsonObject = wireJson.encodeToJsonElement(serializer(), this).jsonObject
    companion object {
        fun parse(o: JsonObject) = wireJson.decodeFromJsonElement(serializer(), o)
    }
}

@Serializable
data class ItemEditor(
    val id: String = java.util.UUID.randomUUID().toString(),
    val item: OnboardingItem = OnboardingItem(),
    val rate: String = "",
    val quantity: String = "1",
    val serials: String = "",
)

@Serializable
data class OnboardingEditor(
    val draft: OnboardingDraft = OnboardingDraft(),
    val rows: List<ItemEditor> = emptyList(),
    val importError: String? = null,
) {
    /** Adding rows never discards existing offerings or forces an explicit single-row mode. */
    fun appendRows(added: List<ItemEditor>): OnboardingEditor {
        require(rows.size + added.size <= MAX_INVENTORY) { "inventory_limit: 500; no rows added" }
        val combined = rows + added
        return copy(draft = draft.copy(mode = if (combined.size > 1) "different" else draft.mode), rows = combined)
    }

    fun pasteNames(text: String, shared: ItemEditor): OnboardingEditor {
        val names = text.lines().map(String::trim).filter(String::isNotBlank)
        require(names.isNotEmpty()) { "No nonblank names found." }
        return copy(draft = draft.copy(mode = "different")).appendRows(names.map { name ->
            shared.copy(
                id = java.util.UUID.randomUUID().toString(), item = shared.item.copy(name = name),
                quantity = "1", serials = "",
            )
        })
    }

    /** UI-only fields can never enter the strict Service draft body. */
    fun toDraft(taxonomy: AssetTaxonomy?): OnboardingDraft = draft.copy(items = rows.map { row ->
        row.item.copy(
            quantity = row.quantity.toIntOrNull() ?: error("quantity"),
            daily_rate_minor = if (row.rate.isBlank()) 0
                else parseMinor(row.rate, draft.currency, allowZero = true) ?: error("daily_rate"),
            marketplace_category = taxonomy?.legacy(row.item.classification) ?: row.item.marketplace_category,
            serial_numbers = row.serials.lines().map(String::trim).let { values ->
                if (values.all(String::isBlank)) null else values
            },
        )
    })
    fun localJson() = wireJson.encodeToString(serializer(), this)
    companion object {
        fun local(text: String) = wireJson.decodeFromString(serializer(), text)
        fun from(draft: OnboardingDraft) = OnboardingEditor(draft, draft.items.map {
            ItemEditor(
                item = it, rate = minorInput(it.daily_rate_minor, draft.currency),
                quantity = it.quantity.toString(), serials = it.serial_numbers.orEmpty().joinToString("\n"),
            )
        })
    }
}

fun slugFromTitle(title: String): String = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(MAX_SLUG_BYTES).trimEnd('-')

internal fun bounded(value: String, bytes: Int) =
    value.toByteArray(Charsets.UTF_8).size <= bytes && value.none(Char::isISOControl)

/** Save permits partial drafts; publish checks canonical type/serial/taxonomy metadata. */
fun validateDraft(
    draft: OnboardingDraft,
    catalog: AssetTypeCatalog?,
    taxonomy: AssetTaxonomy?,
    publish: Boolean,
): List<String> {
    val errors = validateStore(draft, publish).toMutableList()
    if (draft.step !in ONBOARDING_STEPS) errors += "step"
    if (draft.mode !in listOf("same", "different")) errors += "mode"
    if (draft.booking_mode !in listOf("quote", "hold")) errors += "booking_mode"
    if (draft.items.size > MAX_INVENTORY || draft.items.sumOf { it.quantity.toLong() } > MAX_INVENTORY) {
        errors += "inventory_limit"
    }
    if (publish && !validInventory(draft.mode, draft.items.map { it.quantity })) errors += "inventory"
    val serials = mutableSetOf<String>()
    draft.items.forEachIndexed { index, item ->
        val prefix = "row ${index + 1}: "
        errors += validateItem(item, publish).map { prefix + it }
        errors += validateIdentifiers(item, catalog, taxonomy, publish).map { prefix + it }
        item.serial_numbers.orEmpty().filter(String::isNotBlank).forEach {
            if (!serials.add(it.trim().lowercase(Locale.ROOT))) errors += prefix + "duplicate_serial"
        }
    }
    if (draft.body().toString().toByteArray(Charsets.UTF_8).size > MAX_DRAFT_BYTES) errors += "draft_limit"
    return errors
}

private fun validateStore(draft: OnboardingDraft, publish: Boolean): List<String> {
    val errors = mutableListOf<String>()
    if (!bounded(draft.title, MAX_TEXT_BYTES) || publish && draft.title.isBlank()) errors += "title"
    if (!bounded(draft.slug, MAX_SLUG_BYTES) || publish && !validSlug(draft.slug)) errors += "slug"
    if (!bounded(draft.currency, MAX_CURRENCY_BYTES) || publish && currencyDigits(draft.currency) == null) {
        errors += "currency"
    }
    return errors + validateContact(draft, publish)
}

private fun validateContact(draft: OnboardingDraft, publish: Boolean): List<String> {
    val errors = mutableListOf<String>()
    val emailValid = draft.contact_email.isBlank() ||
        Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(draft.contact_email)
    if (!bounded(draft.contact_email, MAX_EMAIL_BYTES) || publish && !emailValid) errors += "contact_email"
    if (!bounded(draft.contact_phone, MAX_PHONE_BYTES)) errors += "contact_phone"
    if (draft.location_id != null && runCatching { java.util.UUID.fromString(draft.location_id) }.isFailure) {
        errors += "location_id"
    }
    return errors
}

private const val MIN_SLUG_BYTES = 3
private fun validSlug(slug: String) =
    slug.length in MIN_SLUG_BYTES..MAX_SLUG_BYTES && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(slug)

private fun validateItem(item: OnboardingItem, publish: Boolean): List<String> {
    val errors = mutableListOf<String>()
    if (!bounded(item.name, MAX_TEXT_BYTES) || publish && item.name.isBlank()) errors += "name"
    if (!bounded(item.asset_type, MAX_TYPE_BYTES) || !bounded(item.marketplace_category, MAX_TYPE_BYTES)) {
        errors += "type"
    }
    if (item.quantity !in 1..MAX_INVENTORY) errors += "quantity"
    if (item.daily_rate_minor !in 0..SAFE_MINOR || publish && item.daily_rate_minor == 0L) errors += "daily_rate"
    if (item.operator_mode !in OPERATOR_MODES) errors += "operator_mode"
    return errors
}

private fun validateIdentifiers(
    item: OnboardingItem,
    catalog: AssetTypeCatalog?,
    taxonomy: AssetTaxonomy?,
    publish: Boolean,
): List<String> {
    val errors = mutableListOf<String>()
    if (publish && (catalog?.info(item.asset_type) == null || !catalog.isVisible(item.asset_type))) {
        errors += "asset_type"
    }
    val classified = taxonomy?.valid(item.classification) == true &&
        taxonomy.legacy(item.classification) == item.marketplace_category
    if (publish && !classified) errors += "classification"
    return errors + validateUnitArrays(item, catalog, publish)
}

private fun validateUnitArrays(
    item: OnboardingItem,
    catalog: AssetTypeCatalog?,
    publish: Boolean,
): List<String> {
    val errors = mutableListOf<String>()
    val ids = item.serial_numbers.orEmpty()
    val serialBounds = ids.size <= item.quantity && ids.all { bounded(it, MAX_TEXT_BYTES) }
    val serialComplete = validSerials(catalog?.info(item.asset_type)?.serialRequired == true, item.quantity, ids)
    if (!serialBounds || publish && !serialComplete) errors += "serial_numbers"
    val names = item.unit_names.orEmpty()
    val nameBounds = names.size <= item.quantity && names.all { bounded(it, MAX_TEXT_BYTES) }
    val namesComplete = names.isEmpty() || (names.size == item.quantity && names.none(String::isBlank))
    if (!nameBounds || publish && !namesComplete) errors += "unit_names"
    return errors
}

/** Account-scoped parser output only. Keep every nonblank row, including incomplete/invalid rows. */
fun importRows(parsed: JsonObject, shared: ItemEditor): List<ItemEditor> {
    require(parsed["truncated"]?.jsonPrimitive?.booleanOrNull == false) { "Import truncated; choose a smaller file." }
    val columns = ImportColumns(parsed)
    val rows = parsed.getValue("rows").jsonArray.map { it.jsonArray.map { cell -> cell.jsonPrimitive.content } }
        .filter { it.any(String::isNotBlank) }
    require(rows.size <= MAX_INVENTORY) { "More than 500 rows; no rows imported." }
    require(rows.isNotEmpty()) { "No nonblank rows found." }
    fun List<String>.cell(vararg keys: String): String =
        columns.cell(this, *keys)
    return rows.map { cells ->
        shared.copy(id = java.util.UUID.randomUUID().toString(), item = shared.item.copy(
            name = cells.cell("name", "asset_name", "title"),
            asset_type = cells.cell("asset_type", "type").ifBlank { shared.item.asset_type },
            operator_mode = cells.cell("operator_mode").ifBlank { shared.item.operator_mode },
            classification = columns.classification(cells, shared.item.classification),
        ), quantity = cells.cell("quantity", "qty").ifBlank { "1" },
            rate = cells.cell("daily_rate", "rate_day", "rental_rate", "rental_price").ifBlank { shared.rate },
            serials = cells.cell("serial_number", "serial", "serial_numbers").replace(';', '\n'))
    }
}

private class ImportColumns(parsed: JsonObject) {
    private val headers = parsed.getValue("headers").jsonArray.map { key(it.jsonPrimitive.content) }
    private val mapping = (parsed["mapping"] as? JsonObject).orEmpty().mapKeys { key(it.key) }

    private fun key(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "_")

    fun cell(row: List<String>, vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        mapping[key]?.jsonPrimitive?.intOrNull ?: headers.indexOf(key).takeIf { it >= 0 }
    }?.let { row.getOrNull(it) }.orEmpty().trim()

    fun classification(row: List<String>, fallback: JsonObject?): JsonObject? {
        val json = cell(row, "classification")
        val category = cell(row, "category_id", "taxonomy_category_id")
        val subcategory = cell(row, "subcategory_id", "taxonomy_subcategory_id")
        val version = cell(row, "taxonomy_version")
        return when {
            // Explicit malformed JSON must stay review-needed, not adopt a shared manual classification.
            json.isNotBlank() ->
                runCatching { Json.parseToJsonElement(json).jsonObject }.getOrElse { JsonObject(emptyMap()) }
            listOf(category, subcategory, version).all(String::isBlank) -> fallback
            else -> buildJsonObject {
                put("category_id", category)
                put("subcategory_id", subcategory)
                put("taxonomy_version", version.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(version))
                put("source", "manual")
            }
        }
    }
}
