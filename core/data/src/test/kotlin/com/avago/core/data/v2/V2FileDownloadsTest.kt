package com.avago.core.data.v2

import com.avago.core.data.domain.FileUnavailableException
import com.avago.core.data.domain.FileVariant
import com.avago.core.data.v2.V2Fixtures.error
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class V2FileDownloadsTest {
    @TempDir lateinit var root: File

    private val fileId = V2Uuid.v7()
    private val transport = MockTransport()
    private val api = V2Api(transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE)
    private val fetched = mutableListOf<String>()
    private var blobStatus = 200
    private val downloads = V2FileDownloads(root = File("unset"), blobs = { _, _ -> 0 })

    private fun downloads() = V2FileDownloads(root, { url, target ->
        fetched += url
        if (blobStatus in 200..299) target.writeText("bytes of $url")
        blobStatus
    })

    private fun link(thumb: String? = "https://s/thumb") = V2Response(
        200,
        """{"file_id":"$fileId","url":"https://s/orig","thumbnail_url":${thumb?.let { "\"$it\"" } ?: "null"},""" +
            """"mime_type":"image/jpeg","size_bytes":5,"original_name":"a.jpg"}""",
    )

    @Test
    fun `a miss asks for a signed url once and later reads hit the cache`() = runTest {
        transport.route = { link() }
        val cache = downloads()

        val first = cache.fetch(api, fileId, FileVariant.ORIGINAL)
        val second = cache.fetch(api, fileId, FileVariant.ORIGINAL)

        assertEquals(first, second)
        assertEquals("bytes of https://s/orig", first.readText())
        assertEquals(1, transport.matching("GET", "/files/$fileId/download").size)
        assertEquals(listOf("https://s/orig"), fetched)
        assertEquals(File(File(root, V2Fixtures.ACCOUNT), fileId), first)
    }

    @Test
    fun `the thumbnail variant uses the thumbnail url and falls back to the original`() = runTest {
        transport.route = { link(thumb = null) }
        val cache = downloads()

        val thumb = cache.fetch(api, fileId, FileVariant.THUMBNAIL)

        assertEquals("$fileId.thumb", thumb.name)
        assertEquals(listOf("https://s/orig"), fetched)

        transport.route = { link() }
        val other = V2Uuid.v7()
        transport.route = { link().copy(body = link().body.replace(fileId, other)) }
        cache.fetch(api, other, FileVariant.THUMBNAIL)
        assertEquals("https://s/thumb", fetched.last())
    }

    @Test
    fun `an unverified file and a refused download leave nothing cached`() = runTest {
        val cache = downloads()
        transport.route = { error(404, "file_not_found") }
        assertTrue(runCatching { cache.fetch(api, fileId, FileVariant.ORIGINAL) }.exceptionOrNull() is FileUnavailableException)

        transport.route = { link() }
        blobStatus = 403
        assertTrue(runCatching { cache.fetch(api, fileId, FileVariant.ORIGINAL) }.exceptionOrNull() is FileUnavailableException)

        val dir = File(root, V2Fixtures.ACCOUNT)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `trim evicts least recently used files down to the cap`() {
        val dir = File(root, V2Fixtures.ACCOUNT).apply { mkdirs() }
        val old = File(dir, "old").apply { writeBytes(ByteArray(100)); setLastModified(1_000) }
        val mid = File(dir, "mid").apply { writeBytes(ByteArray(100)); setLastModified(2_000) }
        val new = File(dir, "new").apply { writeBytes(ByteArray(100)); setLastModified(3_000) }

        val freed = downloads().trim(maxBytes = 150)

        assertEquals(200, freed)
        assertFalse(old.exists())
        assertFalse(mid.exists())
        assertTrue(new.exists())
    }

    @Test
    fun `ids that are not uuids are rejected before any request`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { downloads().fetch(api, "../secrets", FileVariant.ORIGINAL) }
        }
        assertTrue(transport.requests.isEmpty())
    }
}
