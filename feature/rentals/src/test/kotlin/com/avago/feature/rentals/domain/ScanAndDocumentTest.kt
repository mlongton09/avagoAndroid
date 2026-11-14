package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class ScanAndDocumentTest {

    @Test
    fun `scan codes are classified`() {
        val id = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"
        assertEquals(ScanCode.Kind.AssetId(id.lowercase()), ScanCode.classify("https://app.avago.ai/a/$id"))
        assertEquals(ScanCode.Kind.AssetId(id.lowercase()), ScanCode.classify("  $id "))
        assertEquals(ScanCode.Kind.OrderNumber("RO-1042"), ScanCode.classify("ro-1042"))
        assertEquals(ScanCode.Kind.Text("R-12"), ScanCode.classify("R-12"))
        assertTrue(ScanCode.classify("@\n\u001e\rANSI 636014080102DL00410288ZC03290034DLDAQD1234567\n") is ScanCode.Kind.Licence)
    }

    private val us = "@\n\u001e\rANSI 636014080102DL00410288ZC03290034DLDAQD1234567\n" +
        "DCSSMITH\nDACJOHN\nDADNONE\nDBB01311990\nDBA02282030\nDBD03012022\n" +
        "DAG123 MAIN ST\nDAISACRAMENTO\nDAJCA\nDAK958220000  \nDCGUSA\nDCAC\n"

    @Test
    fun `AAMVA licence is parsed with US date order and ZIP normalisation`() {
        val l = Aamva.parse(us)!!
        assertEquals("D1234567", l.number)
        assertEquals("JOHN", l.firstName)
        assertNull(l.middleName, "NONE is treated as empty")
        assertEquals("SMITH", l.lastName)
        assertEquals("JOHN SMITH", l.fullName)
        assertEquals(LocalDate.of(1990, 1, 31), l.dateOfBirth)
        assertEquals(LocalDate.of(2030, 2, 28), l.expiresOn)
        assertEquals("95822", l.postalCode)
        assertEquals("123 MAIN ST, SACRAMENTO CA 95822, USA", l.address)
        assertNull(Aamva.parse("not a licence"))
        assertEquals(LocalDate.of(1990, 1, 31), Aamva.parseDate("19900131", canada = true))
    }

    @Test
    fun `asset profit sums same currency revenue minus costs`() {
        val r = AssetProfit.compute(
            "eur",
            listOf(
                AssetProfit.Revenue("closed", Money(100_00, "EUR")),
                AssetProfit.Revenue("cancelled", Money(999_00, "EUR")),
                AssetProfit.Revenue("draft", Money(999_00, "EUR")),
                AssetProfit.Revenue("on_rent", Money(50_00, "USD")),
                AssetProfit.Revenue("reserved", null),
            ),
            listOf(AssetProfit.Cost(12.345, "EUR"), AssetProfit.Cost(5.0, null), AssetProfit.Cost(1.0, "GBP")),
        )
        assertEquals(Money(100_00, "EUR"), r.revenue)
        assertEquals(Money(17_35, "EUR"), r.costs)
        assertEquals(Money(82_65, "EUR"), r.profit)
        assertEquals(82, r.marginPct)
        assertEquals(2, r.skipped)
        assertEquals(Money(1235, "JPY"), AssetProfit.toMinor(1234.5, "JPY"))
        assertNull(AssetProfit.compute("EUR", emptyList(), emptyList()).marginPct)
    }

    private fun rec(entity: String, id: String, json: String) = RentalRecordEntity(entity = entity, id = id, json = json)

    @Test
    fun `local agenda lists pickups, returns and overdue returns on today`() {
        val now = Instant.parse("2026-05-01T10:00:00Z")
        val from = Instant.parse("2026-05-01T00:00:00Z")
        val to = Instant.parse("2026-05-02T00:00:00Z")
        val records = listOf(
            rec(LocalFleet.ENTITY_FLEET, "A", """{"asset_id":"A","name":"Alpha","rental_status":"available"}"""),
            rec(LocalFleet.ENTITY_FLEET, "B", """{"asset_id":"B","name":"Bravo","rental_status":"on_rent"}"""),
            rec(LocalFleet.ENTITY_ORDER, "O1", """{"id":"O1","number":"RO-1","customer":{"name":"Ada"}}"""),
            rec(
                LocalFleet.ENTITY_ALLOCATION, "1",
                """{"asset_id":"A","kind":"reserved","order_id":"O1","start_at":"2026-05-01T14:00:00Z","end_at":"2026-05-03T08:00:00Z"}""",
            ),
            rec(
                LocalFleet.ENTITY_ALLOCATION, "2",
                """{"asset_id":"B","kind":"on_rent","start_at":"2026-04-28T08:00:00Z","end_at":"2026-04-30T08:00:00Z"}""",
            ),
            rec(
                LocalFleet.ENTITY_ALLOCATION, "3",
                """{"asset_id":"B","kind":"on_rent","active":false,"start_at":"2026-04-28T08:00:00Z","end_at":"2026-05-01T12:00:00Z"}""",
            ),
        )
        val ev = LocalSchedule.events(records, from, to, now)
        assertEquals(listOf("return" to "B", "pickup" to "A"), ev.map { it.type to it.assetId })
        assertTrue(ev[0].overdue)
        assertEquals("RO-1", ev[1].orderNumber)
        assertEquals("Ada", ev[1].customerName)

        val busy = LocalSchedule.busy(records)
        assertEquals(2, busy.size, "inactive allocations are not busy")
        val free = Availability.unit("B", busy, now, now.plusSeconds(3600), now)
        assertTrue(!free.available, "an overdue unit stays busy")
    }
}
