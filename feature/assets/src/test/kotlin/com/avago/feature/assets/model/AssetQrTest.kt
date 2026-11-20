package com.avago.feature.assets.model

import com.avago.core.data.domain.AssetLabelTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AssetQrTest {
    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private val token = "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"

    @Test
    fun `a printed label resolves to its token`() {
        assertEquals(token, AssetQr.tokenFromScan("https://www.avagomate.com/a/$token"))
        assertEquals(token, AssetQr.tokenFromScan(" https://avagomate.com/a/${token.uppercase()} "))
    }

    @Test
    fun `other codes are not labels`() {
        assertNull(AssetQr.tokenFromScan(token))
        assertNull(AssetQr.tokenFromScan("https://evil.example/a/$token"))
        assertNull(AssetQr.tokenFromScan("https://www.avagomate.com/a/not-a-uuid"))
        assertNull(AssetQr.tokenFromScan("https://www.avagomate.com/p/$token"))
        assertNull(AssetQr.tokenFromScan("VIN1234567890"))
    }

    @Test
    fun `token responses and the account token map parse`() {
        assertEquals(QrToken(token, AssetQr.url(token)), AssetQr.token(json("""{"data":{"token_id":"$token"}}""")))
        assertNull(AssetQr.token(json("""{"token_id":null}""")))
        assertEquals(
            QrToken(token, "https://x/a/$token"),
            AssetQr.token(json("""{"token_id":"$token","url":"https://x/a/$token"}""")),
        )
        assertEquals(
            mapOf(token to "asset-1"),
            AssetQr.tokenAssets(json("""{"tokens":[{"asset_id":"asset-1","token_id":"${token.uppercase()}"},{"asset_id":"x"}]}""")),
        )
    }

    @Test
    fun `scan pages know whether more follow`() {
        val page = AssetQr.scans(
            json(
                """{"scans":[{"id":"s1","scanned_by_user_id":null,"scanned_at":"2026-03-01T10:00:00Z",
                "device_info":"Pixel","latitude":48.1,"longitude":11.5}],"total":21,"page":1,"page_size":20}""",
            ),
        )
        assertEquals(listOf("s1"), page.scans.map { it.id })
        assertEquals(48.1, page.scans.single().latitude)
        assertTrue(page.hasMore)
        assertFalse(page.copy(page = 2).hasMore)
    }

    @Test
    fun `a label job downloads only once completed with an output file`() {
        val done = AssetQr.labelJob(json("""{"data":{"id":"j1","status":"completed","page_size":"avery_5160","asset_count":12,"output_file_id":"f1"}}"""))!!
        assertEquals(12, done.assetCount)
        assertEquals("avery_5160", done.pageSize)
        assertTrue(done.completed)
        assertFalse(AssetQr.labelJob(json("""{"id":"j2","status":"pending","asset_count":3}"""))!!.completed)
        assertNull(AssetQr.labelJob(json("""{"status":"completed"}""")))
        assertNull(done.templateName)
    }

    @Test
    fun `a label job names the template it printed with`() {
        val job = AssetQr.labelJob(
            json(
                """{"id":"j1","status":"completed","page_size":"thermal","asset_count":1,"output_file_id":"f1",
                "label_template":{"template_id":"t1","name":"Shelf 50x25","width_mm":50,"height_mm":25,"barcode_format":"qr","layout":[]}}""",
            ),
        )!!
        assertEquals("Shelf 50x25", job.templateName)
    }

    @Test
    fun `the batch body names a chosen template and omits it for the account default`() {
        assertEquals(
            json("""{"asset_ids":["a1","a2"],"format":"pdf","page_size":"thermal","label_template":{"template_id":"t1"}}"""),
            AssetQr.labelSheetBody(listOf("a1", "a2"), QrLabelLayout.THERMAL, "t1"),
        )
        assertEquals(
            json("""{"asset_ids":["a1"],"format":"pdf","page_size":"letter"}"""),
            AssetQr.labelSheetBody(listOf("a1"), QrLabelLayout.LETTER, null),
        )
    }

    @Test
    fun `layouts follow the server sheet rules`() {
        assertEquals(QrLabelLayout.entries - QrLabelLayout.THERMAL, QrLabelLayout.entries.filter { it.accepts(null) })

        val small = template("s", 50.0, 25.0)
        assertEquals(QrLabelLayout.entries.toList(), QrLabelLayout.entries.filter { it.accepts(small) })

        val wide = template("w", 100.0, 38.1)
        assertEquals(
            listOf(QrLabelLayout.LETTER, QrLabelLayout.A4, QrLabelLayout.THERMAL, QrLabelLayout.AVERY_5163),
            QrLabelLayout.entries.filter { it.accepts(wide) },
        )

        // Letter is 215.9 mm wide: 203.2 mm fits inside the 6.35 mm margins, A4's 197.3 mm does not.
        val sheetWide = template("x", 203.2, 30.0)
        assertEquals(listOf(QrLabelLayout.LETTER, QrLabelLayout.THERMAL), QrLabelLayout.entries.filter { it.accepts(sheetWide) })
        assertEquals(listOf(QrLabelLayout.THERMAL), QrLabelLayout.entries.filter { it.accepts(template("y", 300.0, 300.0)) })
    }

    @Test
    fun `a chosen template wins over the account default`() {
        val default = template("d", 50.0, 25.0, isDefault = true)
        val other = template("o", 60.0, 30.0)
        assertEquals(other, effectiveLabelTemplate(listOf(default, other), "o"))
        assertEquals(default, effectiveLabelTemplate(listOf(default, other), null))
        assertEquals(default, effectiveLabelTemplate(listOf(default, other), "gone"))
        assertNull(effectiveLabelTemplate(listOf(other), null))
    }

    private fun template(id: String, w: Double, h: Double, isDefault: Boolean = false) =
        AssetLabelTemplate(id, "Template $id", BigDecimal.valueOf(w), BigDecimal.valueOf(h), isDefault)
}
