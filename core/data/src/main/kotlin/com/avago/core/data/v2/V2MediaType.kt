package com.avago.core.data.v2

/**
 * Content sniffing that mirrors the server's upload check (P3-20, AvagoSvc `media.rs`): the server
 * reads the first bytes of every upload and refuses a body whose kind differs from the type it was
 * registered with (`415 media_type_mismatch`). Registering the sniffed type keeps the two in step
 * whatever the picker or the caller claimed.
 */
object V2MediaType {
    /** How many leading bytes [sniff] looks at (a PDF header may sit anywhere in the first KiB). */
    const val SNIFF_BYTES = 1024

    /** The canonical mime type of [head] (the first bytes of a file), or null for an unknown kind. */
    fun sniff(head: ByteArray): String? = when {
        head.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        head.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        head.ascii(0, 6) == "GIF87a" || head.ascii(0, 6) == "GIF89a" -> "image/gif"
        head.ascii(0, 4) == "RIFF" && head.ascii(8, 4) == "WEBP" -> "image/webp"
        head.startsWith(0x1A, 0x45, 0xDF, 0xA3) -> "audio/webm"
        head.ascii(4, 4) == "ftyp" -> when (head.ascii(8, 4)) {
            in HEIC_BRANDS -> "image/heic"
            in M4A_BRANDS -> "audio/mp4"
            else -> null
        }
        head.containsPdfHeader() -> "application/pdf"
        else -> null
    }

    private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")
    private val M4A_BRANDS = setOf("M4A ", "M4B ", "mp41", "mp42", "isom", "iso2", "dash")
    private val PDF = "%PDF-".toByteArray(Charsets.US_ASCII)

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

    private fun ByteArray.ascii(offset: Int, length: Int): String? =
        if (size >= offset + length) String(this, offset, length, Charsets.ISO_8859_1) else null

    private fun ByteArray.containsPdfHeader(): Boolean {
        val end = minOf(size, SNIFF_BYTES) - PDF.size
        return (0..end).any { start -> PDF.indices.all { this[start + it] == PDF[it] } }
    }
}
