package com.avago.core.sync

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.AssetDao
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.repository.AssetRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for AssetRepository, covering:
 *   - insert vs update operation routing based on serverVersion
 *   - FRE cascade delete when the first real asset is saved
 *   - softDeleteAsset enqueues delete with correct serverVersion
 *   - queueId format: "asset_{assetId}"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetRepositoryTest {

    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockDb = mockk<AvagoDatabase>(relaxed = true)
    private val mockAssetDao = mockk<AssetDao>(relaxed = true)
    private val mockSyncQueueDao = mockk<SyncQueueDao>(relaxed = true)

    private lateinit var repository: AssetRepository

    companion object {
        private const val ACCOUNT_ID = "acct-001"
    }

    @BeforeEach
    fun setUp() {
        coEvery { mockDbFactory.get(ACCOUNT_ID) } returns mockDb
        every { mockDb.assetDao() } returns mockAssetDao
        every { mockDb.syncQueueDao() } returns mockSyncQueueDao
        coEvery { mockSyncQueueDao.enqueueWithDedup(any()) } just Runs
        coEvery { mockAssetDao.upsert(any()) } just Runs
        coEvery { mockAssetDao.softDelete(any(), any()) } just Runs
        coEvery { mockAssetDao.softDeleteAllFreSamples(any(), any()) } just Runs

        repository = AssetRepository(mockDbFactory)
    }

    // ─── insert vs update routing ─────────────────────────────────────────────

    @Test
    fun `new asset (serverVersion=0) enqueues insert operation`() = runTest {
        val entity = buildAsset(serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "insert" &&
                    it.entityId == entity.assetId &&
                    it.entityType == "asset"
            })
        }
    }

    @Test
    fun `existing asset (serverVersion=1) enqueues update operation`() = runTest {
        val entity = buildAsset(serverVersion = 1L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 2

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.operation == "update" })
        }
    }

    @Test
    fun `existing asset (serverVersion=99) enqueues update not insert`() = runTest {
        val entity = buildAsset(serverVersion = 99L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 5

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.operation == "update" })
        }
    }

    // ─── queueId format ───────────────────────────────────────────────────────

    @Test
    fun `queueId is entityType underscore entityId`() = runTest {
        val entity = buildAsset(assetId = "abc-123", serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.queueId == "asset_abc-123" })
        }
    }

    // ─── FRE cascade ──────────────────────────────────────────────────────────

    @Test
    fun `FRE cascade fires when first real asset is saved (realCount=1)`() = runTest {
        val entity = buildAsset(serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 1
        coEvery { mockAssetDao.freeSampleAssetIds(ACCOUNT_ID) } returns listOf("fre-001", "fre-002")

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify(exactly = 1) { mockAssetDao.softDeleteAllFreSamples(ACCOUNT_ID, any()) }
        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.entityId == "fre-001" && it.operation == "delete"
            })
        }
        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.entityId == "fre-002" && it.operation == "delete"
            })
        }
    }

    @Test
    fun `FRE cascade enqueues delete for every sample asset id`() = runTest {
        val freIds = listOf("fre-A", "fre-B", "fre-C")
        val entity = buildAsset(serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 1
        coEvery { mockAssetDao.freeSampleAssetIds(ACCOUNT_ID) } returns freIds

        repository.upsertAsset(ACCOUNT_ID, entity)

        freIds.forEach { id ->
            coVerify {
                mockSyncQueueDao.enqueueWithDedup(match {
                    it.entityId == id && it.operation == "delete" && it.queueId == "asset_$id"
                })
            }
        }
    }

    @Test
    fun `FRE cascade skipped when realCount is 0`() = runTest {
        val entity = buildAsset(serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify(exactly = 0) { mockAssetDao.softDeleteAllFreSamples(any(), any()) }
    }

    @Test
    fun `FRE cascade skipped when realCount is greater than 1`() = runTest {
        val entity = buildAsset(serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 3

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify(exactly = 0) { mockAssetDao.softDeleteAllFreSamples(any(), any()) }
    }

    @Test
    fun `FRE sample save does not trigger cascade check`() = runTest {
        val entity = buildAsset(serverVersion = 0L, isFreSample = true)

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify(exactly = 0) { mockAssetDao.countRealAssets(any()) }
        coVerify(exactly = 0) { mockAssetDao.softDeleteAllFreSamples(any(), any()) }
    }

    @Test
    fun `FRE cascade skipped when no FRE samples exist`() = runTest {
        val entity = buildAsset(serverVersion = 0L, isFreSample = false)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 1
        coEvery { mockAssetDao.freeSampleAssetIds(ACCOUNT_ID) } returns emptyList()

        repository.upsertAsset(ACCOUNT_ID, entity)

        coVerify(exactly = 0) { mockAssetDao.softDeleteAllFreSamples(any(), any()) }
    }

    // ─── softDelete ───────────────────────────────────────────────────────────

    @Test
    fun `softDeleteAsset calls dao softDelete and enqueues delete`() = runTest {
        val assetId = "asset-to-delete"
        coEvery { mockAssetDao.getById(assetId) } returns buildAsset(assetId = assetId, serverVersion = 3L)

        repository.softDeleteAsset(ACCOUNT_ID, assetId)

        coVerify { mockAssetDao.softDelete(assetId, any()) }
        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "delete" && it.entityId == assetId
            })
        }
    }

    @Test
    fun `softDeleteAsset passes serverVersion from DB row`() = runTest {
        val assetId = "asset-versioned"
        coEvery { mockAssetDao.getById(assetId) } returns buildAsset(assetId = assetId, serverVersion = 9L)

        repository.softDeleteAsset(ACCOUNT_ID, assetId)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.serverVersion == 9L })
        }
    }

    @Test
    fun `softDeleteAsset with missing row uses serverVersion 0`() = runTest {
        val assetId = "nonexistent"
        coEvery { mockAssetDao.getById(assetId) } returns null

        repository.softDeleteAsset(ACCOUNT_ID, assetId)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.serverVersion == 0L })
        }
    }

    @Test
    fun `softDeleteAsset syncStatus is pending`() = runTest {
        coEvery { mockAssetDao.getById("a1") } returns buildAsset(assetId = "a1")

        repository.softDeleteAsset(ACCOUNT_ID, "a1")

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.syncStatus == "pending" })
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildAsset(
        assetId: String = "asset-001",
        serverVersion: Long = 0L,
        isFreSample: Boolean = false,
    ) = AssetEntity(
        assetId = assetId,
        accountId = ACCOUNT_ID,
        name = "Test Asset",
        make = null,
        model = null,
        year = null,
        assetType = null,
        meterType = null,
        avatarColor = null,
        avatarInitial = null,
        addressLine1 = null,
        addressLine2 = null,
        city = null,
        state = null,
        postalCode = null,
        country = null,
        locationId = null,
        attributes = null,
        isFreSample = isFreSample,
        parentAssetId = null,
        path = null,
        depth = 0L,
        childCount = 0L,
        isRental = false,
        rentalRate = null,
        rentalRateUnit = null,
        purchasePrice = null,
        salvageValue = null,
        usefulLifeMonths = null,
        depreciationMethod = null,
        placedInServiceDate = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
        serverVersion = serverVersion,
        seq = null,
    )
}
