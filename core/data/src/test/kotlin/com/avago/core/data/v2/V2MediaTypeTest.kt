package com.avago.core.data.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class V2MediaTypeTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `sniffs every kind the server accepts`() {
        assertEquals("image/jpeg", V2MediaType.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/png", V2MediaType.sniff(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals("image/gif", V2MediaType.sniff(ascii("GIF89a....")))
        assertEquals("image/webp", V2MediaType.sniff(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
        assertEquals("image/heic", V2MediaType.sniff(ascii("\u0000\u0000\u0000\u0018ftypheic")))
        assertEquals("audio/mp4", V2MediaType.sniff(ascii("\u0000\u0000\u0000\u0018ftypM4A ")))
        assertEquals("audio/webm", V2MediaType.sniff(bytes(0x1A, 0x45, 0xDF, 0xA3, 0)))
        assertEquals("application/pdf", V2MediaType.sniff(ascii("%PDF-1.7\n")))
    }

    @Test
    fun `a pdf header is found anywhere in the first kilobyte only`() {
        val within = ByteArray(V2MediaType.SNIFF_BYTES - 5) { ' '.code.toByte() } + ascii("%PDF-")
        assertEquals("application/pdf", V2MediaType.sniff(within))
        val beyond = ByteArray(V2MediaType.SNIFF_BYTES) { ' '.code.toByte() } + ascii("%PDF-")
        assertNull(V2MediaType.sniff(beyond))
    }

    @Test
    fun `unknown or truncated content has no type`() {
        assertNull(V2MediaType.sniff(ByteArray(0)))
        assertNull(V2MediaType.sniff(bytes(0xFF, 0xD8)))
        assertNull(V2MediaType.sniff(ascii("\u0000\u0000\u0000\u0018ftypqt  ")))
        assertNull(V2MediaType.sniff(ascii("plain text")))
    }
}
