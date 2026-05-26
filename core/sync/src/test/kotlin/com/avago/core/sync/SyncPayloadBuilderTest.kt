package com.avago.core.sync

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.AssetDao
import com.avago.core.data.db.dao.LogCostLineDao
import com.avago.core.data.db.dao.LogDao
import com.avago.core.data.db.dao.PoLineDao
import com.avago.core.data.db.dao.PurchaseOrderDao
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PoLineEntity
import com.avago.core.data.db.entity.PurchaseOrderEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Unit tests for SyncPayloadBuilder — especially verifying that calendar-date
 * fields (log_date, placed_in_service_date, purchase_date, warranty_end_date) use
 * the noon-UTC date-only format rather than the raw epoch instant, mirroring the
 * iOS SyncPayloadBuilder msToDateOnly() parity requirement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPayloadBuilderTest {

    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockDb = mockk<AvagoDatabase>(relaxed = true)
    private val mockLogDao = mockk<LogDao>()
    private val mockAssetDao = mockk<AssetDao>()
    private val mockLogCostLineDao = mockk<LogCostLineDao>()
    private val mockPurchaseOrderDao = mockk<PurchaseOrderDao>()
    private val mockPoLineDao = mockk<PoLineDao>()

    private lateinit var builder: SyncPayloadBuilder

    private lateinit var originalTz: TimeZone

    companion object {
        private const val ACCOUNT_ID = "acct-test"

        // 2025-01-15T00:00:00Z as epoch millis (midnight UTC on 15 Jan)
        private const val JAN_15_MIDNIGHT_UTC = 1_736_899_200_000L
    }

    @BeforeEach
    fun setUp() {
        // Pin JVM timezone to UTC so msToDateOnly results are deterministic
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        coEvery { mockDbFactory.get(ACCOUNT_ID) } returns mockDb
        every { mockDb.logDao() } returns mockLogDao
        every { mockDb.assetDao() } returns mockAssetDao
        every { mockDb.logCostLineDao() } returns mockLogCostLineDao
        every { mockDb.purchaseOrderDao() } returns mockPurchaseOrderDao
        every { mockDb.poLineDao() } returns mockPoLineDao

        builder = SyncPayloadBuilder(mockDbFactory)
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    // ─── Unknown / missing entities ───────────────────────────────────────────

    @Test
    fun `unknown entity type returns null`() = runTest {
        val result = builder.buildPayload(ACCOUNT_ID, "unicorn", "id-001")
        assertNull(result, "Unknown entity type must return null, not throw")
    }

    @Test
    fun `entity not found in DB returns null`() = runTest {
        coEvery { mockLogDao.getById(any()) } returns null
        val result = builder.buildPayload(ACCOUNT_ID, "log", "missing-log-id")
        assertNull(result)
    }

    @Test
    fun `asset not found in DB returns null`() = runTest {
        coEvery { mockAssetDao.getById(any()) } returns null
        val result = builder.buildPayload(ACCOUNT_ID, "asset", "missing-asset-id")
        assertNull(result)
    }

    // ─── Date-only normalization ──────────────────────────────────────────────

    @Test
    fun `log log_date is noon UTC not raw epoch`() = runTest {
        coEvery { mockLogDao.getById("log-001") } returns buildLogEntity(entryDate = JAN_15_MIDNIGHT_UTC)

        val json = builder.buildPayload(ACCOUNT_ID, "log", "log-001")!!
        val logDate = json["log_date"]?.toString()?.trim('"')

        assertEquals("2025-01-15T12:00:00Z", logDate,
            "log_date must be sent as noon UTC of the local calendar day")
    }

    @Test
    fun `log created_at is raw ISO instant not noon-UTC`() = runTest {
        coEvery { mockLogDao.getById("log-002") } returns buildLogEntity(
            entryDate = JAN_15_MIDNIGHT_UTC,
            createdAt = JAN_15_MIDNIGHT_UTC,
        )

        val json = builder.buildPayload(ACCOUNT_ID, "log", "log-002")!!
        val createdAt = json["created_at"]?.toString()?.trim('"')

        assertEquals("2025-01-15T00:00:00Z", createdAt,
            "created_at must be the raw ISO instant, not pushed to noon")
    }

    @Test
    fun `asset placed_in_service_date is noon UTC when set`() = runTest {
        coEvery { mockAssetDao.getById("asset-001") } returns
            buildAssetEntity(placedInServiceDate = JAN_15_MIDNIGHT_UTC)

        val json = builder.buildPayload(ACCOUNT_ID, "asset", "asset-001")!!
        val serviceDate = json["placed_in_service_date"]?.toString()?.trim('"')

        assertEquals("2025-01-15T12:00:00Z", serviceDate,
            "placed_in_service_date must use noon-UTC format")
    }

    @Test
    fun `asset with null placed_in_service_date omits the field`() = runTest {
        coEvery { mockAssetDao.getById("asset-002") } returns buildAssetEntity(placedInServiceDate = null)

        val json = builder.buildPayload(ACCOUNT_ID, "asset", "asset-002")!!

        assertFalse(json.containsKey("placed_in_service_date"),
            "Null date-only field must not appear in the JSON payload")
    }

    @Test
    fun `noon UTC output always ends with T12_00_00Z suffix`() = runTest {
        coEvery { mockLogDao.getById("log-003") } returns buildLogEntity(entryDate = JAN_15_MIDNIGHT_UTC)

        val json = builder.buildPayload(ACCOUNT_ID, "log", "log-003")!!
        val logDate = json["log_date"]?.toString()?.trim('"') ?: ""

        assertTrue(logDate.endsWith("T12:00:00Z"),
            "date-only fields must always resolve to noon UTC; got: $logDate")
    }

    // ─── Field name correctness ───────────────────────────────────────────────

    @Test
    fun `log payload uses log_id wire name not entry_id`() = runTest {
        coEvery { mockLogDao.getById("log-004") } returns buildLogEntity()

        val json = builder.buildPayload(ACCOUNT_ID, "log", "log-004")!!

        assertTrue(json.containsKey("log_id"), "Wire format must use 'log_id'")
        assertFalse(json.containsKey("entry_id"), "Internal Kotlin property name must NOT appear")
    }

    @Test
    fun `log payload uses meter wire name not odometer_value`() = runTest {
        coEvery { mockLogDao.getById("log-005") } returns
            buildLogEntity().copy(odometerValue = 5000.0)

        val json = builder.buildPayload(ACCOUNT_ID, "log", "log-005")!!

        assertTrue(json.containsKey("meter"), "Wire format must use 'meter'")
        assertFalse(json.containsKey("odometer_value"))
    }

    @Test
    fun `asset payload contains required fields`() = runTest {
        coEvery { mockAssetDao.getById("asset-003") } returns buildAssetEntity()

        val json = builder.buildPayload(ACCOUNT_ID, "asset", "asset-003")!!

        assertTrue(json.containsKey("asset_id"))
        assertTrue(json.containsKey("account_id"))
        assertTrue(json.containsKey("name"))
        assertTrue(json.containsKey("created_at"))
        assertTrue(json.containsKey("updated_at"))
        assertTrue(json.containsKey("is_fre_sample"))
    }

    @Test
    fun `log payload contains required fields`() = runTest {
        coEvery { mockLogDao.getById("log-006") } returns buildLogEntity()

        val json = builder.buildPayload(ACCOUNT_ID, "log", "log-006")!!

        assertTrue(json.containsKey("log_id"))
        assertTrue(json.containsKey("asset_id"))
        assertTrue(json.containsKey("account_id"))
        assertTrue(json.containsKey("title"))
        assertTrue(json.containsKey("log_date"))
        assertTrue(json.containsKey("created_at"))
    }

    @Test
    fun `asset depreciation fields included when set`() = runTest {
        coEvery { mockAssetDao.getById("asset-004") } returns buildAssetEntity(
            placedInServiceDate = JAN_15_MIDNIGHT_UTC,
            purchasePrice = 50_000.0,
            salvageValue = 5_000.0,
            usefulLifeMonths = 60L,
            depreciationMethod = "straight_line",
        )

        val json = builder.buildPayload(ACCOUNT_ID, "asset", "asset-004")!!

        assertTrue(json.containsKey("purchase_price"))
        assertTrue(json.containsKey("salvage_value"))
        assertTrue(json.containsKey("useful_life_months"))
        assertTrue(json.containsKey("depreciation_method"))
        assertTrue(json.containsKey("placed_in_service_date"))
    }

    @Test
    fun `null optional fields are omitted from payload`() = runTest {
        coEvery { mockAssetDao.getById("asset-005") } returns buildAssetEntity()

        val json = builder.buildPayload(ACCOUNT_ID, "asset", "asset-005")!!

        assertFalse(json.containsKey("make"))
        assertFalse(json.containsKey("model"))
        assertFalse(json.containsKey("purchase_price"))
        assertFalse(json.containsKey("depreciation_method"))
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildLogEntity(
        entryDate: Long = JAN_15_MIDNIGHT_UTC,
        createdAt: Long = JAN_15_MIDNIGHT_UTC,
    ) = LogEntity(
        entryId = "log-001",
        assetId = "asset-001",
        accountId = ACCOUNT_ID,
        title = "Oil Change",
        entryDate = entryDate,
        odometerValue = null,
        category = null,
        cost = null,
        performedBy = null,
        performedByUserId = null,
        notes = null,
        data = null,
        attributes = null,
        costMode = null,
        costItems = null,
        costLabor = null,
        costTax = null,
        currency = null,
        baseAmount = null,
        exchangeRateUsed = null,
        configId = null,
        configVersion = null,
        serviceId = null,
        costMisc = null,
        parentId = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )

    // ─── log_cost_line payload ────────────────────────────────────────────────

    @Test
    fun `log_cost_line not found in DB returns null`() = runTest {
        coEvery { mockLogCostLineDao.getById(any()) } returns null
        val result = builder.buildPayload(ACCOUNT_ID, "log_cost_line", "missing-line")
        assertNull(result)
    }

    @Test
    fun `log_cost_line payload contains required fields`() = runTest {
        coEvery { mockLogCostLineDao.getById("line-001") } returns buildLogCostLine()

        val json = builder.buildPayload(ACCOUNT_ID, "log_cost_line", "line-001")!!

        assertTrue(json.containsKey("line_id"))
        assertTrue(json.containsKey("account_id"))
        assertTrue(json.containsKey("log_id"))
        assertTrue(json.containsKey("kind"))
        assertTrue(json.containsKey("quantity"))
        assertTrue(json.containsKey("unit_cost"))
        assertTrue(json.containsKey("display_order"))
        assertTrue(json.containsKey("created_at"))
        assertTrue(json.containsKey("updated_at"))
    }

    @Test
    fun `log_cost_line payload uses wire names not kotlin property names`() = runTest {
        coEvery { mockLogCostLineDao.getById("line-001") } returns buildLogCostLine()

        val json = builder.buildPayload(ACCOUNT_ID, "log_cost_line", "line-001")!!

        assertTrue(json.containsKey("line_id"),     "must use 'line_id' not 'lineId'")
        assertTrue(json.containsKey("log_id"),      "must use 'log_id' not 'logId'")
        assertTrue(json.containsKey("unit_cost"),   "must use 'unit_cost' not 'unitCost'")
        assertTrue(json.containsKey("display_order"))
        assertFalse(json.containsKey("lineId"))
        assertFalse(json.containsKey("logId"))
        assertFalse(json.containsKey("unitCost"))
    }

    @Test
    fun `log_cost_line optional fields omitted when null`() = runTest {
        coEvery { mockLogCostLineDao.getById("line-002") } returns buildLogCostLine(
            inventoryId = null, userId = null, taxAmount = null, glCode = null,
        )

        val json = builder.buildPayload(ACCOUNT_ID, "log_cost_line", "line-002")!!

        assertFalse(json.containsKey("inventory_id"))
        assertFalse(json.containsKey("user_id"))
        assertFalse(json.containsKey("tax_amount"))
        assertFalse(json.containsKey("gl_code"))
    }

    @Test
    fun `log_cost_line optional fields included when set`() = runTest {
        coEvery { mockLogCostLineDao.getById("line-003") } returns buildLogCostLine(
            inventoryId = "inv-001",
            userId = "usr-001",
            taxAmount = 2.50,
            glCode = "5001",
        )

        val json = builder.buildPayload(ACCOUNT_ID, "log_cost_line", "line-003")!!

        assertTrue(json.containsKey("inventory_id"))
        assertTrue(json.containsKey("user_id"))
        assertTrue(json.containsKey("tax_amount"))
        assertTrue(json.containsKey("gl_code"))
    }

    @Test
    fun `log_cost_line kind value is preserved in payload`() = runTest {
        for (kind in listOf("part", "labor", "misc")) {
            coEvery { mockLogCostLineDao.getById("line-$kind") } returns buildLogCostLine().copy(
                lineId = "line-$kind", kind = kind
            )
            val json = builder.buildPayload(ACCOUNT_ID, "log_cost_line", "line-$kind")!!
            assertEquals("\"$kind\"", json["kind"].toString())
        }
    }

    // ─── purchase_order payload ───────────────────────────────────────────────

    @Test
    fun `purchase_order not found in DB returns null`() = runTest {
        coEvery { mockPurchaseOrderDao.getById(any()) } returns null
        val result = builder.buildPayload(ACCOUNT_ID, "purchase_order", "missing-po")
        assertNull(result)
    }

    @Test
    fun `purchase_order payload contains required fields`() = runTest {
        coEvery { mockPurchaseOrderDao.getById("po-001") } returns buildPurchaseOrder()

        val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-001")!!

        assertTrue(json.containsKey("po_id"))
        assertTrue(json.containsKey("account_id"))
        assertTrue(json.containsKey("status"))
        assertTrue(json.containsKey("created_at"))
        assertTrue(json.containsKey("updated_at"))
    }

    @Test
    fun `purchase_order payload uses wire names not kotlin property names`() = runTest {
        coEvery { mockPurchaseOrderDao.getById("po-001") } returns buildPurchaseOrder()

        val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-001")!!

        assertTrue(json.containsKey("po_id"))
        assertFalse(json.containsKey("poId"))
    }

    @Test
    fun `purchase_order optional fields omitted when null`() = runTest {
        coEvery { mockPurchaseOrderDao.getById("po-002") } returns buildPurchaseOrder()

        val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-002")!!

        assertFalse(json.containsKey("po_number"))
        assertFalse(json.containsKey("vendor_id"))
        assertFalse(json.containsKey("subtotal"))
        assertFalse(json.containsKey("tax_total"))
        assertFalse(json.containsKey("grand_total"))
        assertFalse(json.containsKey("notes"))
    }

    @Test
    fun `purchase_order cost fields included when set`() = runTest {
        coEvery { mockPurchaseOrderDao.getById("po-003") } returns buildPurchaseOrder().copy(
            subtotal = 200.0,
            taxTotal = 18.0,
            shippingCost = 12.0,
            discount = 10.0,
            grandTotal = 220.0,
        )

        val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-003")!!

        assertTrue(json.containsKey("subtotal"))
        assertTrue(json.containsKey("tax_total"))
        assertTrue(json.containsKey("shipping_cost"))
        assertTrue(json.containsKey("discount"))
        assertTrue(json.containsKey("grand_total"))
    }

    @Test
    fun `purchase_order status is preserved in payload`() = runTest {
        for (status in listOf("draft", "ordered", "received", "closed")) {
            coEvery { mockPurchaseOrderDao.getById("po-$status") } returns
                buildPurchaseOrder().copy(poId = "po-$status", status = status)
            val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-$status")!!
            assertEquals("\"$status\"", json["status"].toString())
        }
    }

    @Test
    fun `purchase_order vendor_id included when set`() = runTest {
        coEvery { mockPurchaseOrderDao.getById("po-004") } returns buildPurchaseOrder().copy(
            vendorId = "vendor-xyz"
        )

        val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-004")!!
        assertTrue(json.containsKey("vendor_id"))
    }

    @Test
    fun `purchase_order approval fields included when set`() = runTest {
        coEvery { mockPurchaseOrderDao.getById("po-005") } returns buildPurchaseOrder().copy(
            approvedBy = "user-mgr",
            approvedAt = JAN_15_MIDNIGHT_UTC,
            orderedAt = JAN_15_MIDNIGHT_UTC,
        )

        val json = builder.buildPayload(ACCOUNT_ID, "purchase_order", "po-005")!!

        assertTrue(json.containsKey("approved_by"))
        assertTrue(json.containsKey("approved_at"))
        assertTrue(json.containsKey("ordered_at"))
    }

    // ─── po_line payload ──────────────────────────────────────────────────────

    @Test
    fun `po_line not found in DB returns null`() = runTest {
        coEvery { mockPoLineDao.getById(any()) } returns null
        val result = builder.buildPayload(ACCOUNT_ID, "po_line", "missing-line")
        assertNull(result)
    }

    @Test
    fun `po_line payload contains required fields`() = runTest {
        coEvery { mockPoLineDao.getById("pol-001") } returns buildPoLine()

        val json = builder.buildPayload(ACCOUNT_ID, "po_line", "pol-001")!!

        assertTrue(json.containsKey("po_line_id"))
        assertTrue(json.containsKey("po_id"))
        assertTrue(json.containsKey("quantity"))
        assertTrue(json.containsKey("display_order"))
        assertTrue(json.containsKey("created_at"))
        assertTrue(json.containsKey("updated_at"))
    }

    @Test
    fun `po_line uses wire names not kotlin property names`() = runTest {
        coEvery { mockPoLineDao.getById("pol-001") } returns buildPoLine()

        val json = builder.buildPayload(ACCOUNT_ID, "po_line", "pol-001")!!

        assertTrue(json.containsKey("po_line_id"))
        assertTrue(json.containsKey("po_id"))
        assertFalse(json.containsKey("poLineId"))
        assertFalse(json.containsKey("poId"))
    }

    @Test
    fun `po_line optional fields omitted when null`() = runTest {
        coEvery { mockPoLineDao.getById("pol-002") } returns buildPoLine()

        val json = builder.buildPayload(ACCOUNT_ID, "po_line", "pol-002")!!

        assertFalse(json.containsKey("part_id"))
        assertFalse(json.containsKey("description"))
        assertFalse(json.containsKey("unit_cost"))
        assertFalse(json.containsKey("currency"))
        assertFalse(json.containsKey("received_qty"))
    }

    @Test
    fun `po_line unit_cost and part_id included when set`() = runTest {
        coEvery { mockPoLineDao.getById("pol-003") } returns buildPoLine().copy(
            partId = "part-abc",
            unitCost = 15.0,
            receivedQty = 2.0,
        )

        val json = builder.buildPayload(ACCOUNT_ID, "po_line", "pol-003")!!

        assertTrue(json.containsKey("part_id"))
        assertTrue(json.containsKey("unit_cost"))
        assertTrue(json.containsKey("received_qty"))
    }

    // ─── Additional helpers ───────────────────────────────────────────────────

    private fun buildLogCostLine(
        inventoryId: String? = null,
        userId: String? = null,
        taxAmount: Double? = null,
        glCode: String? = null,
    ) = LogCostLineEntity(
        lineId = "line-001",
        accountId = ACCOUNT_ID,
        logId = "log-001",
        kind = "part",
        displayOrder = 0L,
        inventoryId = inventoryId,
        userId = userId,
        description = null,
        quantity = 2.0,
        unitCost = 25.0,
        taxAmount = taxAmount,
        glCode = glCode,
        notes = null,
        woId = null,
        createdAt = JAN_15_MIDNIGHT_UTC,
        updatedAt = JAN_15_MIDNIGHT_UTC,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )

    private fun buildPurchaseOrder() = PurchaseOrderEntity(
        poId = "po-001",
        accountId = ACCOUNT_ID,
        poNumber = null,
        vendorId = null,
        status = "draft",
        currency = null,
        subtotal = null,
        taxTotal = null,
        shippingCost = null,
        discount = null,
        grandTotal = null,
        baseGrandTotal = null,
        exchangeRateUsed = null,
        expectedDelivery = null,
        shipToLocationId = null,
        workOrderId = null,
        assetId = null,
        requestedBy = null,
        approvedBy = null,
        approvedAt = null,
        orderedAt = null,
        closedAt = null,
        notes = null,
        vendorInvoiceNo = null,
        createdBy = null,
        createdAt = JAN_15_MIDNIGHT_UTC,
        updatedAt = JAN_15_MIDNIGHT_UTC,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )

    private fun buildPoLine() = PoLineEntity(
        poLineId = "pol-001",
        poId = "po-001",
        partId = null,
        description = null,
        quantity = 3.0,
        unitCost = null,
        currency = null,
        glCode = null,
        receivedQty = null,
        displayOrder = 0L,
        createdAt = JAN_15_MIDNIGHT_UTC,
        updatedAt = JAN_15_MIDNIGHT_UTC,
        serverVersion = 0L,
    )

    private fun buildAssetEntity(
        placedInServiceDate: Long? = null,
        purchasePrice: Double? = null,
        salvageValue: Double? = null,
        usefulLifeMonths: Long? = null,
        depreciationMethod: String? = null,
    ) = AssetEntity(
        assetId = "asset-001",
        accountId = ACCOUNT_ID,
        name = "Test Truck",
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
        isFreSample = false,
        parentAssetId = null,
        path = null,
        depth = 0L,
        childCount = 0L,
        isRental = false,
        rentalRate = null,
        rentalRateUnit = null,
        purchasePrice = purchasePrice,
        salvageValue = salvageValue,
        usefulLifeMonths = usefulLifeMonths,
        depreciationMethod = depreciationMethod,
        placedInServiceDate = placedInServiceDate,
        createdAt = JAN_15_MIDNIGHT_UTC,
        updatedAt = JAN_15_MIDNIGHT_UTC,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )
}
