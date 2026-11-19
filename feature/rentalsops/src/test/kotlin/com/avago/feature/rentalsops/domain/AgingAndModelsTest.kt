package com.avago.feature.rentalsops.domain

import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalDeliveryTask
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.RentalOrderStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class AgingAndModelsTest {

    @Test
    fun `bucket boundaries match the server`() {
        assertEquals(AgingBucket.CURRENT, AgingBucket.forDaysPastDue(-5))
        assertEquals(AgingBucket.CURRENT, AgingBucket.forDaysPastDue(0))
        assertEquals(AgingBucket.D1_30, AgingBucket.forDaysPastDue(1))
        assertEquals(AgingBucket.D1_30, AgingBucket.forDaysPastDue(30))
        assertEquals(AgingBucket.D31_60, AgingBucket.forDaysPastDue(31))
        assertEquals(AgingBucket.D61_90, AgingBucket.forDaysPastDue(90))
        assertEquals(AgingBucket.OVER_90, AgingBucket.forDaysPastDue(91))
        val asOf = Instant.parse("2026-06-10T00:00:00Z")
        assertEquals(AgingBucket.D1_30, AgingBucket.of(null, Instant.parse("2026-06-01T00:00:00Z"), asOf))
        assertEquals(AgingBucket.CURRENT, AgingBucket.of(null, null, asOf))
    }

    @Test
    fun `aging row accepts stage B and stage C keys and string money`() {
        val b = Json.parseToJsonElement("""{"current":100,"1_30":"250","31_60":0,"61_90":5,"over_90":1,"total":356}""").jsonObject
        val rowB = AgingRow.fromJson("EUR", b)
        assertEquals(250L, rowB[AgingBucket.D1_30])
        assertEquals(356L, rowB.total)
        val c = Json.parseToJsonElement("""{"current":1,"d1_30":2,"d31_60":3,"d61_90":4,"over_90":5}""").jsonObject
        assertEquals(15L, AgingRow.fromJson("EUR", c).total)
    }

    @Test
    fun `minor money parsing`() {
        assertEquals(1234L, MinorMoney.parse(JsonPrimitive(1234)))
        assertEquals(9007199254740993L, MinorMoney.parse(JsonPrimitive("9007199254740993")))
        assertEquals(100L, MinorMoney.parse(JsonPrimitive("100.00")))
        assertNull(MinorMoney.parse(JsonPrimitive("12.5")))
        assertNull(MinorMoney.parse(JsonNull))
        assertNull(MinorMoney.parse(null))
        assertEquals(12.34, MinorMoney.toMajor(1234, "EUR"), 1e-9)
        assertEquals(1234.0, MinorMoney.toMajor(1234, "JPY"), 1e-9)
    }

    private fun task(label: String?, name: String?) = RentalDeliveryTask(
        id = "t1", version = 1, kind = "swap", status = "planned", orderId = "o1", orderNumber = "R-1",
        customerName = "ACME", transferId = null, destinationLocationId = "loc1", destinationLabel = label,
        destinationName = name, latitude = 40.1, longitude = -75.2, contactName = "Kim", contactPhone = "+1 555",
        windowStart = Instant.parse("2026-06-10T06:00:00Z"), windowEnd = null, driverUserId = null, routeSeq = 3,
        notes = null, startedAt = null, arrivedAt = null, completedAt = null, failedReason = null, etaAt = null,
        sync = RecordSyncState.SAVED,
    )

    @Test
    fun `delivery task maps the store row`() {
        val t = DeliveryTask.of(task("1 Main St, Springfield", "North yard"))
        assertEquals(TaskKind.SWAP, t.kind)
        assertEquals(TaskStatus.PLANNED, t.status)
        assertEquals("1 Main St, Springfield", t.address)
        assertEquals("North yard", t.jobSiteName)
        assertEquals("+1 555", t.phone)
        assertEquals(40.1, t.lat)
        assertEquals(Instant.parse("2026-06-10T06:00:00Z"), t.scheduledAt)
        // The label falls back to the location name: no duplicate job-site line.
        assertNull(DeliveryTask.of(task("North yard", "North yard")).jobSiteName)
    }

    private fun order(id: String, status: String) = RentalOrder(
        id = id, version = 1, number = "R-$id", quoteNumber = null, status = status, source = "counter", kind = "rental",
        currency = "EUR", locale = null, customerPartyId = "c1", customerName = "ACME", walkInName = null, contactId = null,
        siteLocationId = null, locationId = "loc1", startAt = Instant.parse("2026-06-01T08:00:00Z"),
        endAt = Instant.parse("2026-06-05T17:00:00Z"), offRentAt = null, holdExpiresAt = null, quoteExpiresAt = null,
        fulfilment = "delivery", poNumber = null, reference = null, notes = null, internalNotes = null,
        subtotalMinor = 0, discountMinor = 0, taxMinor = 0, totalMinor = 0, depositRequiredMinor = 0,
        invoicedMinor = null, invoicePaidMinor = null, cancelReason = null, needsReview = false, sync = RecordSyncState.SAVED,
    )

    private fun line(id: String, orderId: String, classId: String? = "cls", assetId: String? = null, kind: String = "rental", status: String = "open") =
        RentalOrderLine(
            id = id, version = 1, orderId = orderId, lineNo = 1, kind = kind, classId = classId, assetId = assetId,
            productId = null, parentLineId = null, description = "", qty = BigDecimal.ONE, startAt = null, endAt = null,
            actualOutAt = null, actualInAt = null, unitPriceMinor = null, amountMinor = 0, discountMinor = 0, taxMinor = 0,
            status = status, kitInstanceId = null, assignUnitLater = false, sync = RecordSyncState.SAVED,
        )

    @Test
    fun `unassigned lines are open class lines without a unit on live orders`() {
        val orders = listOf(order("o1", RentalOrderStatus.RESERVED), order("o2", "draft"))
        val lines = listOf(
            line("l1", "o1"),
            line("l2", "o1", assetId = "a1"),
            line("l3", "o1", classId = null),
            line("l4", "o1", kind = "accessory"),
            line("l5", "o1", status = "cancelled"),
            line("l6", "o2"),
        )
        val out = UnassignedLine.of(orders, lines, mapOf("cls" to "Mini excavator"))
        assertEquals(listOf("l1"), out.map { it.lineId })
        val l = out.single()
        assertEquals("Mini excavator", l.title)
        assertEquals("R-o1", l.orderNumber)
        assertEquals(1, l.qty)
        // The line has no own window: the order's applies.
        assertEquals(Instant.parse("2026-06-01T08:00:00Z"), l.start)
    }
}
