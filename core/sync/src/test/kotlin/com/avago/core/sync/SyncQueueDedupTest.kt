package com.avago.core.sync

import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for SyncQueueDao.enqueueWithDedup — the iOS-parity dedup rules:
 *
 *   insert + delete  → cancel both (remove pending insert, skip enqueueing delete)
 *   insert + update  → keep as "insert" but use the latest payload
 *   delete + insert  → treat as a fresh insert (re-create on server)
 *   anything else    → replace the existing row with the new one
 *
 * We exercise the concrete interface method via an in-memory FakeSyncQueueDao so
 * the logic runs on the JVM without Room or Android infrastructure.
 */
class SyncQueueDedupTest {

    // ─── Fake DAO ─────────────────────────────────────────────────────────────

    private class FakeSyncQueueDao : SyncQueueDao {
        val store = mutableMapOf<String, SyncQueueEntity>()

        override suspend fun upsert(entity: SyncQueueEntity) {
            store[entity.queueId] = entity
        }

        override suspend fun softDelete(id: String) {
            store.remove(id)
        }

        override suspend fun getPendingByQueueId(queueId: String): SyncQueueEntity? =
            store[queueId]?.takeIf { it.syncStatus in setOf("pending", "error") }

        override fun pendingItems(): Flow<List<SyncQueueEntity>> = flowOf(store.values.toList())
        override suspend fun getById(id: String): SyncQueueEntity? = store[id]
        override suspend fun upsertAll(entities: List<SyncQueueEntity>) = entities.forEach { upsert(it) }
        override suspend fun markInFlight(queueIds: List<String>) {}
        override suspend fun markSuccess(queueId: String) { store.remove(queueId) }
        override suspend fun markError(queueId: String, error: String) {}
        override suspend fun resetInFlightToPending() {}
        override suspend fun enqueueOrReplace(entity: SyncQueueEntity) { upsert(entity) }
        override suspend fun pendingItemsList(): List<SyncQueueEntity> = store.values.toList()
        override suspend fun resetConflictToPending(queueId: String) {}
        override suspend fun markConflict(queueId: String) {}
        override suspend fun resetErrorsToPending(maxAttempts: Long) {}
        override suspend fun hasPendingPush(entityType: String, entityId: String): Int? = null
    }

    private lateinit var dao: FakeSyncQueueDao

    @BeforeEach
    fun setUp() {
        dao = FakeSyncQueueDao()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun entity(
        queueId: String,
        operation: String,
        status: String = "pending",
        serverVersion: Long = 0L,
    ) = SyncQueueEntity(
        queueId = queueId,
        entityType = "asset",
        entityId = queueId.substringAfter("_"),
        operation = operation,
        serverVersion = serverVersion,
        payload = null,
        syncStatus = status,
        attempts = 0L,
        lastError = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    // ─── insert + delete → cancel both ───────────────────────────────────────

    @Test
    fun `insert then delete cancels both`() = runTest {
        dao.upsert(entity("asset_e1", "insert"))
        dao.enqueueWithDedup(entity("asset_e1", "delete"))
        assertTrue(dao.store.isEmpty(),
            "insert+delete should cancel both; store was: ${dao.store.keys}")
    }

    @Test
    fun `insert then delete leaves store empty`() = runTest {
        dao.store["asset_e1"] = entity("asset_e1", "insert")
        dao.enqueueWithDedup(entity("asset_e1", "delete"))
        assertNull(dao.store["asset_e1"])
        assertEquals(0, dao.store.size)
    }

    // ─── insert + update → keep as insert ────────────────────────────────────

    @Test
    fun `insert then update preserves insert operation`() = runTest {
        dao.upsert(entity("asset_e2", "insert"))
        dao.enqueueWithDedup(entity("asset_e2", "update"))
        val result = dao.store["asset_e2"]
        assertNotNull(result)
        assertEquals("insert", result!!.operation,
            "Merging update into pending insert must keep operation=insert")
    }

    @Test
    fun `insert then update uses latest server version`() = runTest {
        dao.upsert(entity("asset_e2b", "insert", serverVersion = 0L))
        dao.enqueueWithDedup(entity("asset_e2b", "update", serverVersion = 5L))
        assertEquals(5L, dao.store["asset_e2b"]?.serverVersion,
            "Merged insert row must use the newer serverVersion from the update")
    }

    // ─── no existing entry ────────────────────────────────────────────────────

    @Test
    fun `no prior entry just upserts insert`() = runTest {
        dao.enqueueWithDedup(entity("asset_e3", "insert"))
        assertEquals(1, dao.store.size)
        assertEquals("insert", dao.store["asset_e3"]?.operation)
    }

    @Test
    fun `no prior entry just upserts update`() = runTest {
        dao.enqueueWithDedup(entity("asset_e3b", "update"))
        assertEquals("update", dao.store["asset_e3b"]?.operation)
    }

    @Test
    fun `no prior entry just upserts delete`() = runTest {
        dao.enqueueWithDedup(entity("asset_e3c", "delete"))
        assertEquals("delete", dao.store["asset_e3c"]?.operation)
    }

    // ─── update + update → replace ───────────────────────────────────────────

    @Test
    fun `update then update replaces with latest server version`() = runTest {
        dao.upsert(entity("asset_e4", "update", serverVersion = 1L))
        dao.enqueueWithDedup(entity("asset_e4", "update", serverVersion = 2L))
        assertEquals(2L, dao.store["asset_e4"]?.serverVersion)
        assertEquals(1, dao.store.size)
    }

    // ─── delete + insert → fresh insert ──────────────────────────────────────

    @Test
    fun `pending delete then new insert creates fresh insert`() = runTest {
        dao.upsert(entity("asset_e5", "delete"))
        dao.enqueueWithDedup(entity("asset_e5", "insert"))
        val result = dao.store["asset_e5"]
        assertNotNull(result)
        assertEquals("insert", result!!.operation,
            "delete followed by insert must treat it as a fresh insert on the server")
    }

    // ─── error status in pending lookup ──────────────────────────────────────

    @Test
    fun `error status insert followed by delete also cancels`() = runTest {
        dao.store["asset_e6"] = entity("asset_e6", "insert", status = "error")
        dao.enqueueWithDedup(entity("asset_e6", "delete"))
        assertTrue(dao.store.isEmpty(),
            "error-status insert + delete should also cancel both")
    }

    // ─── in_flight not deduped ────────────────────────────────────────────────

    @Test
    fun `in_flight entry is not matched by getPendingByQueueId`() = runTest {
        dao.store["asset_e7"] = entity("asset_e7", "insert", status = "in_flight")
        dao.enqueueWithDedup(entity("asset_e7", "delete"))
        // in_flight excluded → falls to default upsert (replaces with delete)
        val result = dao.store["asset_e7"]
        assertNotNull(result)
        assertEquals("delete", result!!.operation,
            "in_flight row is not considered pending; new entry replaces it")
    }

    // ─── multiple independent queue IDs ──────────────────────────────────────

    @Test
    fun `multiple distinct queueIds are independent`() = runTest {
        dao.enqueueWithDedup(entity("asset_X", "insert"))
        dao.enqueueWithDedup(entity("asset_Y", "insert"))
        assertEquals(2, dao.store.size, "Two distinct queue entries must coexist")
        dao.enqueueWithDedup(entity("asset_X", "delete"))
        assertEquals(1, dao.store.size, "Only asset_X should be cancelled")
        assertNotNull(dao.store["asset_Y"], "asset_Y must be unaffected")
    }

    @Test
    fun `different entity types with same entityId are independent`() = runTest {
        // queueId = "asset_id123" vs "log_id123" — different keys
        val assetEntry = entity("asset_id123", "insert")
        val logEntry = entity("log_id123", "insert")
        dao.enqueueWithDedup(assetEntry)
        dao.enqueueWithDedup(logEntry)
        assertEquals(2, dao.store.size)
    }
}
