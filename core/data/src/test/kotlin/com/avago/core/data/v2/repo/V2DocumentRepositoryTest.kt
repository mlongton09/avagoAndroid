package com.avago.core.data.v2.repo

import com.avago.core.data.domain.DocumentDraft
import com.avago.core.data.domain.DocumentEdit
import com.avago.core.data.domain.DocumentKind
import com.avago.core.data.domain.DocumentOwner
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2StagedFile
import com.avago.core.data.v2.V2Stores
import com.avago.core.data.v2.commands.CoreCommands
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class V2DocumentRepositoryTest {
    private val business = V2Fixtures.business()
    private val active = V2ActiveStores().apply { publish(V2Stores(V2Fixtures.identity, business, V2Fixtures.chat())) }
    private val commands = V2CommandService(active) {}
    private val asset = "00000000-0000-4000-8000-0000000000a1"
    private val doc = "00000000-0000-4000-8000-0000000000d1"

    @TempDir
    lateinit var dir: File

    private fun staged(id: String) = V2StagedFile(id, File(dir, id).apply { writeText(id) }, "0".repeat(64), 2, "application/pdf", "$id.pdf")

    private suspend fun documents(owner: DocumentOwner? = null) =
        business.read { V2DocumentRepository.documents(it, owner, null) { null } }

    private suspend fun operations() = business.read { conn ->
        conn.query("SELECT operation FROM pending_commands ORDER BY causal_order").map { it["operation"] }
    }

    @Test
    fun `a new document is listed on its asset with its first revision until the server answers`() = runTest {
        val draft = DocumentDraft(
            owner = DocumentOwner.Asset(asset), kind = DocumentKind.WARRANTY, file = LocalFileRef("content://x", "application/pdf"),
            title = " Pump warranty ", total = BigDecimal("120.50"), currency = "eur", warrantyEndsOn = LocalDate.of(2027, 5, 1),
        )
        commands.submit(CoreCommands.createDocument(doc, "00000000-0000-4000-8000-0000000000e1", "00000000-0000-4000-8000-0000000000f1", draft, staged("00000000-0000-4000-8000-0000000000c1"), 10, null))

        val listed = documents(DocumentOwner.Asset(asset)).single()
        assertEquals("Pump warranty", listed.title)
        assertEquals(DocumentKind.WARRANTY, listed.kind)
        assertEquals(0, BigDecimal("120.50").compareTo(listed.total))
        assertEquals("EUR", listed.currency)
        assertEquals(1, listed.revisionNo)
        assertEquals("00000000-0000-4000-8000-0000000000c1", listed.file?.id)
        assertEquals(asset, listed.assetId)
        assertTrue(documents(DocumentOwner.WorkOrder("00000000-0000-4000-8000-0000000000b1")).isEmpty())
        val revision = business.read { V2DocumentRepository.revisions(it, doc) { null } }.single()
        assertEquals(1, revision.revisionNo)
    }

    @Test
    fun `edits, a replaced file and deletion show through the overlay`() = runTest {
        val draft = DocumentDraft(DocumentOwner.Asset(asset), DocumentKind.RECEIPT, LocalFileRef("content://x", "image/jpeg"), title = "Receipt")
        commands.submit(CoreCommands.createDocument(doc, "00000000-0000-4000-8000-0000000000e1", "00000000-0000-4000-8000-0000000000f1", draft, staged("00000000-0000-4000-8000-0000000000c1"), 10, null))
        val stored = documents().single()

        val edit = DocumentEdit(DocumentKind.INVOICE, null, LocalDate.of(2026, 1, 2), "ACME", null, null, null, "paid")
        commands.submit(CoreCommands.updateDocument(stored, edit, 20)!!)
        val edited = documents().single()
        assertEquals(DocumentKind.INVOICE, edited.kind)
        assertNull(edited.title)
        assertEquals("ACME", edited.issuerName)
        assertEquals(LocalDate.of(2026, 1, 2), edited.issuedOn)

        commands.submit(CoreCommands.replaceDocumentFile(edited, "00000000-0000-4000-8000-0000000000f2", staged("00000000-0000-4000-8000-0000000000c2"), 30, null))
        val replaced = documents().single()
        assertEquals(2, replaced.revisionNo)
        assertEquals("00000000-0000-4000-8000-0000000000c2", replaced.file?.id)
        assertEquals(listOf(2, 1), business.read { V2DocumentRepository.revisions(it, doc) { null } }.map { it.revisionNo })

        commands.submit(CoreCommands.deleteDocument(replaced))
        assertTrue(documents().isEmpty())
        assertTrue(business.read { conn -> V2MaintRows.attachments(conn, "asset_id = ?", listOf(asset), { it["asset_id"] == asset }) { null } }.isEmpty())
        assertEquals(
            listOf("core.documents.create", "core.documents.update", "core.documents.update", "core.documents.delete"),
            operations(),
        )
    }
}
