package com.avago.core.data.v2.repo

import com.avago.core.data.domain.LedgerEntry
import com.avago.core.data.domain.PaymentDraft
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.v2.ScriptedServer
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2Api
import com.avago.core.data.v2.V2CommandOutbox
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2Stores
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.commands.RentalAccountCommands
import com.avago.core.data.v2.commands.RentalCommands
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** The rental money projections read through the overlay: optimistic rows, then rollback on rejection. */
class V2RentalAccountsRepositoryTest {
    private val business = V2Fixtures.business()
    private val active = V2ActiveStores().apply { publish(V2Stores(V2Fixtures.identity, business, V2Fixtures.chat())) }
    private val commands = V2CommandService(active) {}
    private val server = ScriptedServer()
    private val outbox = V2CommandOutbox(business, V2Api(server.transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE), V2Fixtures.identity)

    private val order = V2Uuid.v7()
    private val line = V2Uuid.v7()
    private val open = V2Uuid.v7()
    private val billed = V2Uuid.v7()

    private suspend fun seedCharges() = business.writeWithoutForeignKeys { tx ->
        tx.execute(
            "INSERT INTO rental_order_lines (id, created_at, updated_at, order_id, line_no, kind, qty_micros, tax_snapshot, invoiced_through) " +
                "VALUES (?, 1, 1, ?, 1, 'adjustment', 1000000, '{}', 5)",
            listOf(line, order),
        )
        for ((id, lineId) in listOf(open to null, billed to line)) {
            tx.execute(
                "INSERT INTO post_charges (id, created_at, updated_at, version, order_id, kind, amount_minor, currency, incurred_at, " +
                    "evidence_file_object_id, status, order_line_id) VALUES (?, 1, 1, 2, ?, 'toll', 900, 'EUR', ?, ?, 'pending', ?)",
                listOf(id, order, if (id == open) 2_000L else 1_000L, V2Uuid.v7(), lineId),
            )
        }
    }

    private suspend fun charges() = business.read { V2RentalAccountsRepository.postCharges(it, order) }

    private suspend fun ledger() = business.read { V2RentalAccountsRepository.ledger(it, order, null) }

    @Test
    fun `a charge on an invoiced line cannot be voided`() = runTest {
        seedCharges()
        val (newest, invoiced) = charges()
        assertEquals(open, newest.id)
        assertTrue(newest.voidable)
        assertTrue(newest.chargeable)
        assertEquals(billed, invoiced.id)
        assertTrue(invoiced.invoiced)
        assertFalse(invoiced.voidable)
    }

    @Test
    fun `a voided charge shows at once and returns when the server refuses`() = runTest {
        seedCharges()
        val stored = charges().first { it.id == open }
        val commandId = commands.submit(RentalAccountCommands.voidPostCharge(stored, "duplicate", 5, null))

        val voided = charges().first { it.id == open }
        assertEquals("voided", voided.status)
        assertEquals(RecordSyncState.PENDING, voided.sync)
        assertFalse(voided.chargeable)

        reject(commandId)
        assertEquals("pending", charges().first { it.id == open }.status)
    }

    @Test
    fun `a recorded payment is in the order ledger until the server refuses it`() = runTest {
        val draft = PaymentDraft(
            orderId = order, kind = "payment", method = "card_external", amountMinor = 5000, currency = "EUR", reference = "R1",
            cardBrand = "visa", cardLast4 = "4242", payerName = "Ada", isAuthorization = false, notes = null,
            receivedAt = Instant.ofEpochMilli(3_000), payerEmail = "ada@example.com",
        )
        val entryId = V2Uuid.v7()
        val commandId = commands.submit(RentalCommands.recordPayment(entryId, draft, null, 3_000, null))

        val entry = ledger().single()
        assertEquals(entryId, entry.id)
        assertEquals(LedgerEntry.STATE_SETTLED, entry.state)
        assertEquals("ada@example.com", entry.payerEmail)
        assertEquals("visa", entry.cardBrand)
        assertEquals("4242", entry.cardLast4)
        assertEquals(RecordSyncState.PENDING, entry.sync)
        assertFalse(entry.viaGateway)

        reject(commandId)
        assertTrue(ledger().isEmpty())
    }

    @Test
    fun `an order is billed through the day before the end of its latest cycle`() = runTest {
        business.writeWithoutForeignKeys { tx ->
            for ((start, end) in listOf("2026-01-01" to "2026-01-29", "2026-01-29" to "2026-02-26")) {
                tx.execute(
                    "INSERT INTO rental_billing_cycles (id, created_at, updated_at, order_id, cycle, period_start, period_end, status) " +
                        "VALUES (?, 1, 1, ?, 'every_28_days', ?, ?, 'invoiced')",
                    listOf(V2Uuid.v7(), order, start, end),
                )
            }
        }
        val through = business.read { V2RentalRepository.billedThrough(it) }
        assertEquals(mapOf(order to LocalDate.of(2026, 2, 25)), through)
    }

    private suspend fun reject(commandId: String) {
        server.commands += {
            V2Fixtures.json(
                200,
                buildJsonObject {
                    put("command_id", commandId)
                    put("state", "rejected")
                    put("rejection_code", "invalid_state")
                    put("message", "no")
                },
            )
        }
        assertEquals(listOf(commandId), outbox.flush().rejected)
    }
}
