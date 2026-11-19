package com.avago.feature.rentals.ui

import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.StoredFile
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RentalsKitTest {
    private fun image(
        id: String,
        assetId: String? = "a1",
        sort: Int = 0,
        local: String? = "/data/$id.jpg",
        mime: String = "image/jpeg",
    ) = Attachment(
        id = id,
        file = StoredFile("f$id", "$id.jpg", mime, 10, "uploaded", local?.let(::File)),
        fileId = "f$id", documentId = null, assetId = assetId, activityId = null, workOrderId = null,
        sortOrder = sort, createdAt = 0,
    )

    @Test
    fun `lowest sort order with a local copy wins per asset`() {
        val map = assetThumbnails(
            listOf(image("p3", sort = 3), image("p1", sort = 1), image("p0", sort = 0, local = null)),
        ) { true }
        assertEquals(File("/data/p1.jpg"), map["a1"])
    }

    @Test
    fun `non images, unlinked and missing files are ignored`() {
        val map = assetThumbnails(
            listOf(
                image("pdf", mime = "application/pdf"),
                image("log", assetId = null),
                image("gone", assetId = "a2"),
            ),
        ) { it.path.endsWith("pdf.jpg") || it.path.endsWith("log.jpg") }
        assertNull(map["a1"])
        assertNull(map["a2"])
    }

    @Test
    fun `order primary follows the lifecycle, never extend or cancel`() {
        assertEquals("checkin", primaryOrderAction(listOf("extend", "swap", "off-rent", "checkin")))
        assertEquals("checkout", primaryOrderAction(listOf("checkout", "no-show", "cancel")))
        assertEquals("reserve", primaryOrderAction(listOf("hold", "reserve", "cancel")))
        assertEquals("close", primaryOrderAction(listOf("close")))
        assertNull(primaryOrderAction(listOf("cancel")))
    }
}
