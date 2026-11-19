package com.avago.core.sync.v2

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.PhotoDao
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2Database
import com.avago.core.data.v2.V2Stores
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.SyncPayloadBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class V2LegacyBridgeTest {
    private val queue = mockk<SyncQueueDao>(relaxed = true)
    private val photos = mockk<PhotoDao>(relaxed = true).also { d -> coEvery { d.deletedUploaded(any()) } returns emptyList() }
    private val room = mockk<AvagoDatabase> {
        every { syncQueueDao() } returns queue
        every { photoDao() } returns photos
    }
    private val dbFactory = mockk<DatabaseFactory>().also { f -> coEvery { f.get("acct") } returns room }
    private val payloads = mockk<SyncPayloadBuilder>()
    private val client = mockk<AvagoServiceClient>()
    private val store = mockk<V2Database>()
    private val stores = mockk<V2Stores> { every { business } returns store }
    private val active = mockk<V2ActiveStores> { every { forAccount("acct") } returns stores }
    private val bridge = V2LegacyBridge(dbFactory, payloads, client, active)

    private fun row(id: String, type: String, op: String = "insert") = SyncQueueEntity(
        queueId = "q-$id", entityType = type, entityId = id, operation = op, serverVersion = null, payload = null,
        syncStatus = "pending", attempts = 0, lastError = null, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `queued create is sent over REST and marked synced`() = runBlocking {
        coEvery { queue.pendingItemsList() } returns listOf(row("a1", "asset"))
        coEvery { payloads.buildPayload("acct", "asset", "a1") } returns buildJsonObject { put("name", "Lift") }
        coEvery { store.read<JsonObject?>(any()) } returns null
        coEvery { client.sendJson(any(), any(), any()) } returns NetworkResult.Success("{}")

        assertEquals(1, bridge.push("acct"))
        val body = JsonObject(mapOf("name" to JsonPrimitive("Lift"), "asset_id" to JsonPrimitive("a1")))
        coVerify { client.sendJson("POST", "/accounts/acct/assets", body) }
        coVerify { queue.markSuccess("q-a1") }
    }

    @Test
    fun `unsupported types are parked and offline stops the drain`() = runBlocking {
        coEvery { queue.pendingItemsList() } returns listOf(row("d1", "label_template"), row("a1", "asset"), row("a2", "asset"))
        coEvery { payloads.buildPayload("acct", "asset", any()) } returns buildJsonObject { put("name", "Lift") }
        coEvery { store.read<JsonObject?>(any()) } returns null
        coEvery { client.sendJson(any(), any(), any()) } returns NetworkResult.Error(-1, "offline")

        assertEquals(0, bridge.push("acct"))
        coVerify { queue.markError("q-d1", match { it.startsWith(V2LegacyBridge.UNSUPPORTED) }) }
        coVerify { queue.markOfflineRetry("q-a1", any()) }
        coVerify(exactly = 0) { payloads.buildPayload("acct", "asset", "a2") }
    }

    @Test
    fun `validation errors mark the row failed and the drain continues`() = runBlocking {
        coEvery { queue.pendingItemsList() } returns listOf(row("a1", "asset"), row("a2", "asset"))
        coEvery { payloads.buildPayload("acct", "asset", any()) } returns buildJsonObject { put("name", "Lift") }
        coEvery { store.read<JsonObject?>(any()) } returns null
        coEvery { client.sendJson(any(), any(), any()) } returnsMany listOf(
            NetworkResult.Error(422, "bad"),
            NetworkResult.Success("{}"),
        )

        assertEquals(1, bridge.push("acct"))
        coVerify { queue.markError("q-a1", match { it.contains("422") }) }
        coVerify { queue.markSuccess("q-a2") }
    }

    @Test
    fun `mirror prunes Room rows the server dropped but keeps queued ones`() = runBlocking {
        coEvery { store.read<List<JsonObject>>(any()) } returns emptyList()
        coEvery { queue.unsyncedEntityIds(any()) } returns emptyList()
        coEvery { queue.unsyncedEntityIds("asset") } returns listOf("local-new")
        coEvery { client.sendJson("GET", any(), null) } returns NetworkResult.Error(503, "down")
        val deleted = mutableListOf<Pair<String, Collection<String>>>()

        bridge.mirror(
            "acct",
            apply = { _, _ -> error("nothing to apply") },
            roomIds = { table, _, _ -> if (table == "assets") listOf("stale", "local-new") else emptyList() },
            delete = { table, _, ids -> deleted += table to ids },
        )
        assertEquals(listOf("assets" to listOf("stale")), deleted)
    }

    @Test
    fun `doc writes diff against the REST copy and docs mirror from the REST list`() = runBlocking {
        coEvery { queue.pendingItemsList() } returns listOf(row("d1", "doc", "update"))
        coEvery { payloads.buildPayload("acct", "doc", "d1") } returns buildJsonObject {
            put("doc_id", "d1"); put("asset_id", "a1"); put("title", "Invoice 2"); put("vendor", "Acme")
        }
        coEvery { client.sendJson("GET", "/accounts/acct/docs/d1", null) } returns
            NetworkResult.Success("""{"doc_id":"d1","title":"Invoice","vendor":"Acme"}""")
        coEvery { client.sendJson("PUT", any(), any()) } returns NetworkResult.Success("{}")

        assertEquals(1, bridge.push("acct"))
        coVerify { client.sendJson("PUT", "/accounts/acct/docs/d1", JsonObject(mapOf("title" to JsonPrimitive("Invoice 2")))) }

        coEvery { store.read<List<JsonObject>>(any()) } returns emptyList()
        coEvery { queue.unsyncedEntityIds(any()) } returns emptyList()
        coEvery { client.sendJson("GET", "/accounts/acct/docs?limit=500", null) } returns
            NetworkResult.Success("""{"docs":[{"doc_id":"d1","title":"Invoice 2"}]}""")
        val applied = mutableListOf<String>()
        val deleted = mutableListOf<Pair<String, Collection<String>>>()
        bridge.mirror(
            "acct",
            apply = { type, item -> applied += "$type:${(item["doc_id"] as JsonPrimitive).content}" },
            roomIds = { table, _, _ -> if (table == "docs") listOf("d1", "gone") else emptyList() },
            delete = { table, _, ids -> deleted += table to ids },
        )
        assertEquals(listOf("doc:d1"), applied)
        assertEquals(listOf("docs" to listOf("gone")), deleted)
    }

    @Test
    fun `locally deleted photos are deleted on the server`() = runBlocking {
        coEvery { queue.pendingItemsList() } returns emptyList()
        coEvery { photos.deletedUploaded("acct") } returns listOf(
            com.avago.core.data.db.entity.PhotoEntity(
                photoId = "p1", entityId = "a1", entityType = "asset", accountId = "acct", storageKey = "k",
                downloadUrl = null, sortOrder = 0, isPrimary = false, createdAt = 0, updatedAt = 0, deletedAt = 1,
                serverVersion = 1,
            ),
        )
        coEvery { client.sendJson("DELETE", "/accounts/acct/photos/p1", null) } returns NetworkResult.Success("{}")

        assertEquals(1, bridge.push("acct"))
        coVerify { photos.hardDelete("p1") }
    }
}