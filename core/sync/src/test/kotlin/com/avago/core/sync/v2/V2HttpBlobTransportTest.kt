package com.avago.core.sync.v2

import com.avago.core.data.v2.V2BlobPutResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class V2HttpBlobTransportTest {
    @Test
    fun `reads the code and message of a REST error body`() {
        assertEquals(
            V2BlobPutResult(415, "media_type_mismatch", "The bytes are not image/jpeg"),
            blobPutError(415, """{"error":"The bytes are not image/jpeg","code":"media_type_mismatch","message":"The bytes are not image/jpeg"}"""),
        )
        assertEquals(
            V2BlobPutResult(415, "unsupported_media_type", "Bad type"),
            blobPutError(415, """{"error":"Bad type","code":"unsupported_media_type"}"""),
        )
    }

    @Test
    fun `reads a bare error code and tolerates a body that is not JSON`() {
        assertEquals(V2BlobPutResult(415, "unsupported_media_type", null), blobPutError(415, """{"error":"unsupported_media_type"}"""))
        assertEquals(V2BlobPutResult(503), blobPutError(503, "<html>busy</html>"))
        assertEquals(V2BlobPutResult(500), blobPutError(500, null))
    }
}
