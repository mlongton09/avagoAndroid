package com.avago.core.data.v2.commands

import com.avago.core.data.domain.DocumentDraft
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
     * revision and the attachment link; the overlay shows the pending file and the link (keyed
     * [linkId] locally) until the server's rows arrive.
     */
    fun createDocument(
        id: String,
        linkId: String,
        draft: DocumentDraft,
        file: V2StagedFile,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val (assetId, workOrderId) = when (val owner = draft.owner) {
            is DocumentOwner.Asset -> owner.assetId to null
            is DocumentOwner.WorkOrder -> null to owner.workOrderId
        }
        val currency = draft.currency.clean()?.uppercase()
        require(draft.total == null || currency != null) { "a document total needs a currency" }
        val payload = CoreDocumentsCreate(
            id = id,
            kind = draft.kind.wire,
            title = draft.title.clean(),
            assetId = assetId,
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
                "attachments", linkId,
                mapOf(
                    "file_object_id" to file.fileId, "document_id" to id, "asset_id" to assetId,
                    "work_order_id" to workOrderId,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops), uploads = listOf(file.toUpload()))
    }
}
