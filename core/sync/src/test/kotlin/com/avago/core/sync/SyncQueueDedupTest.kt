package com.avago.core.sync

import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
 * Dedup key is (entity_type, entity_id), not queue_id — rapid back-to-back edits
 * to the same entity collapse to a single queue row.
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

        override suspend fun getPendingByEntity(entityType: String, entityId: String): SyncQueueEntity? =
            store.values.firstOrNull { it.entityType == entityType && it.entityId == entityId && it.syncStatus in setOf("pending", "error") }

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
        entityType: String = "asset",
        entityId: String = queueId,
        status: String = "pending",
        serverVersion: Long = 0L,
    ) = SyncQueueEntity(
        queueId = queueId,
        entityType = entityType,
        entityId = entityId,
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
        dao.upsert(entity("q1", "insert", entityId = "e1"))
        dao.enqueueWithDedup(entity("q2", "delete", entityId = "e1"))
        assertTrue(dao.store.isEmpty(),
            "insert+delete should cancel both; store was: ${dao.store.keys}")
    }

    @Test
    fun `insert then delete leaves store empty`() = runTest {
        dao.store["q1"] = entity("q1", "insert", entityId = "e1")
        dao.enqueueWithDedup(entity("q2", "delete", entityId = "e1"))
        assertNull(dao.store["q1"])
        assertEquals(0, dao.store.size)
    }

    // ─── insert + update → keep as insert ────────────────────────────────────

    @Test
    fun `insert then update preserves insert operation`() = runTest {
        dao.upsert(entity("q1", "insert", entityId = "e2"))
        dao.enqueueWithDedup(entity("q2", "update", entityId = "e2"))
        val result = dao.store["q1"]
        assertNotNull(result)
        assertEquals("insert", result!!.operation,
            "Merging update into pending insert must keep operation=insert")
    }

    @Test
    fun `insert then update reuses original queue id`() = runTest {
        dao.upsert(entity("q1", "insert", entityId = "e2b"))
        dao.enqueueWithDedup(entity("q2", "update", entityId = "e2b"))
        assertEquals(1, dao.store.size, "Merge must not create a second queue row")
        assertNotNull(dao.store["q1"], "Original queue_id must be preserved")
    }

    @Test
    fun `insert then update uses latest server version`() = runTest {
        dao.upsert(entity("q1", "insert", entityId = "e2c", serverVersion = 0L))
        dao.enqueueWithDedup(entity("q2", "update", entityId = "e2c", serverVersion = 5L))
        assertEquals(5L, dao.store["q1"]?.serverVersion,
            "Merged insert row must use the newer serverVersion from the update")
    }

    // ─── no existing entry ────────────────────────────────────────────────────

    @Test
    fun `no prior entry just upserts insert`() = runTest {
        dao.enqueueWithDedup(entity("q1", "insert", entityId = "e3"))
        assertEquals(1, dao.store.size)
        assertEquals("insert", dao.store["q1"]?.operation)
    }

    @Test
    fun `no prior entry just upserts update`() = runTest {
        dao.enqueueWithDedup(entity("q1", "update", entityId = "e3b"))
        assertEquals("update", dao.store["q1"]?.operation)
    }

    @Test
    fun `no prior entry just upserts delete`() = runTest {
        dao.enqueueWithDedup(entity("q1", "delete", entityId = "e3c"))
        assertEquals("delete", dao.store["q1"]?.operation)
    }

    // ─── update + update → replace ───────────────────────────────────────────

    @Test
    fun `update then update replaces with latest server version`() = runTest {
        dao.upsert(entity("q1", "update", entityId = "e4", serverVersion = 1L))
        dao.enqueueWithDedup(entity("q2", "update", entityId = "e4", serverVersion = 2L))
        assertEquals(2L, dao.store["q1"]?.serverVersion)
        assertEquals(1, dao.store.size)
    }

    // ─── delete + insert → fresh insert ──────────────────────────────────────

    @Test
    fun `pending delete then new insert creates fresh insert`() = runTest {
        dao.upsert(entity("q1", "delete", entityId = "e5"))
        dao.enqueueWithDedup(entity("q2", "insert", entityId = "e5"))
        val result = dao.store["q1"]
        assertNotNull(result)
        assertEquals("insert", result!!.operation,
            "delete followed by insert must treat it as a fresh insert on the server")
    }

    // ─── error status in pending lookup ──────────────────────────────────────

    @Test
    fun `error status insert followed by delete also cancels`() = runTest {
        dao.store["q1"] = entity("q1", "insert", entityId = "e6", status = "error")
        dao.enqueueWithDedup(entity("q2", "delete", entityId = "e6"))
        assertTrue(dao.store.isEmpty(),
            "error-status insert + delete should also cancel both")
    }

    // ─── in_flight not deduped ────────────────────────────────────────────────

    @Test
    fun `in_flight entry is not matched by getPendingByEntity`() = runTest {
        dao.store["q1"] = entity("q1", "insert", entityId = "e7", status = "in_flight")
        dao.enqueueWithDedup(entity("q2", "delete", entityId = "e7"))
        // in_flight excluded → falls to default upsert (adds new delete row)
        assertNotNull(dao.store["q2"],
            "new delete row should be inserted when existing is in_flight")
        assertEquals("delete", dao.store["q2"]!!.operation)
    }

    // ─── multiple independent entities ───────────────────────────────────────

    @Test
    fun `multiple distinct entityIds are independent`() = runTest {
        dao.enqueueWithDedup(entity("q1", "insert", entityId = "eX"))
        dao.enqueueWithDedup(entity("q2", "insert", entityId = "eY"))
        assertEquals(2, dao.store.size, "Two distinct entity entries must coexist")
        dao.enqueueWithDedup(entity("q3", "delete", entityId = "eX"))
        assertEquals(1, dao.store.size, "Only eX should be cancelled")
        assertNotNull(dao.store["q2"], "eY must be unaffected")
    }

    @Test
    fun `different entity types with same entityId are independent`() = runTest {
        dao.enqueueWithDedup(entity("q1", "insert", entityType = "asset", entityId = "id123"))
        dao.enqueueWithDedup(entity("q2", "insert", entityType = "log", entityId = "id123"))
        assertEquals(2, dao.store.size,
            "Same entityId but different entityType must be independent queue entries")
    }
}
