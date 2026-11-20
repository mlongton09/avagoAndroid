package com.avago.feature.assets.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
