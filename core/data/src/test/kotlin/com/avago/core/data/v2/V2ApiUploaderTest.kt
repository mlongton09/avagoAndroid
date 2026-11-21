package com.avago.core.data.v2

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class V2ApiUploaderTest {
    private val transport = MockTransport()
    private val api = V2Api(transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE)
    private val jobId = V2Uuid.v7()
    private val sha = "a".repeat(64)

    @TempDir
    lateinit var dir: File

    private fun file() = File(dir, "p.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)) }

    private fun routeRegister(answer: V2Response) {
        transport.route = { r ->
            when {
                r.path.endsWith("/uploads") -> answer
                r.path.endsWith("/finalize") -> V2Response(200, """{"state":"finalized"}""")
                else -> error("unexpected ${r.method} ${r.path}")
            }
        }
    }

    private fun process(uploader: V2ApiUploader, path: String) = kotlinx.coroutines.runBlocking {
        uploader.process(jobId, path, sha, 4, "image/jpeg")
    }

    @Test
    fun `registers with the media type, PUTs and finalizes`() = runTest {
        routeRegister(V2Response(200, """{"upload_url":"https://blob/put"}"""))
        val puts = mutableListOf<Pair<String, String>>()
        val uploader = V2ApiUploader(api, { url, _, type -> puts += url to type; V2BlobPutResult(200) })

        assertEquals("finalized", uploader.process(jobId, file().path, sha, 4, "image/jpeg"))
        val registered = Json.parseToJsonElement(transport.matching("POST", "/uploads").single().body!!).jsonObject
        assertEquals("image/jpeg", registered["media_type"]!!.jsonPrimitive.content)
        assertEquals(listOf("https://blob/put" to "image/jpeg"), puts)
        assertEquals(1, transport.matching("POST", "/finalize").size)
    }

    @Test
    fun `a type the route does not allow is refused at register`() {
        routeRegister(V2Fixtures.error(400, V2ApiUploader.UNSUPPORTED_MEDIA_TYPE))
        val uploader = V2ApiUploader(api, { _, _, _ -> error("no PUT expected") })

        val e = assertThrows(V2UploadRefusedException::class.java) { process(uploader, file().path) }
        assertEquals(V2ApiUploader.UNSUPPORTED_MEDIA_TYPE, e.code)
    }

    @Test
    fun `a 415 PUT is refused with the storage code and not finalized`() {
        routeRegister(V2Response(200, """{"upload_url":"https://blob/put"}"""))
        val uploader = V2ApiUploader(api, { _, _, _ -> V2BlobPutResult(415, V2ApiUploader.MEDIA_TYPE_MISMATCH, "bytes are not image/jpeg") })

        val e = assertThrows(V2UploadRefusedException::class.java) { process(uploader, file().path) }
        assertEquals(V2ApiUploader.MEDIA_TYPE_MISMATCH, e.code)
        assertEquals("bytes are not image/jpeg", e.message)
        assertTrue(transport.matching("POST", "/finalize").isEmpty())
    }

    @Test
    fun `any other PUT failure stays retryable`() {
        routeRegister(V2Response(200, """{"upload_url":"https://blob/put"}"""))
        val uploader = V2ApiUploader(api, { _, _, _ -> V2BlobPutResult(503) })

        assertThrows(IllegalStateException::class.java) { process(uploader, file().path) }
    }
}
