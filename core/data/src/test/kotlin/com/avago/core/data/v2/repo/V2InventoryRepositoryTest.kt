package com.avago.core.data.v2.repo

import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.v2.ScriptedServer
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2Api
import com.avago.core.data.v2.V2ChangeQueueRepository
import com.avago.core.data.v2.V2CommandOutbox
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Stores
import com.avago.core.data.v2.V2Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Reads and writes through the v2 store: optimistic apply on submit, rollback on rejection. */
class V2InventoryRepositoryTest {
    private val business = V2Fixtures.business()
    private val active = V2ActiveStores().apply { publish(V2Stores(V2Fixtures.identity, business, V2Fixtures.chat())) }
    private var flushes = 0
    private val commands = V2CommandService(active) { flushes++ }
    private val access = V2StoreAccess(active)
    private val repo = V2InventoryRepository(access, commands)
    private val changes = V2ChangeQueueRepository(access)
    private val server = ScriptedServer()
    private val outbox = V2CommandOutbox(
        business, V2Api(server.transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE), V2Fixtures.identity,
    )

    private val site = V2Uuid.v7()
    private val product = V2Uuid.v7()
    private val position = V2Uuid.v7()

    private suspend fun seed() = business.writeWithoutForeignKeys { tx ->
        tx.execute(
            "INSERT INTO products (id, created_at, updated_at, version, sku, name, standard_unit_cost, standard_cost_currency) VALUES (?, 1, 1, 1, 'F-1', 'Filter', '2.50', 'USD')",
            listOf(product),
        )
        tx.execute(
            "INSERT INTO stock_positions (id, created_at, updated_at, version, product_id, location_id, on_hand_qty_micros) " +
                "VALUES (?, 1, 1, 1, ?, ?, 5000000)",
            listOf(position, product, site),
        )
    }

    private suspend fun onHand() = repo.observeProductStock().first().single { it.product.id == product }.onHand

    private fun issue(qty: String) = StockDocumentDraft(
        MovementKind.ISSUE, site, listOf(StockLineDraft(product, quantity = BigDecimal(qty))),
    )

    @Test
    fun `reads the synced base rows`() = runTest {
        seed()
        val stock = repo.observeProductStock().first().single()
        assertEquals("Filter", stock.product.name)
        assertEquals(0, BigDecimal("2.5").compareTo(stock.product.standardUnitCost))
        assertEquals(0, BigDecimal(5).compareTo(stock.onHand))
        assertEquals(listOf(position), repo.observePositions(productId = product).first().map { it.id })
    }

    @Test
    fun `a posted document applies at once and rolls back when the server rejects it`() = runTest {
        seed()
        val commandId = repo.postStockDocument(issue("2"))

        assertEquals(1, flushes)
        assertEquals(0, BigDecimal(3).compareTo(onHand()))
        assertEquals(1, changes.observePendingCount().first())
        val movement = repo.observeMovements(setOf(MovementKind.ISSUE)).first().single()
        assertEquals(1, movement.lines.size)

        server.commands += {
            V2Fixtures.json(
                200,
                buildJsonObject {
                    put("command_id", commandId)
                    put("state", "rejected")
                    put("rejection_code", "insufficient_stock")
                    put("message", "only 1 on hand")
                },
            )
        }
        assertEquals(listOf(commandId), outbox.flush().rejected)

        assertEquals(0, BigDecimal(5).compareTo(onHand()))
        assertTrue(repo.observeMovements(setOf(MovementKind.ISSUE)).first().isEmpty())
        val failure = changes.observeFailures().first().single()
        assertEquals("insufficient_stock", failure.code)
        assertEquals("stock.documents.create_and_post", failure.operation)

        changes.dismiss(commandId)
        assertTrue(changes.observeFailures().first().isEmpty())
        assertEquals(0, changes.observePendingCount().first())
    }

    @Test
    fun `a cycle count line is queued as a catalog command`() = runTest {
        seed()
        val countId = repo.planCycleCount(
            com.avago.core.data.domain.CycleCountDraft(locationId = site, scope = com.avago.core.data.domain.CountScope.FULL),
        )
        val lineId = V2Uuid.v7()
        repo.recordCount(lineId, BigDecimal("4"), "shelf 2")

        val queued = business.read { conn ->
            conn.query("SELECT operation, entity_key FROM pending_commands ORDER BY causal_order").map {
                it["operation"] to it["entity_key"]
            }
        }
        assertEquals(
            listOf("stock.cycle_counts.create" to countId, "stock.cycle_counts.count_line" to lineId),
            queued,
        )
    }
}
