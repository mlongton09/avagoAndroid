package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentDraft
import com.avago.core.data.domain.DocumentEdit
import com.avago.core.data.domain.DocumentOwner
import com.avago.core.data.v2.V2Money
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2StagedFile

/** Builders for the `core.*` catalog commands (documents). */
object CoreCommands {

    /**
     * `core.documents.create` for an uploaded [file]. The server writes the document, its first
     * revision and the attachment link; the overlay shows the document, its first revision (keyed
     * [revisionId] locally), the pending file and the link (keyed [linkId] locally) until the
     * server's rows arrive.
     */
    fun createDocument(
        id: String,
        linkId: String,
        revisionId: String,
        draft: DocumentDraft,
        file: V2StagedFile,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val owner = draft.owner
        val assetId = (owner as? DocumentOwner.Asset)?.assetId
        val activityId = (owner as? DocumentOwner.Activity)?.activityId
        val workOrderId = (owner as? DocumentOwner.WorkOrder)?.workOrderId
        val currency = draft.currency.clean()?.uppercase()
        require(draft.total == null || currency != null) { "a document total needs a currency" }
        val payload = CoreDocumentsCreate(
            id = id,
            kind = draft.kind.wire,
            title = draft.title.clean(),
            assetId = assetId,
            activityId = activityId,
            workOrderId = workOrderId,
            fileObjectId = file.fileId,
            issuedOn = draft.issuedOn,
            issuerName = draft.issuerName.clean(),
            totalMinor = draft.total?.let { V2Money.toMinor(it, currency) },
            currency = currency,
            warrantyEndsOn = draft.warrantyEndsOn,
            notes = draft.notes.clean(),
        )
        val ops = listOf(
            MaintCommands.pendingFileRow(file, nowMillis, userId),
            V2RowOp.upsert(
                DOCUMENTS, id,
                mapOf(
                    "kind" to payload.kind, "status" to "issued", "title" to payload.title,
                    "current_revision_id" to revisionId, "issued_on" to payload.issuedOn?.toString(),
                    "issuer_name" to payload.issuerName, "total_minor" to payload.totalMinor, "currency" to currency,
                    "warranty_ends_on" to payload.warrantyEndsOn?.toString(), "notes" to payload.notes,
                ) + created(nowMillis, userId),
            ),
            revisionRow(revisionId, id, 1, file.fileId, nowMillis, userId),
            V2RowOp.upsert(
                "attachments", linkId,
                mapOf(
                    "file_object_id" to file.fileId, "document_id" to id, "asset_id" to assetId,
                    "activity_id" to activityId, "work_order_id" to workOrderId,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops), uploads = listOf(file.toUpload()))
    }

    /** `core.documents.update` with the changed metadata; null when nothing changed. */
    fun updateDocument(stored: Document, edit: DocumentEdit, nowMillis: Long): V2NewCommand? {
        val currency = edit.currency.clean()?.uppercase()
        require(edit.total == null || currency != null) { "a document total needs a currency" }
        val d = V2UpdateDiff(CoreDocumentsUpdate.Clear.entries)
        val kind = d.field("kind", stored.kind.wire, edit.kind.wire)
        val title = d.text("title", stored.title, edit.title)
        val issuedOn = d.field("issued_on", stored.issuedOn, edit.issuedOn) { it.toString() }
        val issuer = d.text("issuer_name", stored.issuerName, edit.issuerName)
        val storedCurrency = stored.currency.takeIf { stored.total != null }
        val newCurrency = currency.takeIf { edit.total != null }
        val totalMinor = d.field(
            "total_minor",
            stored.total?.let { V2Money.toMinor(it, storedCurrency) },
            edit.total?.let { V2Money.toMinor(it, newCurrency) },
        )
        val currencySet = d.field("currency", storedCurrency, newCurrency)
        val warranty = d.field("warranty_ends_on", stored.warrantyEndsOn, edit.warrantyEndsOn) { it.toString() }
        val notes = d.text("notes", stored.notes, edit.notes)
        d.check()
        if (d.isEmpty) return null
        return CoreDocumentsUpdate(
            id = stored.id, kind = kind, title = title, issuedOn = issuedOn, issuerName = issuer, totalMinor = totalMinor,
            currency = currencySet, warrantyEndsOn = warranty, notes = notes, clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert(DOCUMENTS, stored.id, d.overlay + updated(nowMillis))),
        )
    }

    /**
     * `core.documents.update` with `file_object_id`: the uploaded [file] becomes the document's new
     * current revision (keyed [revisionId] in the overlay until the server's revision arrives).
     */
    fun replaceDocumentFile(
        stored: Document,
        revisionId: String,
        file: V2StagedFile,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand =
        CoreDocumentsUpdate(id = stored.id, fileObjectId = file.fileId).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(
                listOf(
                    MaintCommands.pendingFileRow(file, nowMillis, userId),
                    revisionRow(revisionId, stored.id, (stored.revisionNo ?: 0) + 1, file.fileId, nowMillis, userId),
                    V2RowOp.upsert(DOCUMENTS, stored.id, mapOf("current_revision_id" to revisionId) + updated(nowMillis)),
                ),
            ),
            uploads = listOf(file.toUpload()),
        )

    /** `core.documents.delete`; the overlay hides the document and its attachment links. */
    fun deleteDocument(stored: Document): V2NewCommand =
        CoreDocumentsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.delete(DOCUMENTS, stored.id)) + stored.linkIds.map { V2RowOp.delete("attachments", it) }),
        )

    private fun revisionRow(id: String, documentId: String, revisionNo: Int, fileId: String, nowMillis: Long, userId: String?) =
        V2RowOp.upsert(
            DOCUMENT_REVISIONS, id,
            mapOf("document_id" to documentId, "revision_no" to revisionNo.toLong(), "file_object_id" to fileId, "ocr_status" to "none") +
                created(nowMillis, userId),
        )

    const val DOCUMENTS = "documents"
    const val DOCUMENT_REVISIONS = "document_revisions"
}
