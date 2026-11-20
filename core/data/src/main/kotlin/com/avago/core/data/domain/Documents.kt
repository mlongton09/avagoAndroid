package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/** Document kind (`documents.kind`; the legacy document types). */
enum class DocumentKind(val wire: String) {
    RECEIPT("receipt"),
    INVOICE("invoice"),
    WARRANTY("warranty"),
    HOWTO("howto"),
    OTHER("other"),
    ;

    companion object {
        fun fromWire(value: String?): DocumentKind = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

/** What a new document hangs off: the catalog requires exactly one asset or work order. */
sealed interface DocumentOwner {
    data class Asset(val assetId: String) : DocumentOwner
    data class WorkOrder(val workOrderId: String) : DocumentOwner
}

/** A new document: its scanned or imported file plus the metadata printed on it. */
data class DocumentDraft(
    val owner: DocumentOwner,
    val kind: DocumentKind,
    val file: LocalFileRef,
    val title: String? = null,
    val issuedOn: LocalDate? = null,
    val issuerName: String? = null,
    val total: BigDecimal? = null,
    val currency: String? = null,
    val warrantyEndsOn: LocalDate? = null,
    val notes: String? = null,
)

/**
 * A maintenance document (`documents` linked to an asset or work order through `attachments`).
 * Rental paperwork (contracts, invoices, receipts, signed agreements) lives on its order and is not
 * listed here.
 */
data class Document(
    val id: String,
    val version: Long,
    val kind: DocumentKind,
    val status: String,
    val title: String?,
    val issuedOn: LocalDate?,
    val issuerName: String?,
    val total: BigDecimal?,
    val currency: String?,
    val warrantyEndsOn: LocalDate?,
    val notes: String?,
    /** The current revision's file (the linked file before the first revision arrives). */
    val file: StoredFile?,
    val revisionNo: Int?,
    val assetId: String?,
    val workOrderId: String?,
    /** The `attachments` rows linking the document to its owner. */
    val linkIds: List<String>,
    val createdAt: Long,
    val createdBy: String?,
    val updatedAt: Long,
    val updatedBy: String?,
) {
    val displayTitle: String? get() = title ?: issuerName ?: file?.name
}

/** One uploaded version of a document's file (`document_revisions`), newest first in lists. */
data class DocumentRevision(
    val id: String,
    val documentId: String,
    val revisionNo: Int,
    val file: StoredFile?,
    val createdAt: Long,
    val createdBy: String?,
)

/** Edited metadata of an existing document; blank text and null values clear the field. */
data class DocumentEdit(
    val kind: DocumentKind,
    val title: String?,
    val issuedOn: LocalDate?,
    val issuerName: String?,
    val total: BigDecimal?,
    val currency: String?,
    val warrantyEndsOn: LocalDate?,
    val notes: String?,
)

/** Maintenance documents: `core.documents.*` writes over the synced `documents` projection. */
interface DocumentRepository {
    /** Documents of [owner], or every maintenance document when null; newest first. */
    fun observeDocuments(owner: DocumentOwner? = null): Flow<List<Document>>

    fun observeDocument(id: String): Flow<Document?>

    /** The document's file revisions, newest first. */
    fun observeRevisions(documentId: String): Flow<List<DocumentRevision>>

    /** Uploads the file (E12) and creates the document with its first revision; returns its id. */
    suspend fun create(draft: DocumentDraft): String

    /** `core.documents.update`; does nothing when [edit] changes nothing. */
    suspend fun update(stored: Document, edit: DocumentEdit)

    /** Uploads [file] (E12) as the document's new current revision. */
    suspend fun replaceFile(stored: Document, file: LocalFileRef)

    /** `core.documents.delete`: removes the document and its attachment links. */
    suspend fun delete(stored: Document)
}
