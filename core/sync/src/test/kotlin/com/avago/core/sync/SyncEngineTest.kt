package com.avago.core.sync

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.AssetDao
import com.avago.core.data.db.dao.SyncMetadataDao
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkException
import com.avago.core.network.model.SyncOperationResult
import com.avago.core.network.model.SyncPullResponse
import com.avago.core.network.model.SyncPushResponse
import com.avago.core.ui.AvagoToast
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.UUID
import javax.inject.Provider

/**
 * Unit tests for SyncEngine verifying iOS parity on push routing, retry, resync, and pull guards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    // ── Dependencies ──────────────────────────────────────────────────────────

    private val mockIdentity = mockk<IdentityManager>(relaxed = true)
    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockClient = mockk<AvagoServiceClient>()
    private val mockPayloadBuilder = mockk<SyncPayloadBuilder>(relaxed = true)
    private val mockConflictCoordinator = mockk<SyncConflictCoordinator>(relaxed = true)
    private val mockToast = mockk<AvagoToast>(relaxed = true)
    private val mockDeltaApplier = mockk<DeltaPushApplier>(relaxed = true)
    private val mockPreferencesSync = mockk<PreferencesSync>(relaxed = true)
    private val mockPhotoUploader = mockk<PhotoUploader>(relaxed = true)
    private val mockSyncGate = mockk<SyncGate>(relaxed = true)
    private val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)
    private val mockConnectivity = mockk<ConnectivityMonitor>()

    // ── DB mocks ──────────────────────────────────────────────────────────────

    private val mockDb = mockk<AvagoDatabase>(relaxed = true)
    private val mockSyncQueueDao = mockk<SyncQueueDao>()
    private val mockMetadataDao = mockk<SyncMetadataDao>()
    private val mockAssetDao = mockk<AssetDao>(relaxed = true)
    private val mockOpenHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
    private val mockWritableDb = mockk<SupportSQLiteDatabase>(relaxed = true)
    private val mockReadableDb = mockk<SupportSQLiteDatabase>(relaxed = true)
    private val mockCursor = mockk<Cursor>()

    private lateinit var engine: SyncEngine

    companion object {
        private const val ACCOUNT_ID = "acct-001"
    }

    @BeforeEach
    fun setUp() {
        // IdentityManager is a final class; without the MockK inline agent its private backing
        // fields are null (constructor bypassed). Initialise _activeAccountId via reflection so
        // getActiveAccountId() works whether or not inline instrumentation is active.
        listOf("_activeAccountId", "_activeUserId", "_isInitialized", "_signOutEvents", "_devRoleOverride").forEach { name ->
            runCatching {
                IdentityManager::class.java.getDeclaredField(name).also {
                    it.isAccessible = true
                    when (name) {
                        "_activeAccountId" -> it.set(mockIdentity, kotlinx.coroutines.flow.MutableStateFlow<String?>(ACCOUNT_ID))
                        "_activeUserId"    -> it.set(mockIdentity, kotlinx.coroutines.flow.MutableStateFlow<String?>(null))
                        "_isInitialized"   -> it.set(mockIdentity, kotlinx.coroutines.flow.MutableStateFlow(false))
                        "_signOutEvents"   -> it.set(mockIdentity, kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4))
                        "_devRoleOverride" -> it.set(mockIdentity, kotlinx.coroutines.flow.MutableStateFlow<String?>(null))
                    }
                }
            }
        }

        every { mockConnectivity.networkStatus } returns networkStatusFlow

        // DB factory → mocked database
        coEvery { mockDbFactory.get(ACCOUNT_ID) } returns mockDb
        every { mockDb.syncQueueDao() } returns mockSyncQueueDao
        every { mockDb.syncMetadataDao() } returns mockMetadataDao
        every { mockDb.assetDao() } returns mockAssetDao
        every { mockDb.openHelper } returns mockOpenHelper
        every { mockOpenHelper.writableDatabase } returns mockWritableDb
        every { mockOpenHelper.readableDatabase } returns mockReadableDb

        // Cursor returns no row by default (localVersion = 0)
        every { mockReadableDb.query(any<String>(), any()) } returns mockCursor
        every { mockCursor.moveToFirst() } returns false
        every { mockCursor.close() } just Runs

        // SyncQueueDao defaults
        coEvery { mockSyncQueueDao.resetInFlightToPending() } just Runs
        coEvery { mockSyncQueueDao.resetErrorsToPending(any()) } just Runs
        coEvery { mockSyncQueueDao.markInFlight(any()) } just Runs
        coEvery { mockSyncQueueDao.markSuccess(any()) } just Runs
        coEvery { mockSyncQueueDao.markError(any(), any()) } just Runs
        coEvery { mockSyncQueueDao.markConflict(any()) } just Runs
        coEvery { mockSyncQueueDao.hasPendingPush(any(), any()) } returns null

        // MetadataDao defaults
        coEvery { mockMetadataDao.getWatermark(any()) } returns 0L
        coEvery { mockMetadataDao.upsert(any()) } just Runs
        coEvery { mockMetadataDao.updateWatermark(any(), any()) } just Runs

        // writableDatabase.execSQL for server_version update
        every { mockWritableDb.execSQL(any<String>(), any<Array<*>>()) } just Runs

        // withTransaction executes its block synchronously in tests
        // args[0] = receiver (mockDb), args[1] = the suspend block lambda
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { mockDb.withTransaction<Unit>(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Unit).invoke()
        }

        every { mockIdentity.getActiveAccountId() } returns ACCOUNT_ID

        engine = SyncEngine(
            identity = mockIdentity,
            dbFactory = mockDbFactory,
            client = mockClient,
            payloadBuilder = mockPayloadBuilder,
            conflictCoordinator = Provider { mockConflictCoordinator },
            toast = mockToast,
            scope = testScope,
            deltaApplier = Provider { mockDeltaApplier },
            preferencesSync = Provider { mockPreferencesSync },
            photoUploader = Provider { mockPhotoUploader },
            syncGate = mockSyncGate,
            connectivity = mockConnectivity,
        )
    }

    // ─── Push result routing ──────────────────────────────────────────────────

    @Test
    fun `push success marks item complete and stamps server version`() = runTest {
        val queueId = UUID.randomUUID().toString()
        val entityId = "asset-abc"
        val pendingItem = buildQueueEntity(queueId, "asset", entityId)
        coEvery { mockSyncQueueDao.pendingItemsList() } returns listOf(pendingItem)
        coEvery { mockPayloadBuilder.buildPayload(any(), any(), any()) } returns emptyJsonObject()

        coEvery { mockClient.syncPush(any(), any()) } returns SyncPushResponse(
            results = listOf(SyncOperationResult(entityId, success = true, server_version = 7L))
        )
        // Empty pull so we don't need full pull setup
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        val result = engine.sync()

        assertEquals(SyncResult.Success, result)
        coVerify { mockSyncQueueDao.markSuccess(queueId) }
        // server_version must be stamped to the entity row
        verify { mockWritableDb.execSQL(match { it.contains("UPDATE assets SET server_version") }, any<Array<*>>()) }
    }

    @Test
    fun `push conflict marks item as conflict and notifies coordinator`() = runTest {
        val queueId = UUID.randomUUID().toString()
        val entityId = "wo-xyz"
        val pendingItem = buildQueueEntity(queueId, "work_order", entityId)
        coEvery { mockSyncQueueDao.pendingItemsList() } returns listOf(pendingItem)
        coEvery { mockPayloadBuilder.buildPayload(any(), any(), any()) } returns emptyJsonObject()

        coEvery { mockClient.syncPush(any(), any()) } returns SyncPushResponse(
            results = listOf(SyncOperationResult(entityId, success = false, conflict = true, error = "version_conflict"))
        )
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        engine.sync()

        coVerify { mockSyncQueueDao.markConflict(queueId) }
        verify { mockConflictCoordinator.addConflict(match { it.entityId == entityId }) }
    }

    @Test
    fun `push error marks item with error message`() = runTest {
        val queueId = UUID.randomUUID().toString()
        val entityId = "log-001"
        val pendingItem = buildQueueEntity(queueId, "log", entityId)
        coEvery { mockSyncQueueDao.pendingItemsList() } returns listOf(pendingItem)
        coEvery { mockPayloadBuilder.buildPayload(any(), any(), any()) } returns emptyJsonObject()

        coEvery { mockClient.syncPush(any(), any()) } returns SyncPushResponse(
            results = listOf(SyncOperationResult(entityId, success = false, error = "invalid_payload"))
        )
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        engine.sync()

        coVerify { mockSyncQueueDao.markError(queueId, "invalid_payload") }
    }

    @Test
    fun `push entity_id lookup is case-insensitive`() = runTest {
        val queueId = UUID.randomUUID().toString()
        // Local entity ID is uppercase
        val entityId = "ASSET-UPPERCASE-UUID"
        val pendingItem = buildQueueEntity(queueId, "asset", entityId)
        coEvery { mockSyncQueueDao.pendingItemsList() } returns listOf(pendingItem)
        coEvery { mockPayloadBuilder.buildPayload(any(), any(), any()) } returns emptyJsonObject()

        // Server returns lowercase
        coEvery { mockClient.syncPush(any(), any()) } returns SyncPushResponse(
            results = listOf(SyncOperationResult(entityId.lowercase(), success = true, server_version = 2L))
        )
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        engine.sync()

        // Must still match and mark success despite case difference
        coVerify { mockSyncQueueDao.markSuccess(queueId) }
    }

    // ─── Retry / resiliency ───────────────────────────────────────────────────

    @Test
    fun `push retries on IOException and succeeds on second attempt`() = runTest {
        val queueId = UUID.randomUUID().toString()
        val entityId = "asset-retry"
        val pendingItem = buildQueueEntity(queueId, "asset", entityId)
        coEvery { mockSyncQueueDao.pendingItemsList() } returns listOf(pendingItem)
        coEvery { mockPayloadBuilder.buildPayload(any(), any(), any()) } returns emptyJsonObject()

        var callCount = 0
        coEvery { mockClient.syncPush(any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) throw IOException("network blip")
            SyncPushResponse(listOf(SyncOperationResult(entityId, success = true, server_version = 1L)))
        }
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        val result = engine.sync()

        assertEquals(SyncResult.Success, result)
        assertEquals(2, callCount, "Expected exactly 2 push attempts (1 failure + 1 success)")
        coVerify { mockSyncQueueDao.markSuccess(queueId) }
    }

    @Test
    fun `push fails after max retries and resets in-flight to pending`() = runTest {
        val queueId = UUID.randomUUID().toString()
        val pendingItem = buildQueueEntity(queueId, "asset", "asset-fail")
        coEvery { mockSyncQueueDao.pendingItemsList() } returns listOf(pendingItem)
        coEvery { mockPayloadBuilder.buildPayload(any(), any(), any()) } returns emptyJsonObject()

        coEvery { mockClient.syncPush(any(), any()) } throws IOException("persistent failure")

        val result = engine.sync()

        assertTrue(result is SyncResult.Failed)
        coVerify { mockSyncQueueDao.resetInFlightToPending() }
    }

    @Test
    fun `pull retries on IOException`() = runTest {
        coEvery { mockSyncQueueDao.pendingItemsList() } returns emptyList()

        var callCount = 0
        coEvery { mockClient.syncPull(any(), any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) throw IOException("flaky network")
            emptyPullResponse()
        }

        // Pull iterates all entity types; at least asset should retry
        engine.sync()

        assertTrue(callCount >= 2, "Expected at least one retry on pull; got $callCount calls")
    }

    // ─── resyncRequested ──────────────────────────────────────────────────────

    @Test
    fun `sync when already running returns Partial and queues a re-sync`() = runTest {
        coEvery { mockSyncQueueDao.pendingItemsList() } returns emptyList()
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        // Grab the internal mutex directly to simulate a sync already in progress.
        // This avoids cross-dispatcher races that arise from MockK coAnswers running on
        // Dispatchers.Default while the test scheduler waits on UnconfinedTestDispatcher.
        val mutexField = SyncEngine::class.java.getDeclaredField("mutex")
        mutexField.isAccessible = true
        val engineMutex = mutexField.get(engine) as kotlinx.coroutines.sync.Mutex
        engineMutex.lock()

        val result = try {
            engine.sync()
        } finally {
            engineMutex.unlock()
        }

        assertEquals(SyncResult.Partial(0, 0), result)
    }

    // ─── Pull guards ──────────────────────────────────────────────────────────

    @Test
    fun `pull hasPendingPush guard skips upsert for asset with local edit queued`() = runTest {
        coEvery { mockSyncQueueDao.pendingItemsList() } returns emptyList()

        val assetId = "asset-locally-edited"
        // hasPendingPush returns non-null → local edit exists
        coEvery { mockSyncQueueDao.hasPendingPush("asset", assetId) } returns 1

        val assetJson = buildJsonItem(
            "asset_id" to assetId,
            "account_id" to ACCOUNT_ID,
            "name" to "My Asset",
            "server_version" to "3",
        )
        coEvery { mockClient.syncPull(ACCOUNT_ID, "asset", any()) } returns SyncPullResponse(
            items = listOf(assetJson), has_more = false, max_seq = 5L
        )
        coEvery { mockClient.syncPull(ACCOUNT_ID, match { it != "asset" }, any()) } returns emptyPullResponse()

        engine.sync()

        // Upsert must NOT be called — hasPendingPush guard fired
        coVerify(exactly = 0) { mockAssetDao.upsert(any()) }
    }

    @Test
    fun `pull optimistic concurrency skips upsert when incoming version not newer`() = runTest {
        coEvery { mockSyncQueueDao.pendingItemsList() } returns emptyList()
        coEvery { mockSyncQueueDao.hasPendingPush(any(), any()) } returns null

        val assetId = "asset-already-current"
        // Local version = 10, server sends version = 10 → skip
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex("server_version") } returns 0
        every { mockCursor.getLong(0) } returns 10L

        val assetJson = buildJsonItem(
            "asset_id" to assetId,
            "account_id" to ACCOUNT_ID,
            "name" to "Stale Asset",
            "server_version" to "10",
        )
        coEvery { mockClient.syncPull(ACCOUNT_ID, "asset", any()) } returns SyncPullResponse(
            items = listOf(assetJson), has_more = false, max_seq = 10L
        )
        coEvery { mockClient.syncPull(ACCOUNT_ID, match { it != "asset" }, any()) } returns emptyPullResponse()

        engine.sync()

        // Must not upsert a row we already have at the same version
        coVerify(exactly = 0) { mockAssetDao.upsert(any()) }
    }

    @Test
    fun `pull upserts item when no local version exists`() = runTest {
        coEvery { mockSyncQueueDao.pendingItemsList() } returns emptyList()
        coEvery { mockSyncQueueDao.hasPendingPush(any(), any()) } returns null
        // moveToFirst() returns false (setUp default) → localVersion = 0 → version guard never skips

        val assetId = "asset-new-from-server"
        val assetJson = buildJsonItem(
            "asset_id" to assetId,
            "account_id" to ACCOUNT_ID,
            "name" to "Server Asset",
            "server_version" to "1",
        )
        coEvery { mockClient.syncPull(ACCOUNT_ID, "asset", any()) } returns SyncPullResponse(
            items = listOf(assetJson), has_more = false, max_seq = 5L
        )
        coEvery { mockClient.syncPull(ACCOUNT_ID, match { it != "asset" }, any()) } returns emptyPullResponse()

        val entitySlot = slot<com.avago.core.data.db.entity.AssetEntity>()
        coEvery { mockAssetDao.upsert(capture(entitySlot)) } just Runs

        engine.sync()

        assertTrue(entitySlot.isCaptured, "Expected assetDao.upsert() to be called with an AssetEntity")
        assertEquals(assetId, entitySlot.captured.assetId)
    }

    // ─── resetErrorsToPending on push start ───────────────────────────────────

    @Test
    fun `runSync resets error items to pending at start of push phase`() = runTest {
        coEvery { mockSyncQueueDao.pendingItemsList() } returns emptyList()
        coEvery { mockClient.syncPull(any(), any(), any()) } returns emptyPullResponse()

        engine.sync()

        coVerify { mockSyncQueueDao.resetErrorsToPending(any()) }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildQueueEntity(
        queueId: String,
        entityType: String,
        entityId: String,
    ) = SyncQueueEntity(
        queueId = queueId,
        entityType = entityType,
        entityId = entityId,
        operation = "update",
        serverVersion = 1L,
        payload = null,
        syncStatus = "pending",
        attempts = 0L,
        lastError = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    private fun emptyJsonObject() = JsonObject(emptyMap())

    private fun emptyPullResponse() = SyncPullResponse(items = emptyList(), has_more = false, max_seq = 0L)

    private fun buildJsonItem(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    // neq() removed — use `match { it != "value" }` inline in mock setup blocks
}
