package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentEdit
import com.avago.core.data.domain.DocumentKind
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class CoreCommandsTest {
    private val stored = Document(
        id = "d1", version = 4, kind = DocumentKind.RECEIPT, status = "issued", title = "Tyres", issuedOn = LocalDate.of(2026, 3, 1),
        issuerName = "ACME", total = BigDecimal("12.50"), currency = "EUR", warrantyEndsOn = null, notes = null, file = null,
        revisionNo = 1, assetId = "a1", activityId = null, workOrderId = null, linkIds = listOf("l1"), createdAt = 1, createdBy = null, updatedAt = 1, updatedBy = null,
    )
    private val same = DocumentEdit(stored.kind, " Tyres ", stored.issuedOn, "ACME", BigDecimal("12.5"), "eur", null, "")

    @Test
    fun `unchanged metadata queues nothing`() {
        assertNull(CoreCommands.updateDocument(stored, same, 9))
    }

    @Test
    fun `a removed total clears the amount and its currency`() {
        val command = CoreCommands.updateDocument(stored, same.copy(total = null, title = "Winter tyres"), 9)!!
        val payload = command.payload.jsonObject
        assertEquals(4L, command.baseVersion)
        assertEquals("Winter tyres", payload["set"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(listOf("currency", "total_minor"), payload["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `a changed amount is sent in minor units and a total needs a currency`() {
        val payload = CoreCommands.updateDocument(stored, same.copy(total = BigDecimal("13")), 9)!!.payload.jsonObject
        assertEquals(1300L, payload["set"]!!.jsonObject["total_minor"]!!.jsonPrimitive.content.toLong())
        assertThrows(IllegalArgumentException::class.java) { CoreCommands.updateDocument(stored, same.copy(currency = " "), 9) }
    }
}
