package com.avago.core.data.v2.repo

import androidx.core.net.toUri
import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentDraft
import com.avago.core.data.domain.DocumentEdit
import com.avago.core.data.domain.DocumentKind
import com.avago.core.data.domain.DocumentOwner
import com.avago.core.data.domain.DocumentRepository
import com.avago.core.data.domain.DocumentRevision
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.StoredFile
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2Money
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.commands.CoreCommands
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2DocumentRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : DocumentRepository {

    override fun observeDocuments(owner: DocumentOwner?): Flow<List<Document>> =
        store.observe(TABLES, emptyList()) { conn -> documents(conn, owner, null, stager::localFile) }

    override fun observeDocument(id: String): Flow<Document?> =
        store.observe(TABLES, null) { conn -> documents(conn, null, id, stager::localFile).firstOrNull() }

    override fun observeRevisions(documentId: String): Flow<List<DocumentRevision>> =
        store.observe(setOf(CoreCommands.DOCUMENT_REVISIONS, "files"), emptyList()) { conn ->
            revisions(conn, documentId, stager::localFile)
        }

    override suspend fun create(draft: DocumentDraft): String {
        val staged = stager.stage(draft.file.uri.toUri(), draft.file.fallbackMediaType)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(
            CoreCommands.createDocument(id, V2Uuid.v7(now), V2Uuid.v7(now), draft, staged, now, commands.currentUserId()),
        )
        return id
    }

    override suspend fun update(stored: Document, edit: DocumentEdit) {
        CoreCommands.updateDocument(stored, edit, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun replaceFile(stored: Document, file: LocalFileRef) {
        val staged = stager.stage(file.uri.toUri(), file.fallbackMediaType)
        val now = System.currentTimeMillis()
        commands.submit(CoreCommands.replaceDocumentFile(stored, V2Uuid.v7(now), staged, now, commands.currentUserId()))
    }

    override suspend fun delete(stored: Document) {
        commands.submit(CoreCommands.deleteDocument(stored))
    }

    companion object {
        val TABLES = setOf(CoreCommands.DOCUMENTS, CoreCommands.DOCUMENT_REVISIONS, "attachments", "files")

        /** Rental paperwork hangs off an order, invoice, ledger entry, hand-over, credit application or signature request. */
        private val RENTAL_LINKS = listOf(
            "order_id", "invoice_id", "ledger_entry_id", "handover_id", "credit_application_id", "signature_request_id",
        )
        private val MAINTENANCE_WHERE = RENTAL_LINKS.joinToString(" AND ") { "$it IS NULL" }

        /** Maintenance documents of [owner] (all when null), or only [id]; newest first. */
        fun documents(conn: V2Connection, owner: DocumentOwner?, id: String?, localFile: (String) -> File?): List<Document> {
            val links = V2OverlayMerge.query(conn, "attachments", "document_id IS NOT NULL") { it["document_id"] != null }
                .groupBy { it.str("document_id") }
            val ownerIds = owner?.let { o ->
                val (column, ownerId) = when (o) {
                    is DocumentOwner.Asset -> "asset_id" to o.assetId
                    is DocumentOwner.Activity -> "activity_id" to o.activityId
                    is DocumentOwner.WorkOrder -> "work_order_id" to o.workOrderId
                }
                links.filterValues { rows -> rows.any { it.strOrNull(column) == ownerId } }.keys
            }
            val docs = V2OverlayMerge.query(
                conn, CoreCommands.DOCUMENTS,
                where = listOfNotNull(MAINTENANCE_WHERE, id?.let { "id = ?" }).joinToString(" AND "),
                args = listOfNotNull(id),
            ) { r -> RENTAL_LINKS.all { r[it] == null } && (id == null || r["id"] == id) }
                .filter { ownerIds == null || it.str("id") in ownerIds }
            if (docs.isEmpty()) return emptyList()
            val docIds = docs.map { it.str("id") }.toSet()
            val revisions = V2OverlayMerge.query(conn, CoreCommands.DOCUMENT_REVISIONS) { it["document_id"] in docIds }
                .filter { it.str("document_id") in docIds }
            val fileIds = (revisions.mapNotNull { it.strOrNull("file_object_id") } +
                docIds.flatMap { d -> links[d].orEmpty().mapNotNull { it.strOrNull("file_object_id") } }).toSet()
            val files = files(conn, fileIds, localFile)
            val revisionsByDoc = revisions.groupBy { it.str("document_id") }
            return docs.map { r ->
                val docId = r.str("id")
                val docLinks = links[docId].orEmpty()
                val current = revisionsByDoc[docId].orEmpty().let { revs ->
                    revs.firstOrNull { it.str("id") == r.strOrNull("current_revision_id") }
                        ?: revs.maxByOrNull { it.intOrNull("revision_no") ?: 0 }
                }
                val fileId = current?.strOrNull("file_object_id") ?: docLinks.firstNotNullOfOrNull { it.strOrNull("file_object_id") }
                document(r, current, fileId?.let(files::get), docLinks)
            }.sortedWith(compareByDescending<Document> { it.issuedOn ?: LocalDate.MIN }.thenByDescending { it.createdAt }.thenBy { it.id })
        }

        fun revisions(conn: V2Connection, documentId: String, localFile: (String) -> File?): List<DocumentRevision> {
            val rows = V2OverlayMerge.query(conn, CoreCommands.DOCUMENT_REVISIONS, "document_id = ?", listOf(documentId)) {
                it["document_id"] == documentId
            }
            val files = files(conn, rows.mapNotNull { it.strOrNull("file_object_id") }.toSet(), localFile)
            return rows.map { r ->
                DocumentRevision(
                    id = r.str("id"),
                    documentId = documentId,
                    revisionNo = r.intOrNull("revision_no") ?: 1,
                    file = r.strOrNull("file_object_id")?.let(files::get),
                    createdAt = r.longOrNull("created_at") ?: 0L,
                    createdBy = r.strOrNull("created_by"),
                )
            }.sortedByDescending { it.revisionNo }
        }

        private fun files(conn: V2Connection, ids: Set<String>, localFile: (String) -> File?): Map<String, StoredFile> =
            if (ids.isEmpty()) {
                emptyMap()
            } else {
                V2OverlayMerge.query(conn, "files") { it["id"] in ids }
                    .filter { it.str("id") in ids }
                    .associate { it.str("id") to V2MaintRows.file(it, localFile) }
            }

        private fun document(r: V2Row, revision: V2Row?, file: StoredFile?, links: List<V2Row>): Document {
            val currency = r.strOrNull("currency")
            return Document(
                id = r.str("id"),
                version = r.longOrNull("version") ?: 0L,
                kind = DocumentKind.fromWire(r.strOrNull("kind")),
                status = r.strOrNull("status") ?: "issued",
                title = r.strOrNull("title"),
                issuedOn = r.strOrNull("issued_on")?.let(LocalDate::parse),
                issuerName = r.strOrNull("issuer_name"),
                total = r.longOrNull("total_minor")?.let { V2Money.toMajor(it, currency) },
                currency = currency,
                warrantyEndsOn = r.strOrNull("warranty_ends_on")?.let(LocalDate::parse),
                notes = r.strOrNull("notes"),
                file = file,
                revisionNo = revision?.intOrNull("revision_no"),
                assetId = links.firstNotNullOfOrNull { it.strOrNull("asset_id") },
                activityId = links.firstNotNullOfOrNull { it.strOrNull("activity_id") },
                workOrderId = links.firstNotNullOfOrNull { it.strOrNull("work_order_id") },
                linkIds = links.map { it.str("id") },
                createdAt = r.longOrNull("created_at") ?: 0L,
                createdBy = r.strOrNull("created_by"),
                updatedAt = r.longOrNull("updated_at") ?: 0L,
                updatedBy = r.strOrNull("updated_by"),
            )
        }
    }
}
