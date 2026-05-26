package com.avago.core.sync

import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.db.entity.WoCommentEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for WoCommentEntity, DocEntity, and PhotoEntity data classes.
 *
 * Verifies required/optional field presence and Kotlin data-class equality semantics.
 */
class EntityFieldTest {

    // ─── WoCommentEntity ──────────────────────────────────────────────────────

    private fun sampleComment(
        commentId: String = "cmt-001",
        woId: String = "wo-001",
        authorId: String = "user-001",
        body: String = "Looks good",
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_000L,
        deletedAt: Long? = null,
        serverVersion: Long = 1L,
        seq: Long? = null,
    ) = WoCommentEntity(
        commentId = commentId,
        woId = woId,
        authorId = authorId,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        serverVersion = serverVersion,
        seq = seq,
    )

    @Test
    fun `WoCommentEntity required fields are present`() {
        val c = sampleComment()
        assertEquals("cmt-001", c.commentId)
        assertEquals("wo-001", c.woId)
        assertEquals("user-001", c.authorId)
        assertEquals("Looks good", c.body)
        assertEquals(1L, c.serverVersion)
    }

    @Test
    fun `WoCommentEntity deletedAt is nullable`() {
        val c = sampleComment(deletedAt = null)
        assertNull(c.deletedAt)
    }

    @Test
    fun `WoCommentEntity deletedAt can be set`() {
        val ts = 1_750_000_000_000L
        val c = sampleComment(deletedAt = ts)
        assertEquals(ts, c.deletedAt)
    }

    @Test
    fun `WoCommentEntity seq is nullable`() {
        val c = sampleComment(seq = null)
        assertNull(c.seq)
    }

    @Test
    fun `WoCommentEntity equality two identical comments are equal`() {
        val c1 = sampleComment()
        val c2 = sampleComment()
        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
    }

    @Test
    fun `WoCommentEntity inequality different body`() {
        val c1 = sampleComment(body = "First")
        val c2 = sampleComment(body = "Second")
        assertNotEquals(c1, c2)
    }

    @Test
    fun `WoCommentEntity inequality different commentId`() {
        val c1 = sampleComment(commentId = "cmt-001")
        val c2 = sampleComment(commentId = "cmt-002")
        assertNotEquals(c1, c2)
    }

    @Test
    fun `WoCommentEntity copy with new body`() {
        val original = sampleComment(body = "Original")
        val updated = original.copy(body = "Updated")
        assertEquals("Updated", updated.body)
        assertEquals(original.commentId, updated.commentId)
    }

    // ─── DocEntity ────────────────────────────────────────────────────────────

    private fun sampleDoc(
        docId: String = "doc-001",
        accountId: String = "acct-001",
        name: String = "Invoice.pdf",
        docType: String? = "invoice",
        mimeType: String? = "application/pdf",
        storageKey: String? = "uploads/invoice.pdf",
        downloadUrl: String? = "https://cdn.example.com/invoice.pdf",
    ) = DocEntity(
        docId = docId,
        assetId = null,
        entityId = null,
        entityType = null,
        accountId = accountId,
        name = name,
        docType = docType,
        mimeType = mimeType,
        storageKey = storageKey,
        downloadUrl = downloadUrl,
        fileHash = null,
        fileSize = null,
        ocrRawText = null,
        ocrExtractedJson = null,
        vendor = null,
        total = null,
        currency = null,
        purchaseDate = null,
        warrantyEndDate = null,
        uploadedBy = null,
        uploadedAt = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )

    @Test
    fun `DocEntity required fields`() {
        val doc = sampleDoc()
        assertEquals("doc-001", doc.docId)
        assertEquals("acct-001", doc.accountId)
        assertEquals("Invoice.pdf", doc.name)
    }

    @Test
    fun `DocEntity mimeType application pdf`() {
        val doc = sampleDoc(mimeType = "application/pdf")
        assertEquals("application/pdf", doc.mimeType)
    }

    @Test
    fun `DocEntity mimeType image jpeg`() {
        val doc = sampleDoc(mimeType = "image/jpeg")
        assertEquals("image/jpeg", doc.mimeType)
    }

    @Test
    fun `DocEntity mimeType image png`() {
        val doc = sampleDoc(mimeType = "image/png")
        assertEquals("image/png", doc.mimeType)
    }

    @Test
    fun `DocEntity storageKey and downloadUrl present`() {
        val doc = sampleDoc(
            storageKey = "uploads/doc.pdf",
            downloadUrl = "https://cdn.example.com/doc.pdf",
        )
        assertEquals("uploads/doc.pdf", doc.storageKey)
        assertEquals("https://cdn.example.com/doc.pdf", doc.downloadUrl)
    }

    @Test
    fun `DocEntity optional fields nullable`() {
        val doc = sampleDoc().copy(
            assetId = null,
            entityId = null,
            entityType = null,
            docType = null,
            mimeType = null,
        )
        assertNull(doc.assetId)
        assertNull(doc.entityId)
        assertNull(doc.entityType)
        assertNull(doc.docType)
        assertNull(doc.mimeType)
    }

    @Test
    fun `DocEntity equality`() {
        val d1 = sampleDoc()
        val d2 = sampleDoc()
        assertEquals(d1, d2)
    }

    @Test
    fun `DocEntity inequality different docId`() {
        val d1 = sampleDoc(docId = "doc-001")
        val d2 = sampleDoc(docId = "doc-002")
        assertNotEquals(d1, d2)
    }

    @Test
    fun `DocEntity with entity link`() {
        val doc = sampleDoc().copy(entityId = "wo-abc", entityType = "work_order")
        assertEquals("wo-abc", doc.entityId)
        assertEquals("work_order", doc.entityType)
    }

    // ─── PhotoEntity ──────────────────────────────────────────────────────────

    private fun samplePhoto(
        photoId: String = "photo-001",
        entityId: String = "asset-001",
        entityType: String = "asset",
        accountId: String = "acct-001",
        isPrimary: Boolean = false,
        sortOrder: Long = 0L,
    ) = PhotoEntity(
        photoId = photoId,
        entityId = entityId,
        entityType = entityType,
        accountId = accountId,
        storageKey = "photos/photo.jpg",
        downloadUrl = "https://cdn.example.com/photo.jpg",
        sortOrder = sortOrder,
        isPrimary = isPrimary,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = null,
        serverVersion = 0L,
        localPath = null,
    )

    @Test
    fun `PhotoEntity required fields present`() {
        val p = samplePhoto()
        assertEquals("photo-001", p.photoId)
        assertEquals("asset-001", p.entityId)
        assertEquals("asset", p.entityType)
    }

    @Test
    fun `PhotoEntity isPrimary defaults to false`() {
        val p = samplePhoto(isPrimary = false)
        assertFalse(p.isPrimary)
    }

    @Test
    fun `PhotoEntity isPrimary can be true`() {
        val p = samplePhoto(isPrimary = true)
        assertTrue(p.isPrimary)
    }

    @Test
    fun `PhotoEntity sortOrder is stored`() {
        val p = samplePhoto(sortOrder = 3L)
        assertEquals(3L, p.sortOrder)
    }

    @Test
    fun `PhotoEntity deletedAt nullable`() {
        val p = samplePhoto().copy(deletedAt = null)
        assertNull(p.deletedAt)
    }

    @Test
    fun `PhotoEntity localPath is nullable by default`() {
        val p = samplePhoto()
        assertNull(p.localPath)
    }

    @Test
    fun `PhotoEntity equality`() {
        val p1 = samplePhoto()
        val p2 = samplePhoto()
        assertEquals(p1, p2)
    }

    @Test
    fun `PhotoEntity inequality different photoId`() {
        val p1 = samplePhoto(photoId = "photo-001")
        val p2 = samplePhoto(photoId = "photo-002")
        assertNotEquals(p1, p2)
    }

    @Test
    fun `PhotoEntity entityType work_order variant`() {
        val p = samplePhoto(entityId = "wo-999", entityType = "work_order")
        assertEquals("work_order", p.entityType)
        assertEquals("wo-999", p.entityId)
    }
}
