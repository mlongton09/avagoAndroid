package com.avago.feature.rentalsops.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
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

    @Test
    fun `delivery task parses stage C row`() {
        val o = Json.parseToJsonElement(
            """{"id":"t1","kind":"pickup","status":"en_route","asset_ids":["a","b"],
               "address":{"line1":"1 Main St","city":"Springfield","country":"US"},
               "lat":40.1,"lng":-75.2,"proof_photo_file_ids":["f1"],"proof_signature_file_id":null,
               "scheduled_at":"2026-06-10T08:00:00+02:00","route_seq":3}""",
        ).jsonObject
        val t = DeliveryTask.from(o)!!
        assertEquals(TaskKind.PICKUP, t.kind)
        assertEquals(TaskStatus.EN_ROUTE, t.status)
        assertEquals(listOf("a", "b"), t.assetIds)
        assertEquals("1 Main St, Springfield, US", t.address)
        assertEquals(1, t.proofPhotoCount)
        assertEquals(false, t.hasProofSignature)
        assertEquals(Instant.parse("2026-06-10T06:00:00Z"), t.scheduledAt)
        assertEquals(3, t.routeSeq)
    }

    @Test
    fun `unassigned line requires line and order ids`() {
        assertNull(UnassignedLine.from(Json.parseToJsonElement("""{"order_id":"o1"}""").jsonObject))
        assertNull(UnassignedLine.from(Json.parseToJsonElement("""{"line_id":"l1"}""").jsonObject))
        val l = UnassignedLine.from(Json.parseToJsonElement("""{"line_id":"l1","order_id":"o1","description":"Lift","order_version":null}""").jsonObject)!!
        assertEquals("Lift", l.title)
        assertNull(l.orderVersion)
    }

    @Test
    fun `task detail fields - assets, job site and phone fallback`() {
        val o = Json.parseToJsonElement(
            """{"id":"t1","kind":"delivery","status":"scheduled","order_status":"reserved",
               "customer_display_name":"ACME Ltd","customer_name":"acme","customer_phone":"+1 555",
               "contact_phone":null,"job_site_name":"North yard",
               "assets":[{"asset_id":"a1","name":"Lift 1","serial_number":"SN1"},{"asset_id":"a2"}]}""",
        ).jsonObject
        val t = DeliveryTask.from(o)!!
        assertEquals("ACME Ltd", t.customerName)
        assertEquals("+1 555", t.phone)
        assertEquals("North yard", t.jobSiteName)
        assertEquals("reserved", t.orderStatus)
        assertEquals(listOf(TaskAsset("a1", "Lift 1", "SN1"), TaskAsset("a2", null, null)), t.assets)
    }
}
