package com.avago.feature.rentals.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class Wave2ModelsTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `short codes classify before free text and resolve to a unit or a part`() {
        assertEquals(ScanCode.Kind.ShortCode("U1A2B3C4D"), ScanCode.classify(" u1a2b3c4d "))
        assertEquals(ScanCode.Kind.ShortCode("P00FF00FF"), ScanCode.classify("P00FF00FF"))
        // Too short / not hex: plain text, and orders still win.
        assertTrue(ScanCode.classify("U12") is ScanCode.Kind.Text)
        assertTrue(ScanCode.classify("R-1042") is ScanCode.Kind.OrderNumber)

        val a = ScanResolution.parse(
            obj("""{"type":"asset","code":"U1","asset":{"asset_id":"a1","name":"Lift","current":{"order_id":"o1","order_number":"R-1"}}}"""),
        )!!
        assertTrue(a.isAsset)
        assertFalse(a.isPart)
        assertEquals("o1", a.currentOrderId)
        val p = ScanResolution.parse(obj("""{"part":{"id":"p1","name":"Cone","part_number":"C-1"}}"""))!!
        assertEquals("part", p.type)
        assertTrue(p.isPart)
        assertNull(ScanResolution.parse(obj("""{"code":"U1"}""")))
        assertNull(ScanResolution.parse(null))
    }

    @Test
    fun `kits group components under their header and keep standalone lines in order`() {
        data class L(val id: String, val kit: String?, val header: Boolean = false)
        val lines = listOf(L("x", null), L("c1", "k"), L("h", "k", header = true), L("y", null), L("c2", "k"), L("orphan", "k2"))
        val g = Kits.group(lines, { it.kit }, { it.header })
        assertEquals(4, g.size)
        assertEquals(listOf("x"), g[0].components.map { it.id })
        assertEquals(listOf("y"), g[2].components.map { it.id })
        val kit = g[1]
        assertEquals("h", kit.header!!.id)
        assertEquals(listOf("c1", "c2"), kit.components.map { it.id })
        // Headless kit still grouped.
        assertNull(g[3].header)
        assertEquals(listOf("orphan"), g[3].components.map { it.id })
        assertFalse(Kits.isHandoverLine("kit"))
        assertTrue(Kits.isHandoverLine("rental"))
    }

    @Test
    fun `bulk outstanding and count checks mirror the server`() {
        val returns = listOf(obj("""{"returned_qty":"3","damaged_qty":"1"}"""), obj("""{"lost_qty":"0.5"}"""))
        assertEquals(BigDecimal("5.5"), Bulk.outstanding(BigDecimal("10"), returns))
        assertEquals(BigDecimal.ZERO, Bulk.outstanding(BigDecimal("2"), returns))
        fun c(r: String, d: String = "0", l: String = "0") = Bulk.Counts(BigDecimal(r), BigDecimal(d), BigDecimal(l))
        assertEquals(Bulk.Check.OK, Bulk.check(c("5", "0.5"), BigDecimal("5.5")))
        assertEquals(Bulk.Check.EXCEEDS, Bulk.check(c("5", "1"), BigDecimal("5.5")))
        assertEquals(Bulk.Check.EMPTY, Bulk.check(c("0"), BigDecimal("5")))
        assertEquals(Bulk.Check.NEGATIVE, Bulk.check(c("-1", "2"), BigDecimal("5")))
        assertEquals(BigDecimal("1.5"), Bulk.parseQty(" 1,5 "))
        assertEquals(BigDecimal("0"), Bulk.parseQty(""))
        assertNull(Bulk.parseQty("abc"))
        val body = Bulk.returnBody(c("2.0"), 0, "  ", Instant.parse("2026-06-10T08:00:00Z"), null)
        assertEquals("2", body["returned"]!!.jsonPrimitive.content)
        assertNull(body["damage_charge_minor"])
        assertNull(body["notes"])
        assertNull(body["location_id"])
        assertTrue(Bulk.isBulk(null, "bulk_rental", "l1", emptySet()))
        assertTrue(Bulk.isBulk("accessory", null, "l1", setOf("l1")))
        assertFalse(Bulk.isBulk("accessory", null, "l1", setOf("l2")))
    }

    @Test
    fun `kpi summary tolerates missing sections and computes trends`() {
        val k = KpiSummary.parse(
            obj(
                """{"days":30,"currency":"EUR","current":{"time_utilization_bp":6250,"revenue_minor":120000,"checkouts":12},
                   "previous":{"revenue_minor":100000},"fleet":{"on_rent":4,"units":10},"ar":{"overdue_minor":500}}""",
            ),
        )!!
        assertEquals(6250L, k.utilizationBp)
        assertNull(k.prevUtilizationBp)
        assertEquals(4L, k.unitsOnRent)
        assertFalse(k.isEmpty)
        assertEquals(20, KpiSummary.trendPercent(k.revenueMinor, k.prevRevenueMinor))
        assertNull(KpiSummary.trendPercent(5, 0))
        assertNull(KpiSummary.trendPercent(null, 5))
        assertEquals("62.5 %", KpiSummary.percent(6250))
        assertTrue(KpiSummary.parse(obj("{}"))!!.isEmpty)
    }

    @Test
    fun `online payments are off unless the server says enabled`() {
        assertFalse(OnlinePayments.enabled(null))
        assertFalse(OnlinePayments.enabled(obj("""{}""")))
        assertTrue(OnlinePayments.enabled(obj("""{"online_payments_enabled":true}""")))
        val off = OnlinePayments.parseOrder(obj("""{"online_payments_enabled":false,"can":{"request":true}}"""), "EUR")!!
        assertFalse(off.canRequest)
        val on = OnlinePayments.parseOrder(
            obj(
                """{"online_payments_enabled":true,"payment_requests":[
                   {"id":"r1","status":"open","amount_minor":500,"pay_url":"https://pay/x"},
                   {"id":"r2","status":"open","link_available":false,"pay_url":"https://pay/y"},
                   {"status":"open"}]}""",
            ),
            "EUR",
        )!!
        assertTrue(on.canRequest)
        assertEquals(listOf("r1", "r2"), on.requests.map { it.id })
        assertTrue(on.requests[0].shareable)
        assertEquals(500L, on.requests[0].amount!!.minor)
        assertFalse(on.requests[1].shareable)
    }

    @Test
    fun `credit check parses blocks and missing documents`() {
        val c = CreditCheck.parse(
            obj(
                """{"allowed":false,"can_override":true,"account_status":"hold","blocks":[
                   {"code":"credit_limit_exceeded","message":"Over limit"},{"code":"document_missing","kind":"documents"},{"message":"no code"}],
                   "documents":[{"kind":"id","status":"valid"},{"kind":"coi","status":"expired","expires_on":"2026-01-01"},{"kind":"licence"}],
                   "exposure":{"available_minor":1000,"after_order_minor":-500}}""",
            ),
        )!!
        assertFalse(c.allowed)
        assertTrue(c.canOverride)
        assertEquals(listOf("credit_limit_exceeded", "document_missing"), c.blocks.map { it.code })
        assertEquals("credit", c.blocks[0].kind)
        assertTrue(c.hasDocumentBlock)
        assertEquals(listOf("coi", "licence"), c.missingDocuments.map { it.kind })
        assertEquals(-500L, c.afterOrderMinor)
        // Absent `allowed` never blocks; absent can_override never allows an override.
        val open = CreditCheck.parse(obj("{}"))!!
        assertTrue(open.allowed)
        assertFalse(open.canOverride)
    }

    @Test
    fun `contracts and sub-rent flags`() {
        val c = ContractView.of(obj("""{"id":"c1","kind":"rent_to_own","monthly_rate_minor":9900,"currency":"EUR"}"""))!!
        assertTrue(c.isRentToOwn)
        assertEquals("active", c.status)
        assertEquals(9900L, c.monthlyRate!!.minor)
        assertNull(ContractView.of(obj("""{"kind":"long_term"}""")))
        assertTrue(SubRent.isSubrented(obj("""{"ownership":"subrented"}""")))
        assertFalse(SubRent.isSubrented(obj("""{"ownership":"owned"}""")))
        assertFalse(SubRent.isSubrented(null))
    }
}
