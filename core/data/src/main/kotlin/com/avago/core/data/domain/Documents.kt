package com.avago.core.data.domain

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
 * Document writes. Documents are read through their attachment links
 * ([AssetRepository.observeAttachments], [Attachment.documentId]); the `documents` table itself is
 * not projected to devices yet, so its metadata cannot be read back or edited here.
 */
interface DocumentRepository {
    /** Uploads the file (E12) and creates the document with its first revision; returns its id. */
    suspend fun create(draft: DocumentDraft): String
}
