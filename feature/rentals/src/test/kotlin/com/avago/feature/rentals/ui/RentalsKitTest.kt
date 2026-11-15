package com.avago.feature.rentals.ui

import java.io.File
import com.avago.core.data.db.entity.PhotoEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RentalsKitTest {
    private fun photo(
        id: String,
        entityId: String = "a1",
        type: String = "asset",
        sort: Long = 0,
        primary: Boolean = false,
        local: String? = null,
        url: String? = "https://x/$id.jpg",
        deleted: Long? = null,
    ) = PhotoEntity(
        photoId = id, entityId = entityId, entityType = type, accountId = "acc", storageKey = null, downloadUrl = url,
        sortOrder = sort, isPrimary = primary, createdAt = 0, updatedAt = 0, deletedAt = deleted, serverVersion = 0, localPath = local,
    )

    @Test
    fun `primary photo wins over sort order`() {
        val map = assetThumbnails(listOf(photo("p1", sort = 0), photo("p2", sort = 5, primary = true)))
        assertEquals("https://x/p2.jpg", map["a1"])
    }

    @Test
    fun `lowest sort order when none is primary, ignoring deleted and other entity types`() {
        val map = assetThumbnails(
            listOf(
                photo("p0", sort = 0, deleted = 1),
                photo("p3", sort = 3),
                photo("p1", sort = 1),
                photo("log", entityId = "a2", type = "log"),
            ),
        )
        assertEquals("https://x/p1.jpg", map["a1"])
        assertNull(map["a2"])
    }

    @Test
    fun `existing local file preferred, missing file falls back to url`() {
        val local = photo("p1", local = "/data/p1.jpg")
        assertEquals(File("/data/p1.jpg"), photoModel(local) { true })
        assertEquals("https://x/p1.jpg", photoModel(local) { false })
        assertNull(photoModel(photo("p2", url = null, local = "/gone.jpg")) { false })
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
