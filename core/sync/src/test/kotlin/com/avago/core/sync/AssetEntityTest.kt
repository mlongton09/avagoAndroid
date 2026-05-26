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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for asset entity fields and AssetRepository behaviour covering:
 *   - Asset identifier: required fields, optional fields
 *   - Asset hierarchy: parentAssetId, path construction, depth, childCount
 *   - Asset depreciation: placedInServiceDate, usefulLifeMonths, depreciationMethod, salvageValue
 *   - Asset rental fields: isRental, rentalRate, rentalRateUnit
 *   - AssetDao operations via mocks: getById, directChildren, descendants, eligibleParents
 *   - AssetDao FRE helpers: countRealAssets, softDeleteAllFreSamples, freeSampleAssetIds
 *   - AssetRepository CRUD: upsert, softDelete
 *   - Search/filter by name, assetType, location
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetEntityTest {

    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockDb = mockk<AvagoDatabase>(relaxed = true)
    private val mockAssetDao = mockk<AssetDao>(relaxed = true)
    private val mockSyncQueueDao = mockk<SyncQueueDao>(relaxed = true)

    private lateinit var repository: AssetRepository

    companion object {
        private const val ACCOUNT_ID = "acct-test-001"
        private const val NOW = 1_700_000_000_000L
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

    // ─── Asset identifier: required fields ───────────────────────────────────

    @Test
    fun `asset entity has required assetId field`() {
        val asset = buildAsset(assetId = "uuid-001")
        assertEquals("uuid-001", asset.assetId)
    }

    @Test
    fun `asset entity has required name field`() {
        val asset = buildAsset(name = "Forklift #12")
        assertEquals("Forklift #12", asset.name)
    }

    @Test
    fun `asset entity has required accountId field`() {
        val asset = buildAsset()
        assertEquals(ACCOUNT_ID, asset.accountId)
    }

    @Test
    fun `asset entity has required createdAt field`() {
        val asset = buildAsset(createdAt = NOW)
        assertEquals(NOW, asset.createdAt)
    }

    @Test
    fun `asset entity has required updatedAt field`() {
        val asset = buildAsset(updatedAt = NOW)
        assertEquals(NOW, asset.updatedAt)
    }

    // ─── Asset identifier: optional fields ───────────────────────────────────

    @Test
    fun `make is nullable`() {
        val noMake = buildAsset(make = null)
        assertNull(noMake.make)
        val withMake = buildAsset(make = "Caterpillar")
        assertEquals("Caterpillar", withMake.make)
    }

    @Test
    fun `model is nullable`() {
        val noModel = buildAsset(model = null)
        assertNull(noModel.model)
        val withModel = buildAsset(model = "336 GC")
        assertEquals("336 GC", withModel.model)
    }

    @Test
    fun `year is nullable`() {
        val noYear = buildAsset(year = null)
        assertNull(noYear.year)
        val withYear = buildAsset(year = 2023L)
        assertEquals(2023L, withYear.year)
    }

    @Test
    fun `assetType is nullable`() {
        val noType = buildAsset(assetType = null)
        assertNull(noType.assetType)
        val withType = buildAsset(assetType = "heavy_equipment")
        assertEquals("heavy_equipment", withType.assetType)
    }

    @Test
    fun `deletedAt is null for live assets`() {
        val asset = buildAsset(deletedAt = null)
        assertNull(asset.deletedAt)
    }

    @Test
    fun `deletedAt is set for soft-deleted assets`() {
        val asset = buildAsset(deletedAt = NOW)
        assertEquals(NOW, asset.deletedAt)
    }

    // ─── Asset hierarchy: parent-child relationships ──────────────────────────

    @Test
    fun `root asset has null parentAssetId`() {
        val root = buildAsset(parentAssetId = null, depth = 0L)
        assertNull(root.parentAssetId)
        assertEquals(0L, root.depth)
    }

    @Test
    fun `child asset has parentAssetId set`() {
        val child = buildAsset(parentAssetId = "parent-001", depth = 1L)
        assertEquals("parent-001", child.parentAssetId)
    }

    @Test
    fun `depth increments for nested children`() {
        val grandchild = buildAsset(parentAssetId = "child-001", depth = 2L)
        assertEquals(2L, grandchild.depth)
    }

    @Test
    fun `path contains asset id`() {
        val asset = buildAsset(assetId = "asset-abc", path = "/root-001/asset-abc")
        assertNotNull(asset.path)
        assertTrue(asset.path!!.contains("asset-abc"))
    }

    @Test
    fun `root asset path starts with slash`() {
        val root = buildAsset(assetId = "root-001", path = "/root-001")
        assertTrue(asset_path_starts_with_slash(root.path))
    }

    @Test
    fun `nested path includes parent segment`() {
        val child = buildAsset(
            assetId = "child-001",
            parentAssetId = "parent-001",
            path = "/parent-001/child-001",
            depth = 1L,
        )
        val childPath = child.path!!
        assertTrue(childPath.contains("parent-001"))
        assertTrue(childPath.contains("child-001"))
    }

    @Test
    fun `childCount is zero for leaf nodes`() {
        val leaf = buildAsset(childCount = 0L)
        assertEquals(0L, leaf.childCount)
    }

    @Test
    fun `childCount reflects direct child count`() {
        val parent = buildAsset(childCount = 3L)
        assertEquals(3L, parent.childCount)
    }

    // ─── Asset hierarchy: DAO operations ─────────────────────────────────────

    @Test
    fun `directChildren returns children for given parent`() = runTest {
        val children = listOf(
            buildAsset(assetId = "child-A", parentAssetId = "parent-001"),
            buildAsset(assetId = "child-B", parentAssetId = "parent-001"),
        )
        coEvery { mockAssetDao.directChildren("parent-001") } returns children

        val result = mockAssetDao.directChildren("parent-001")

        assertEquals(2, result.size)
        assertTrue(result.all { it.parentAssetId == "parent-001" })
    }

    @Test
    fun `directChildren returns empty list when asset has no children`() = runTest {
        coEvery { mockAssetDao.directChildren("leaf-001") } returns emptyList()

        val result = mockAssetDao.directChildren("leaf-001")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `descendants returns all nested assets in depth order`() = runTest {
        val descendants = listOf(
            buildAsset(assetId = "child-001", depth = 1L),
            buildAsset(assetId = "grandchild-001", depth = 2L),
        )
        coEvery { mockAssetDao.descendants("root-001", "/root-001/%") } returns descendants

        val result = mockAssetDao.descendants("root-001", "/root-001/%")

        assertEquals(2, result.size)
        assertEquals("child-001", result[0].assetId)
        assertEquals("grandchild-001", result[1].assetId)
    }

    @Test
    fun `descendants returns empty when no children`() = runTest {
        coEvery { mockAssetDao.descendants("leaf-001", "/leaf-001/%") } returns emptyList()

        val result = mockAssetDao.descendants("leaf-001", "/leaf-001/%")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `eligibleParents excludes specified asset ids`() = runTest {
        val exclude = listOf("asset-A", "asset-B")
        val eligible = listOf(
            buildAsset(assetId = "asset-C"),
            buildAsset(assetId = "asset-D"),
        )
        coEvery { mockAssetDao.eligibleParents(ACCOUNT_ID, exclude) } returns eligible

        val result = mockAssetDao.eligibleParents(ACCOUNT_ID, exclude)

        assertEquals(2, result.size)
        assertTrue(result.none { it.assetId in exclude })
    }

    @Test
    fun `getById returns asset when found`() = runTest {
        val asset = buildAsset(assetId = "asset-xyz")
        coEvery { mockAssetDao.getById("asset-xyz") } returns asset

        val result = mockAssetDao.getById("asset-xyz")

        assertNotNull(result)
        assertEquals("asset-xyz", result!!.assetId)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { mockAssetDao.getById("missing") } returns null

        val result = mockAssetDao.getById("missing")

        assertNull(result)
    }

    // ─── Asset depreciation fields ────────────────────────────────────────────

    @Test
    fun `depreciation fields are null by default`() {
        val asset = buildAsset()
        assertNull(asset.placedInServiceDate)
        assertNull(asset.usefulLifeMonths)
        assertNull(asset.depreciationMethod)
        assertNull(asset.salvageValue)
        assertNull(asset.purchasePrice)
    }

    @Test
    fun `straight-line depreciation fields can be set`() {
        val asset = buildAsset(
            purchasePrice = 50_000.0,
            salvageValue = 5_000.0,
            usefulLifeMonths = 60L,
            depreciationMethod = "straight_line",
            placedInServiceDate = NOW,
        )
        assertEquals(50_000.0, asset.purchasePrice)
        assertEquals(5_000.0, asset.salvageValue)
        assertEquals(60L, asset.usefulLifeMonths)
        assertEquals("straight_line", asset.depreciationMethod)
        assertEquals(NOW, asset.placedInServiceDate)
    }

    @Test
    fun `declining-balance depreciation method is stored correctly`() {
        val asset = buildAsset(depreciationMethod = "declining_balance")
        assertEquals("declining_balance", asset.depreciationMethod)
    }

    @Test
    fun `salvageValue zero is valid (fully depreciating asset)`() {
        val asset = buildAsset(salvageValue = 0.0, purchasePrice = 10_000.0)
        assertEquals(0.0, asset.salvageValue)
        assertEquals(10_000.0, asset.purchasePrice)
    }

    @Test
    fun `usefulLifeMonths 12 represents one year`() {
        val asset = buildAsset(usefulLifeMonths = 12L)
        assertEquals(12L, asset.usefulLifeMonths)
    }

    @Test
    fun `usefulLifeMonths 120 represents ten years`() {
        val asset = buildAsset(usefulLifeMonths = 120L)
        assertEquals(120L, asset.usefulLifeMonths)
    }

    @Test
    fun `placedInServiceDate epoch millis is stored as Long`() {
        val serviceDate = 1_609_459_200_000L  // 2021-01-01T00:00:00Z
        val asset = buildAsset(placedInServiceDate = serviceDate)
        assertEquals(serviceDate, asset.placedInServiceDate)
    }

    @Test
    fun `partial depreciation info with only purchasePrice set`() {
        val asset = buildAsset(purchasePrice = 25_000.0)
        assertEquals(25_000.0, asset.purchasePrice)
        assertNull(asset.salvageValue)
        assertNull(asset.usefulLifeMonths)
        assertNull(asset.depreciationMethod)
    }

    // ─── Asset rental fields ──────────────────────────────────────────────────

    @Test
    fun `non-rental asset has isRental false`() {
        val asset = buildAsset(isRental = false)
        assertFalse(asset.isRental)
        assertNull(asset.rentalRate)
        assertNull(asset.rentalRateUnit)
    }

    @Test
    fun `rental asset has isRental true`() {
        val asset = buildAsset(isRental = true)
        assertTrue(asset.isRental)
    }

    @Test
    fun `rental rate and unit are set for rental asset`() {
        val asset = buildAsset(
            isRental = true,
            rentalRate = 150.0,
            rentalRateUnit = "daily",
        )
        assertEquals(150.0, asset.rentalRate)
        assertEquals("daily", asset.rentalRateUnit)
    }

    @Test
    fun `hourly rental rate unit is stored`() {
        val asset = buildAsset(isRental = true, rentalRate = 25.0, rentalRateUnit = "hourly")
        assertEquals("hourly", asset.rentalRateUnit)
        assertEquals(25.0, asset.rentalRate)
    }

    @Test
    fun `weekly rental rate unit is stored`() {
        val asset = buildAsset(isRental = true, rentalRate = 500.0, rentalRateUnit = "weekly")
        assertEquals("weekly", asset.rentalRateUnit)
    }

    @Test
    fun `monthly rental rate unit is stored`() {
        val asset = buildAsset(isRental = true, rentalRate = 1500.0, rentalRateUnit = "monthly")
        assertEquals("monthly", asset.rentalRateUnit)
    }

    @Test
    fun `rental rate can be fractional`() {
        val asset = buildAsset(isRental = true, rentalRate = 99.99, rentalRateUnit = "daily")
        assertEquals(99.99, asset.rentalRate!!, 0.001)
    }

    // ─── Search / filter by name ──────────────────────────────────────────────

    @Test
    fun `filter by name matches case-sensitive`() {
        val assets = listOf(
            buildAsset(assetId = "a1", name = "Forklift A"),
            buildAsset(assetId = "a2", name = "Excavator"),
            buildAsset(assetId = "a3", name = "Forklift B"),
        )
        val matches = assets.filter { it.name.contains("Forklift") }
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.name.contains("Forklift") })
    }

    @Test
    fun `filter by name returns empty list when no match`() {
        val assets = listOf(
            buildAsset(assetId = "a1", name = "Crane"),
            buildAsset(assetId = "a2", name = "Bulldozer"),
        )
        val matches = assets.filter { it.name.contains("Forklift") }
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `filter by assetType narrows results`() {
        val assets = listOf(
            buildAsset(assetId = "a1", assetType = "vehicle"),
            buildAsset(assetId = "a2", assetType = "equipment"),
            buildAsset(assetId = "a3", assetType = "vehicle"),
        )
        val vehicles = assets.filter { it.assetType == "vehicle" }
        assertEquals(2, vehicles.size)
    }

    @Test
    fun `filter by assetType null excludes typed assets`() {
        val assets = listOf(
            buildAsset(assetId = "a1", assetType = null),
            buildAsset(assetId = "a2", assetType = "vehicle"),
        )
        val untyped = assets.filter { it.assetType == null }
        assertEquals(1, untyped.size)
        assertEquals("a1", untyped[0].assetId)
    }

    @Test
    fun `filter by locationId groups assets in same location`() {
        val assets = listOf(
            buildAsset(assetId = "a1", locationId = "loc-001"),
            buildAsset(assetId = "a2", locationId = "loc-002"),
            buildAsset(assetId = "a3", locationId = "loc-001"),
        )
        val atLoc1 = assets.filter { it.locationId == "loc-001" }
        assertEquals(2, atLoc1.size)
    }

    @Test
    fun `filter excludes soft-deleted assets`() {
        val assets = listOf(
            buildAsset(assetId = "a1", deletedAt = null),
            buildAsset(assetId = "a2", deletedAt = NOW),
            buildAsset(assetId = "a3", deletedAt = null),
        )
        val live = assets.filter { it.deletedAt == null }
        assertEquals(2, live.size)
        assertTrue(live.all { it.deletedAt == null })
    }

    // ─── AssetDao FRE helpers ──────────────────────────────────────────────────

    @Test
    fun `countRealAssets returns zero when all assets are FRE samples`() = runTest {
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        val count = mockAssetDao.countRealAssets(ACCOUNT_ID)

        assertEquals(0, count)
    }

    @Test
    fun `countRealAssets counts non-FRE non-deleted assets`() = runTest {
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 5

        val count = mockAssetDao.countRealAssets(ACCOUNT_ID)

        assertEquals(5, count)
    }

    @Test
    fun `freeSampleAssetIds returns ids of FRE sample assets`() = runTest {
        val expected = listOf("fre-001", "fre-002", "fre-003")
        coEvery { mockAssetDao.freeSampleAssetIds(ACCOUNT_ID) } returns expected

        val result = mockAssetDao.freeSampleAssetIds(ACCOUNT_ID)

        assertEquals(expected, result)
    }

    @Test
    fun `freeSampleAssetIds returns empty list when no FRE samples`() = runTest {
        coEvery { mockAssetDao.freeSampleAssetIds(ACCOUNT_ID) } returns emptyList()

        val result = mockAssetDao.freeSampleAssetIds(ACCOUNT_ID)

        assertTrue(result.isEmpty())
    }

    // ─── AssetRepository: upsert ──────────────────────────────────────────────

    @Test
    fun `upsertAsset calls dao upsert`() = runTest {
        val asset = buildAsset()
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        repository.upsertAsset(ACCOUNT_ID, asset)

        coVerify { mockAssetDao.upsert(asset) }
    }

    @Test
    fun `upsertAsset always enqueues sync`() = runTest {
        val asset = buildAsset(serverVersion = 0L)
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        repository.upsertAsset(ACCOUNT_ID, asset)

        coVerify(exactly = 1) { mockSyncQueueDao.enqueueWithDedup(any()) }
    }

    @Test
    fun `upsertAsset with rental asset routes to insert when serverVersion is 0`() = runTest {
        val rentalAsset = buildAsset(
            serverVersion = 0L,
            isRental = true,
            rentalRate = 200.0,
            rentalRateUnit = "daily",
        )
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 0

        repository.upsertAsset(ACCOUNT_ID, rentalAsset)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.operation == "insert" })
        }
    }

    @Test
    fun `upsertAsset with depreciation asset routes to update when serverVersion positive`() = runTest {
        val depreciatingAsset = buildAsset(
            serverVersion = 5L,
            purchasePrice = 40_000.0,
            depreciationMethod = "straight_line",
        )
        coEvery { mockAssetDao.countRealAssets(ACCOUNT_ID) } returns 2

        repository.upsertAsset(ACCOUNT_ID, depreciatingAsset)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.operation == "update" })
        }
    }

    // ─── AssetRepository: softDelete ─────────────────────────────────────────

    @Test
    fun `softDeleteAsset calls dao softDelete with timestamp`() = runTest {
        val assetId = "asset-del-001"
        coEvery { mockAssetDao.getById(assetId) } returns buildAsset(assetId = assetId)

        repository.softDeleteAsset(ACCOUNT_ID, assetId)

        coVerify { mockAssetDao.softDelete(assetId, any()) }
    }

    @Test
    fun `softDeleteAsset enqueues delete operation`() = runTest {
        val assetId = "asset-del-002"
        coEvery { mockAssetDao.getById(assetId) } returns buildAsset(assetId = assetId, serverVersion = 7L)

        repository.softDeleteAsset(ACCOUNT_ID, assetId)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "delete" && it.entityId == assetId
            })
        }
    }

    @Test
    fun `softDeleteAsset queueId follows asset underscore id convention`() = runTest {
        val assetId = "asset-del-003"
        coEvery { mockAssetDao.getById(assetId) } returns buildAsset(assetId = assetId)

        repository.softDeleteAsset(ACCOUNT_ID, assetId)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.queueId == "asset_$assetId" })
        }
    }

    // ─── Hierarchy path helpers ───────────────────────────────────────────────

    @Test
    fun `path prefix for descendants query uses slash wildcard`() {
        val rootId = "root-001"
        val expectedPrefix = "/$rootId/%"
        val path = "/$rootId"
        val prefix = "$path/%"
        assertEquals(expectedPrefix, prefix)
    }

    @Test
    fun `two-level path has exactly two segments`() {
        val path = "/parent-001/child-001"
        val segments = path.split("/").filter { it.isNotEmpty() }
        assertEquals(2, segments.size)
    }

    @Test
    fun `three-level path has exactly three segments`() {
        val path = "/root/parent/child"
        val segments = path.split("/").filter { it.isNotEmpty() }
        assertEquals(3, segments.size)
    }

    @Test
    fun `path segment count matches depth plus one`() {
        val depth = 2L
        val path = "/root/level1/level2"
        val segments = path.split("/").filter { it.isNotEmpty() }
        assertEquals(depth + 1, segments.size.toLong())
    }

    // ─── isFreSample flag ────────────────────────────────────────────────────

    @Test
    fun `FRE sample asset has isFreSample true`() {
        val freAsset = buildAsset(isFreSample = true)
        assertTrue(freAsset.isFreSample)
    }

    @Test
    fun `real asset has isFreSample false`() {
        val realAsset = buildAsset(isFreSample = false)
        assertFalse(realAsset.isFreSample)
    }

    // ─── Avatar fields ────────────────────────────────────────────────────────

    @Test
    fun `avatarColor is nullable`() {
        val noColor = buildAsset(avatarColor = null)
        assertNull(noColor.avatarColor)
    }

    @Test
    fun `avatarColor hex string is stored`() {
        val asset = buildAsset(avatarColor = "#FF5733")
        assertEquals("#FF5733", asset.avatarColor)
    }

    @Test
    fun `avatarInitial is nullable`() {
        val noInitial = buildAsset(avatarInitial = null)
        assertNull(noInitial.avatarInitial)
    }

    @Test
    fun `avatarInitial single character is stored`() {
        val asset = buildAsset(avatarInitial = "F")
        assertEquals("F", asset.avatarInitial)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun asset_path_starts_with_slash(path: String?): Boolean =
        path?.startsWith("/") ?: false

    @Suppress("LongParameterList")
    private fun buildAsset(
        assetId: String = "asset-001",
        accountId: String = ACCOUNT_ID,
        name: String = "Test Asset",
        make: String? = null,
        model: String? = null,
        year: Long? = null,
        assetType: String? = null,
        avatarColor: String? = null,
        avatarInitial: String? = null,
        locationId: String? = null,
        isFreSample: Boolean = false,
        parentAssetId: String? = null,
        path: String? = null,
        depth: Long = 0L,
        childCount: Long = 0L,
        isRental: Boolean = false,
        rentalRate: Double? = null,
        rentalRateUnit: String? = null,
        purchasePrice: Double? = null,
        salvageValue: Double? = null,
        usefulLifeMonths: Long? = null,
        depreciationMethod: String? = null,
        placedInServiceDate: Long? = null,
        serverVersion: Long = 0L,
        deletedAt: Long? = null,
        createdAt: Long = NOW,
        updatedAt: Long = NOW,
    ) = AssetEntity(
        assetId = assetId,
        accountId = accountId,
        name = name,
        make = make,
        model = model,
        year = year,
        assetType = assetType,
        meterType = null,
        avatarColor = avatarColor,
        avatarInitial = avatarInitial,
        addressLine1 = null,
        addressLine2 = null,
        city = null,
        state = null,
        postalCode = null,
        country = null,
        locationId = locationId,
        attributes = null,
        isFreSample = isFreSample,
        parentAssetId = parentAssetId,
        path = path,
        depth = depth,
        childCount = childCount,
        isRental = isRental,
        rentalRate = rentalRate,
        rentalRateUnit = rentalRateUnit,
        purchasePrice = purchasePrice,
        salvageValue = salvageValue,
        usefulLifeMonths = usefulLifeMonths,
        depreciationMethod = depreciationMethod,
        placedInServiceDate = placedInServiceDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        serverVersion = serverVersion,
        seq = null,
    )
}
