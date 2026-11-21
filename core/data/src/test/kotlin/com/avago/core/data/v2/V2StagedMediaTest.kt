package com.avago.core.data.v2

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Plan 4.3: a cache budget never evicts unsent photos, signatures or documents. */
class V2StagedMediaTest {

    @TempDir
    lateinit var root: File

    private fun staged(name: String, bytes: Int, age: Long): File =
        File(root, name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(1_000_000L + age)
        }

    @Test
    fun `a zero budget keeps every unsent copy`() {
        val unsent = listOf(staged("photo", 100, 1), staged("signature", 50, 2))
        val media = V2StagedMedia(root)

        assertEquals(0L, media.sweep(0))
        unsent.forEach { assertTrue(it.isFile, "${it.name} must survive") }
    }

    @Test
    fun `uploaded copies are evicted oldest first until the budget holds`() {
        val old = staged("old", 100, 1)
        val newer = staged("newer", 100, 2)
        val unsent = staged("unsent", 100, 0)
        val media = V2StagedMedia(root)
        media.markUploaded(old.path)
        media.markUploaded(newer.path)

        assertEquals(100L, media.sweep(200))
        assertFalse(old.exists())
        assertFalse(media.isUploaded("old"))
        assertTrue(newer.isFile)
        assertTrue(unsent.isFile)

        assertEquals(100L, media.sweep(0))
        assertFalse(newer.exists())
        assertTrue(unsent.isFile, "the unsent copy outlives a zero budget")
    }

    @Test
    fun `only copies inside the staging directory can be marked`() {
        val outside = File(root.parentFile, "outside-${root.name}").apply { writeText("x") }
        try {
            val media = V2StagedMedia(root)
            media.markUploaded(outside.path)
            media.markUploaded(File(root, "missing").path)
            assertFalse(media.isUploaded(outside.name))
            assertFalse(media.isUploaded("missing"))
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `the uploader marks a copy only once the server holds the bytes`() {
        val file = staged("job", 10, 0)
        val media = V2StagedMedia(root)
        val transport = MockTransport()
        transport.route = { r ->
            when {
                r.path.endsWith("/uploads") -> V2Response(200, """{"upload_url":"https://blob/put"}""")
                r.path.endsWith("/finalize") -> V2Response(200, """{"state":"finalized"}""")
                else -> error("unexpected ${r.method} ${r.path}")
            }
        }
        val api = V2Api(transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE)
        val failing = V2ApiUploader(api, { _, _, _ -> V2BlobPutResult(503) }, media::markUploaded)

        val attempt = runCatching {
            runBlocking { failing.process("job", file.path, "a".repeat(64), 10, "image/jpeg") }
        }
        assertTrue(attempt.isFailure)
        assertFalse(media.isUploaded("job"))
        assertEquals(0L, media.sweep(0))

        val ok = V2ApiUploader(api, { _, _, _ -> V2BlobPutResult(200) }, media::markUploaded)
        runBlocking { ok.process("job", file.path, "a".repeat(64), 10, "image/jpeg") }
        assertTrue(media.isUploaded("job"))
        assertEquals(0L, media.sweep(10))
        assertEquals(10L, media.sweep(0))
    }
}
