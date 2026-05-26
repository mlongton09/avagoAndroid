package com.avago.core.sync

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.SyncMetadataDao
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.network.AvagoServiceClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Provider

/**
 * Unit tests for DeltaPushApplier, verifying the gate logic matches iOS DeltaPushApplier
 * and that the cold-start armed gate is correctly persisted to sync_metadata.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeltaPushApplierTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockDb = mockk<AvagoDatabase>(relaxed = true)
    private val mockMetadataDao = mockk<SyncMetadataDao>()
    private val mockServiceClient = mockk<AvagoServiceClient>(relaxed = true)
    private val mockSyncEngine = mockk<SyncEngine>(relaxed = true)
    private val mockSyncEngineProvider = Provider { mockSyncEngine }

    private lateinit var applier: DeltaPushApplier

    companion object {
        private const val ACCOUNT_ID = "test-account-123"
        private const val ARMED_KEY = "__delta_push_armed__"
    }

    @BeforeEach
    fun setUp() {
        coEvery { mockDbFactory.get(ACCOUNT_ID) } returns mockDb
        every { mockDb.syncMetadataDao() } returns mockMetadataDao
        coEvery { mockSyncEngine.sync() } returns SyncResult.Success
        coEvery { mockMetadataDao.upsert(any()) } just Runs
        coEvery { mockMetadataDao.updateWatermark(any(), any()) } just Runs

        applier = DeltaPushApplier(
            dbFactory = mockDbFactory,
            serviceClient = mockServiceClient,
            syncEngine = mockSyncEngineProvider,
            scope = testScope,
        )
    }

    // ─── Cold-start gate ──────────────────────────────────────────────────────

    @Test
    fun `handle when gate not armed in memory or db returns IgnoredColdStart and triggers sync`() = runTest {
        // DB returns 0 (not armed)
        coEvery { mockMetadataDao.getWatermark(ARMED_KEY) } returns 0L

        val result = applier.handle("asset", 5L, ACCOUNT_ID)

        assertEquals(DeltaOutcome.IgnoredColdStart, result)
        // Sync must be triggered on connectivity recovery path
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(atLeast = 1) { mockSyncEngine.sync() }
    }

    @Test
    fun `handle when gate armed in db but not in memory cache returns Applied`() = runTest {
        // Process restarted: cache is empty, but DB shows armed = 1
        coEvery { mockMetadataDao.getWatermark(ARMED_KEY) } returns 1L
        coEvery { mockMetadataDao.getWatermark("asset") } returns 0L

        val result = applier.handle("asset", 1L, ACCOUNT_ID)

        // Should NOT be IgnoredColdStart — gate is armed via DB read
        assertFalse(result is DeltaOutcome.IgnoredColdStart,
            "Expected gate to be armed from DB; got $result")
    }

    // ─── markFirstSyncComplete ────────────────────────────────────────────────

    @Test
    fun `markFirstSyncComplete persists ARMED_KEY to sync_metadata`() = runTest {
        applier.markFirstSyncComplete(ACCOUNT_ID)

        coVerify {
            mockMetadataDao.upsert(match { it.entityType == ARMED_KEY && it.lastServerSeq == 1L })
        }
    }

    @Test
    fun `markFirstSyncComplete arms in-memory cache so subsequent handle skips DB read`() = runTest {
        // After markFirstSyncComplete, isArmed should be true in-memory without DB reads
        coEvery { mockMetadataDao.getWatermark("work_order") } returns 0L
        applier.markFirstSyncComplete(ACCOUNT_ID)

        // DB should NOT be queried for the armed key on the next handle() call
        val result = applier.handle("work_order", 1L, ACCOUNT_ID)

        assertFalse(result is DeltaOutcome.IgnoredColdStart,
            "Cache should be warm after markFirstSyncComplete; got $result")
        // Verify armed key was never queried (cache hit)
        coVerify(exactly = 0) { mockMetadataDao.getWatermark(ARMED_KEY) }
    }

    // ─── Stale gate ───────────────────────────────────────────────────────────

    @Test
    fun `handle when incomingSeq at or below watermark returns IgnoredStale without sync`() = runTest {
        coEvery { mockMetadataDao.getWatermark(ARMED_KEY) } returns 1L
        coEvery { mockMetadataDao.getWatermark("log") } returns 10L

        val equalResult = applier.handle("log", 10L, ACCOUNT_ID)
        val belowResult = applier.handle("log", 8L, ACCOUNT_ID)

        assertEquals(DeltaOutcome.IgnoredStale, equalResult)
        assertEquals(DeltaOutcome.IgnoredStale, belowResult)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { mockSyncEngine.sync() }
    }

    // ─── Gap gate ─────────────────────────────────────────────────────────────

    @Test
    fun `handle when gap exceeds 500 returns FellBackToFullSync and triggers sync`() = runTest {
        coEvery { mockMetadataDao.getWatermark(ARMED_KEY) } returns 1L
        coEvery { mockMetadataDao.getWatermark("work_order") } returns 0L

        // gap = 501
        val result = applier.handle("work_order", 501L, ACCOUNT_ID)

        assertTrue(result is DeltaOutcome.FellBackToFullSync)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(atLeast = 1) { mockSyncEngine.sync() }
    }

    @Test
    fun `handle when gap is exactly 500 returns Applied not FellBack`() = runTest {
        coEvery { mockMetadataDao.getWatermark(ARMED_KEY) } returns 1L
        coEvery { mockMetadataDao.getWatermark("asset") } returns 0L

        // gap = 500 (on the boundary — not greater than, so Applied)
        val result = applier.handle("asset", 500L, ACCOUNT_ID)

        assertEquals(DeltaOutcome.Applied, result)
    }

    // ─── Applied path ─────────────────────────────────────────────────────────

    @Test
    fun `handle when gap is small returns Applied and triggers sync`() = runTest {
        coEvery { mockMetadataDao.getWatermark(ARMED_KEY) } returns 1L
        coEvery { mockMetadataDao.getWatermark("inventory") } returns 42L

        // gap = 3
        val result = applier.handle("inventory", 45L, ACCOUNT_ID)

        assertEquals(DeltaOutcome.Applied, result)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(atLeast = 1) { mockSyncEngine.sync() }
    }
}
