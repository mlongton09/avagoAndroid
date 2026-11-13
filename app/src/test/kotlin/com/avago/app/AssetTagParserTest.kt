package com.avago.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AssetTagParserTest {

    private val id = "0b9c6a8e-1f2d-4c3b-9a8e-7d6c5b4a3f21"

    @Test
    fun `reads ids from web urls custom scheme and plain text`() {
        assertEquals(id, AssetTagParser.assetId("https://www.avagomate.com/assets/$id"))
        assertEquals(id, AssetTagParser.assetId("https://www.avagomate.com/assets/$id/log/abc/report?x=1"))
        assertEquals(id, AssetTagParser.assetId("avago://asset/$id"))
        assertEquals(id, AssetTagParser.assetId("  $id\n"))
    }

    @Test
    fun `rejects unrelated payloads`() {
        assertNull(AssetTagParser.assetId("https://example.com/somewhere"))
        assertNull(AssetTagParser.assetId("https://www.avagomate.com/assets/new"))
        assertNull(AssetTagParser.assetId("hello world"))
        assertNull(AssetTagParser.assetId(""))
        assertNull(AssetTagParser.assetId("mailto:x@y.z"))
    }

    @Test
    fun `decodes rtd text payloads`() {
        val utf8 = byteArrayOf(0x02, 'e'.code.toByte(), 'n'.code.toByte()) + "A-12".toByteArray(Charsets.UTF_8)
        assertEquals("A-12", AssetTagParser.decodeRtdText(utf8))
        assertNull(AssetTagParser.decodeRtdText(byteArrayOf(0x05, 'e'.code.toByte())))
    }
}
