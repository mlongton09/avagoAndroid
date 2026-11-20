package com.avago.feature.rentals.onboarding

import java.time.Year
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.ApiError
import com.avago.feature.assets.model.AssetTaxonomy
import com.avago.feature.assets.model.AssetTypeCatalog
import com.avago.feature.assets.model.AssetTypeInfo
import com.avago.feature.assets.model.AssetSuggestionTarget
import com.avago.feature.assets.model.text
import com.avago.feature.assets.model.validAssetMetadata
import com.avago.feature.assets.model.mergeAssetAttributes
import com.avago.feature.assets.model.decodeAssetAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

class OnboardingTest {
    private val fixture = Json.parseToJsonElement(
        javaClass.getResource("/mobile-rental-onboarding-cases.json")!!.readText(),
    ).jsonObject
    // Bounded metadata fixture, not an app catalog. Production always reads GET /asset-taxonomy.
    private val taxonomy = AssetTaxonomy.parse(Json.parseToJsonElement("""
        {"version":1,"categories":[
        {"id":"power_distribution","legacy_marketplace_category":"tools","subcategories":[{"id":"power_distribution.generators"}]},
        {"id":"agriculture","legacy_marketplace_category":"farm","subcategories":[{"id":"agriculture.tractors"}]}]}
    """).jsonObject)
    private val catalog = AssetTypeCatalog(false, listOf("light_vehicle", "power_tools"), emptySet(), entries = listOf(
        AssetTypeInfo("light_vehicle", "road", "vehicle", "distance", false, true),
        AssetTypeInfo("power_tools", "tools", "tool", "hours", true, false),
    ))
    private val classification get() = fixture.getValue("classification").jsonObject.getValue("valid").jsonObject
    private fun item() = OnboardingItem("Generator", "light_vehicle", "tools", 1, 1250, classification = classification)
    private fun draft(items: List<OnboardingItem> = listOf(item()), mode: String = "same") =
        OnboardingDraft(title = "Farm rentals", slug = "farm-rentals", items = items, mode = mode)

    @Test fun `shared metadata cases use same validator as ordinary asset save`() {
        fixture.getValue("new_asset_validation").jsonArray.forEach {
            val c = it.jsonObject
            assertEquals(
                c.getValue("valid").jsonPrimitive.boolean,
                validAssetMetadata(c.text("name"), c.text("year")), c.text("id"),
            )
        }
        assertFalse(validAssetMetadata("Pickup", "abcd"))
        assertFalse(validAssetMetadata("Pickup", "2"))
        assertTrue(validAssetMetadata("Pickup", "2024"))
    }

    @Test fun `shared exact money fixture rejects unsafe integers and excess precision`() {
        fixture.getValue("money").jsonArray.forEach {
            val c = it.jsonObject
            val minor = parseMinor(c.text("major"), c.text("currency"))
            assertEquals(c.getValue("valid").jsonPrimitive.boolean, minor != null, c.text("id"))
            if (minor != null) assertEquals(c.getValue("minor").jsonPrimitive.long, minor)
        }

        assertEquals(SAFE_MINOR, parseMinor("90071992547409.91", "USD"))
        assertNull(parseMinor("1e3", "USD"))
        assertNull(parseMinor("1,25", "USD"))
        assertNull(parseMinor("0".repeat(100_000) + "1", "USD"))
        assertEquals("1.250", minorInput(1250, "KWD"))
        assertEquals(0L, parseMinor("0", "USD", allowZero = true))
    }

    @Test fun `metadata year uses current Gregorian year plus one and four ASCII digits`() {
        val currentYear = Year.now().value
        assertTrue(validAssetMetadata("Pickup", ""))
        assertTrue(validAssetMetadata("Pickup", "   "))
        assertTrue(validAssetMetadata("Pickup", " 1886 "))
        assertTrue(validAssetMetadata("Pickup", currentYear.toString()))
        assertTrue(validAssetMetadata("Pickup", " ${currentYear + 1} "))
        assertFalse(validAssetMetadata("Pickup", (currentYear + 2).toString()))
        assertFalse(validAssetMetadata("Pickup", "1885"))
        assertFalse(validAssetMetadata("Pickup", "２０２４"))
        assertFalse(validAssetMetadata("Pickup", "٢٠٢٤"))
        assertFalse(validAssetMetadata("Pickup", "02024"))
    }

    @Test fun `metadata name enforces trimmed UTF8 bytes and rejects ISO controls`() {
        assertFalse(validAssetMetadata("   ", ""))
        assertTrue(validAssetMetadata("  Pickup  ", ""))
        assertTrue(validAssetMetadata(" ${"a".repeat(200)} ", ""))
        assertFalse(validAssetMetadata("a".repeat(201), ""))
        assertTrue(validAssetMetadata("é".repeat(100), ""))
        assertFalse(validAssetMetadata("é".repeat(101), ""))
        assertTrue(validAssetMetadata("🚜".repeat(50), ""))
        assertFalse(validAssetMetadata("🚜".repeat(51), ""))
        listOf('\u0000', '\t', '\n', '\r', '\u001f', '\u007f', '\u0085', '\u009f').forEach { control ->
            assertFalse(validAssetMetadata("Pick${control}up", ""), "Control ${control.code}")
            assertFalse(validAssetMetadata("${control}Pickup", ""), "Leading control ${control.code}")
            assertFalse(validAssetMetadata("Pickup$control", ""), "Trailing control ${control.code}")
        }
    }

    @Test fun `10 and 488 and 500 inventories run complete UI publish validation`() {
        fixture.getValue("inventory").jsonArray.forEach {
            val c = it.jsonObject
            val items = List(c.getValue("row_count").jsonPrimitive.int) {
                item().copy(quantity = c.getValue("quantity_per_row").jsonPrimitive.int)
            }
            val errors = validateDraft(draft(items, c.text("mode")), catalog, taxonomy, publish = true)
            assertEquals(c.getValue("valid").jsonPrimitive.boolean, errors.isEmpty(), "${c.text("id")}: $errors")
            val editor = OnboardingEditor.from(draft(items, c.text("mode")))
            assertEquals(items.size, editor.toDraft(taxonomy).items.size)
        }
        assertFalse(validateDraft(draft(List(501) { item() }, "different"), catalog, taxonomy, false).isEmpty())
    }

    @Test fun `serial fixture and cross row duplicates use real canonical policy`() {
        fixture.getValue("serials").jsonArray.forEach {
            val c = it.jsonObject
            assertEquals(c.getValue("valid").jsonPrimitive.boolean, validSerials(
                c.getValue("required").jsonPrimitive.boolean, c.getValue("quantity").jsonPrimitive.int,
                c.getValue("serials").jsonArray.map { it.jsonPrimitive.content }), c.text("id"))
        }
        val tool = item().copy(asset_type = "power_tools", serial_numbers = listOf("Real-1"))
        assertTrue(validateDraft(draft(listOf(tool)), catalog, taxonomy, true).isEmpty())
        val missing = validateDraft(draft(listOf(tool.copy(serial_numbers = null))), catalog, taxonomy, true)
        assertTrue(missing.any { it.contains("serial_numbers") })
        val duplicates = draft(listOf(tool, tool.copy(serial_numbers = listOf("real-1"))), "different")
        assertTrue(validateDraft(duplicates, catalog, taxonomy, true).any { it.contains("duplicate_serial") })
    }

    @Test fun `classification checks parent version and manual wins late AI`() {
        val cases = fixture.getValue("classification").jsonObject
        assertTrue(taxonomy.valid(classification))
        assertFalse(taxonomy.valid(cases.getValue("mismatched").jsonObject))
        assertFalse(taxonomy.valid(JsonObject(classification + ("taxonomy_version" to JsonPrimitive(2)))))
        val suggested = JsonObject(cases.getValue("other_valid").jsonObject + mapOf(
            "source" to JsonPrimitive("qwen"), "confidence" to JsonPrimitive(0.9),
            "requires_review" to JsonPrimitive(false),
        ))
        assertEquals(classification, taxonomy.acceptSuggestion(classification, suggested))
        assertEquals("agriculture", taxonomy.acceptSuggestion(null, suggested)?.text("category_id"))
        assertNull(taxonomy.acceptSuggestion(null, JsonObject(suggested + ("confidence" to JsonPrimitive(0.79)))))
        listOf(0.8, 1.0).forEach { confidence ->
            val response = JsonObject(suggested + ("confidence" to JsonPrimitive(confidence)))
            assertEquals("agriculture", taxonomy.acceptSuggestion(null, response)?.text("category_id"))
        }
        listOf(1.01, 2.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { confidence ->
            val response = JsonObject(suggested + ("confidence" to JsonPrimitive(confidence)))
            assertNull(taxonomy.acceptSuggestion(null, response), "Confidence $confidence")
            assertEquals(classification, taxonomy.acceptSuggestion(classification, response))
        }
        assertNull(taxonomy.acceptSuggestion(null, JsonObject(suggested + ("requires_review" to JsonPrimitive(true)))))
        val target = AssetSuggestionTarget("account-a", "row-a", "Generator", "light_vehicle")
        assertTrue(target.matches(target.copy(), null))
        assertFalse(target.matches(target.copy(account = "account-b"), null))
        assertFalse(target.matches(target.copy(row = "row-b"), null))
        assertFalse(target.matches(target.copy(name = "Tractor"), null))
        assertFalse(target.matches(target, classification))
        assertFalse(taxonomy.valid(JsonObject(classification + ("taxonomy_version" to JsonObject(emptyMap())))))
        assertFalse(taxonomy.valid(JsonObject(classification + ("taxonomy_version" to JsonPrimitive("1")))))
    }

    @Test fun `ordinary legacy edits preserve unknown JSON attributes and classification`() {
        val original = Json.parseToJsonElement("""
            {"nested":{"keep":[1,true]},"classification":{"legacy":"unknown"},"fleet_number":"42"}
        """).jsonObject
        val edited = mergeAssetAttributes(original, buildJsonObject { put("notes", "new note") })
        assertEquals(original["nested"], edited["nested"])
        assertEquals(original["classification"], edited["classification"])
        assertEquals("42", edited.text("fleet_number"))
        val classified = mergeAssetAttributes(original, buildJsonObject { put("classification", classification) })
        assertEquals(original["nested"], classified["nested"])
        assertEquals(classification, classified["classification"])
        assertEquals(original, decodeAssetAttributes(original.toString()))
        assertThrows(IllegalArgumentException::class.java) { decodeAssetAttributes("""["legacy"]""") }
    }

    @Test fun `strict wire keys omit UI editor fields preserve revision and exact money`() {
        val source = OnboardingEditor.from(draft()).copy(
            rows = listOf(ItemEditor(item = item(), rate = "1.250", quantity = "488")),
        )
            .let { it.copy(draft = it.draft.copy(currency = "KWD")) }
        val wire = OnboardingRepository.saveBody(7, source.toDraft(taxonomy))
        assertEquals(setOf("revision", "draft"), wire.keys)
        assertEquals(setOf(
            "step", "title", "slug", "currency", "mode", "items",
            "booking_mode", "contact_email", "contact_phone", "location_id",
        ), wire.getValue("draft").jsonObject.keys)
        val row = wire.getValue("draft").jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals(setOf(
            "name", "asset_type", "marketplace_category", "quantity", "daily_rate_minor",
            "operator_mode", "classification", "unit_names", "serial_numbers",
        ), row.keys)
        assertEquals(1250L, row.getValue("daily_rate_minor").jsonPrimitive.long)
        assertEquals(488, row.getValue("quantity").jsonPrimitive.int)
        assertEquals(JsonNull, row["unit_names"])
        assertEquals(buildJsonObject { put("revision", 8) }, OnboardingRepository.publishBody(8))
        assertEquals(source, OnboardingEditor.local(source.localJson()))
    }

    @Test fun `partial saves are allowed publication needs receipt and errors preserve draft`() {
        assertTrue(validateDraft(OnboardingDraft(currency = ""), catalog, taxonomy, false).isEmpty())
        assertFalse(validateDraft(OnboardingDraft(currency = ""), catalog, taxonomy, true).isEmpty())
        val local = DraftSession(OnboardingEditor.from(draft()), 4)
        listOf(409, 403, 404, 405, 501, 500, 0).forEach { status ->
            val failed = local.receive(ApiResult.Err(ApiError(status, "test_error", "test")), false)
            assertEquals(local.editor, failed.editor)
            assertEquals(4L, failed.revision)
            assertNotNull(failed.error)
        }
        val server = buildJsonObject { put("revision", 5); put("draft", draft().copy(title = "Server title").body()) }
        val resumed = local.receive(ApiResult.Ok(server), replaceEditor = false)
        assertEquals(local.editor, resumed.editor)
        assertEquals(5L, resumed.revision)
        val conflictedResume = local.receive(ApiResult.Ok(server), replaceEditor = false, rebase = false)
        assertEquals(local.editor, conflictedResume.editor)
        assertEquals(local.revision, conflictedResume.revision)
        assertTrue(conflictedResume.error!!.startsWith("revision_conflict"))
        assertFalse(local.published(buildJsonObject { put("storefront_published", false) }).publishedSuccessfully)
        val published = local.published(buildJsonObject {
            put("storefront_published", true)
            put("requests_ready", true)
            put("payments_configured", false)
        })
        assertTrue(published.publishedSuccessfully)
        assertEquals("false", published.receipt?.text("payments_configured"))
        assertTrue(validateDraft(draft(), catalog, taxonomy, true).isEmpty()) // no location or gateway needed
    }

    @Test fun `parser retains incomplete rows and invalid fields and rejects truncation`() {
        val parsed = Json.parseToJsonElement("""
            {"truncated":false,"headers":["name","quantity","daily_rate"],"mapping":{"name":0},
            "rows":[["First","1","12.50"],["","2","bad"],["  "," "," "],["Third","501","3"]]}
        """).jsonObject
        val rows = importRows(parsed, ItemEditor(item = item(), rate = "5.00"))
        assertEquals(3, rows.size)
        assertEquals("", rows[1].item.name)
        assertEquals("bad", rows[1].rate)
        assertEquals("501", rows[2].quantity)
        assertThrows(IllegalArgumentException::class.java) {
            importRows(JsonObject(parsed + ("truncated" to JsonPrimitive(true))), ItemEditor())
        }
        assertThrows(IllegalArgumentException::class.java) {
            val tooMany = JsonArray(List(501) { JsonArray(listOf(JsonPrimitive("Name"))) })
            importRows(JsonObject(parsed + ("rows" to tooMany)), ItemEditor())
        }
    }

    private fun parsedRow(
        headers: List<String>, cells: List<String>, mapping: JsonObject = JsonObject(emptyMap()),
    ) = buildJsonObject {
        put("truncated", false)
        put("headers", JsonArray(headers.map(::JsonPrimitive)))
        put("rows", JsonArray(listOf(JsonArray(cells.map(::JsonPrimitive)))))
        put("mapping", mapping)
    }

    @Test fun `adding a second row changes mode without discarding rows or explicit mode edits`() {
        val first = ItemEditor(item = item(), rate = "12.50")
        val second = first.copy(id = "second", item = item().copy(name = "Second"))
        val one = OnboardingEditor(draft(items = emptyList())).appendRows(listOf(first))
        assertEquals("same", one.draft.mode)
        val two = one.appendRows(listOf(second))
        assertEquals("different", two.draft.mode)
        assertEquals(listOf(first, second), two.rows)
        assertTrue(validateDraft(two.toDraft(taxonomy), catalog, taxonomy, true).isEmpty())
        val explicitSame = two.copy(draft = two.draft.copy(mode = "same"))
        assertEquals(two.rows, explicitSame.rows)
        assertFalse(validateDraft(explicitSame.toDraft(taxonomy), catalog, taxonomy, true).isEmpty())
        val full = one.copy(rows = List(500) { first.copy(id = "row-$it") })
        val pasted = "New name\nAnother name"
        assertThrows(IllegalArgumentException::class.java) { full.pasteNames(pasted, first) }
        assertEquals(500, full.rows.size)
        assertEquals(first, full.rows.first().copy(id = first.id))
        val successful = one.pasteNames(" Pasted name \n\nOther", first)
        assertEquals("different", successful.draft.mode)
        assertEquals(listOf("Generator", "Pasted name", "Other"), successful.rows.map { it.item.name })
    }

    @Test fun `import supports every shared rate and serial alias with normalized headers and mapping keys`() {
        val shared = ItemEditor(item = item(), rate = "5.00")
        listOf("daily_rate", "rate_day", "rental_rate", "rental_price").forEach { rate ->
            listOf("serial_number", "serial", "serial_numbers").forEach { serial ->
                val headers = listOf("name", "asset_type", "quantity", rate, serial)
                    .map { " ${it.replace('_', ' ').uppercase(java.util.Locale.ROOT)} " }
                val cells = listOf("Imported", "power_tools", "2", "12.50", "Real-A;Real-B")
                val row = importRows(parsedRow(headers, cells), shared).single()
                assertEquals("12.50", row.rate, rate)
                assertEquals("Real-A\nReal-B", row.serials, serial)
                val editor = OnboardingEditor(draft(items = emptyList())).appendRows(listOf(row))
                assertTrue(validateDraft(editor.toDraft(taxonomy), catalog, taxonomy, true).isEmpty())
            }
        }
        val mapped = parsedRow(
            listOf("Label", "Kind", "Count", "Amount", "Identifier"),
            listOf("Mapped", "power_tools", "1", "3.25", "Real-X"),
            buildJsonObject {
                put(" NAME ", 0); put("ASSET TYPE", 1); put("QUANTITY", 2)
                put("RENTAL PRICE", 3); put("SERIAL NUMBER", 4)
            },
        )
        val row = importRows(mapped, shared).single()
        assertEquals("Mapped", row.item.name)
        assertEquals("3.25", row.rate)
        assertEquals("Real-X", row.serials)
    }

    @Test fun `import accepts flat taxonomy aliases and JSON without replacing explicit invalid classifications`() {
        val shared = ItemEditor(item = item(), rate = "5.00")
        val other = fixture.getValue("classification").jsonObject.getValue("other_valid").jsonObject
        listOf("category_id" to "subcategory_id", "taxonomy_category_id" to "taxonomy_subcategory_id").forEach {
            val headers = listOf("name", it.first, it.second, "taxonomy_version")
                .map { key -> key.replace('_', ' ') }
            val cells = listOf("Flat", other.text("category_id"), other.text("subcategory_id"), "1")
            val row = importRows(parsedRow(headers, cells), shared).single()
            assertEquals(other, row.item.classification)
            assertTrue(taxonomy.valid(row.item.classification))
        }
        val json = importRows(parsedRow(listOf("name", "classification"), listOf("JSON", other.toString())), shared)
        assertEquals(other, json.single().item.classification)
        val invalid = listOf(
            parsedRow(listOf("name", "classification"), listOf("Bad JSON", "not-json")),
            parsedRow(listOf("name", "classification"), listOf("Array", "[]")),
            parsedRow(listOf("name", "category_id"), listOf("Incomplete", other.text("category_id"))),
            parsedRow(
                listOf("name", "category_id", "subcategory_id", "taxonomy_version"),
                listOf("Bad version", other.text("category_id"), other.text("subcategory_id"), "bad"),
            ),
            parsedRow(
                listOf("name", "category_id", "subcategory_id", "taxonomy_version"),
                listOf("Mismatch", other.text("category_id"), classification.text("subcategory_id"), "1"),
            ),
        )
        invalid.forEach { parsed ->
            val row = importRows(parsed, shared).single()
            assertNotNull(row.item.classification)
            assertFalse(taxonomy.valid(row.item.classification))
            val editor = OnboardingEditor(draft(items = emptyList())).appendRows(listOf(row))
            val errors = validateDraft(editor.toDraft(taxonomy), catalog, taxonomy, true)
            assertTrue(errors.any { "classification" in it })
        }
        val noExplicit = importRows(parsedRow(listOf("name"), listOf("Shared")), shared).single()
        assertEquals(shared.item.classification, noExplicit.item.classification)
    }

    @Test fun `network fake save then atomic publish and timeout retry reuse revision`() = runTest {
        val body = draft()
        val calls = mutableListOf<String>()
        var failPublish = true
        val fake = object : OnboardingService {
            override suspend fun get(account: String): ApiResult<JsonObject> = error("not called")
            override suspend fun save(
                account: String, revision: Long, draft: OnboardingDraft,
            ): ApiResult<JsonObject> {
                calls += "save:$account:$revision"
                return ApiResult.Ok(buildJsonObject { put("revision", revision + 1); put("draft", draft.body()) })
            }
            override suspend fun publish(account: String, revision: Long): ApiResult<JsonObject> {
                calls += "publish:$account:$revision"
                return if (failPublish) ApiResult.Err(ApiError(0, null, "timeout"))
                else ApiResult.Ok(buildJsonObject {
                    put("storefront_published", true)
                    put("requests_ready", true)
                    put("payments_configured", false)
                })
            }
        }
        val local = DraftSession(OnboardingEditor.from(body), 6)
        val failed = persistOnboarding(fake, "account-a", local, body, true)
        assertEquals(local.editor, failed.editor)
        assertEquals(7, failed.revision.toInt())
        assertFalse(failed.publishedSuccessfully)
        failPublish = false
        val retried = persistOnboarding(fake, "account-a", failed, body, true)
        assertTrue(retried.publishedSuccessfully)
        assertEquals(listOf("save:account-a:6", "publish:account-a:7", "publish:account-a:7"), calls)
    }

    @Test fun `network fake save conflict never publishes or replaces manual draft`() = runTest {
        var publishes = 0
        val fake = object : OnboardingService {
            override suspend fun get(account: String): ApiResult<JsonObject> = error("not called")
            override suspend fun save(account: String, revision: Long, draft: OnboardingDraft) =
                ApiResult.Err(ApiError(409, "stale_revision", "Fetch current state"))
            override suspend fun publish(account: String, revision: Long): ApiResult<JsonObject> {
                publishes++; return ApiResult.Ok(buildJsonObject { put("storefront_published", true) })
            }
        }
        val local = DraftSession(OnboardingEditor.from(draft()), 3)
        val result = persistOnboarding(fake, "a", local, draft(), true)
        assertEquals(0, publishes)
        assertEquals(local.editor, result.editor)
        assertEquals(local.revision, result.revision)
        assertTrue(result.error!!.startsWith("revision_conflict"))
    }
}
