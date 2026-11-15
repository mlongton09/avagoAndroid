package com.avago.feature.provider.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

class ProviderParsersTest {
    @Test
    fun `dashboard parses lists, counts and falls back to the account currency`() {
        val d = Dashboard.from(
            json(
                """{"today_jobs":[{"id":"j1","number":"J-1","status":"in_progress","title":"Service","scheduled_for":"2026-11-20T09:00:00Z","customer_name":"Ann","asset_name":"Cat 320"},{"number":"no id"}],
                "open_quotes":[{"id":"q1","number":"Q-1","status":"sent","total_minor":12500,"customer_name":"Ann","valid_until":"2026-12-01"}],
                "unpaid_invoices":[{"id":"i1","number":"INV-1","total_minor":9900,"currency":"usd","due_date":"2026-11-30"}],
                "unpaid_total_minor":9900,"unpaid_count":1,"currency":"EUR","customer_count":4,"linked_customer_count":2,
                "assets_serviced":7,"recent_customers":[{"id":"c1","name":"Ann","linked":true}],"reminders_due":3}""",
            ),
        )
        assertEquals(listOf("j1"), d.todayJobs.map { it.id })
        assertEquals("Cat 320", d.todayJobs[0].assetName)
        assertEquals("EUR", d.openQuotes[0].currency)
        assertEquals("2026-12-01", d.openQuotes[0].date)
        assertEquals("usd", d.unpaidInvoices[0].currency)
        assertEquals("2026-11-30", d.unpaidInvoices[0].date)
        assertEquals(9900, d.unpaidTotalMinor)
        assertEquals(3, d.remindersDue)
        assertTrue(d.recentCustomers.single().linked)
    }

    @Test
    fun `customer detail uses the top-level linked flag and parses child documents`() {
        val d = CustomerDetail.from(
            json(
                """{"customer":{"id":"c1","name":"Ann","email":"a@x.io","owner_account_id":null},"linked":true,
                "assets":[{"id":"a1","name":"Boat","service_type":"marine","make":"Yamaha","year":2019,"last_meter":120.5,"meter_unit":"hours"}],
                "connection":{"status":"pending"},
                "jobs":[{"id":"j1","number":"J-1","status":"completed","lines":[]}],
                "quotes":[{"id":"q1","number":"Q-1","status":"draft","total_minor":100,"currency":"USD","lines":[]}],
                "invoices":[]}""",
            ),
        )!!
        assertTrue(d.customer.linked)
        assertEquals("pending", d.connectionStatus)
        assertEquals("2019 Yamaha", d.assets.single().summary)
        assertEquals(120.5, d.assets.single().lastMeter)
        assertEquals("J-1", d.jobs.single().number)
        assertNull(CustomerDetail.from(json("""{"assets":[]}""")))
    }

    @Test
    fun `doc lines accept string or number quantities and compute amounts`() {
        val lines = DocLine.list(
            json(
                """{"lines":[{"kind":"labour","description":"Diag","quantity":"1.5","unit_price_minor":8000,"taxable":true},
                {"kind":"parts","description":"Filter","quantity":2,"unit_price_minor":1250,"taxable":false},
                {"kind":"fee","quantity":1}]}""",
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(12000, lines[0].amountMinor)
        assertEquals(2500, lines[1].amountMinor)
        assertFalse(lines[1].taxable)
    }

    @Test
    fun `job detail fills the customer name and reads photos and totals`() {
        val d = JobDetail.from(
            json(
                """{"job":{"id":"j1","number":"J-7","status":"completed","invoice_id":null,"labour_hours":2,"lines":[]},
                "customer":{"id":"c1","name":"Bob"},"asset":null,
                "photos":[{"photo_id":"p1","sort_order":0,"download_url":"https://x/p1.jpg"},{"sort_order":1}],
                "line_totals":{"subtotal_minor":1000,"tax_minor":0,"total_minor":1000,"labour_minor":1000,"parts_minor":0}}""",
            ),
            "EUR",
        )!!
        assertEquals("Bob", d.job.customerName)
        assertEquals(listOf("p1"), d.photos.map { it.id })
        assertEquals(1000, d.lineTotals!!.totalMinor)
        assertTrue(d.job.canInvoice)
        assertEquals("EUR", d.currency)
    }

    @Test
    fun `job status transitions mirror the backend`() {
        assertEquals(listOf("in_progress", "completed", "cancelled"), JobStatus.next("scheduled"))
        assertEquals(listOf("on_hold", "completed", "cancelled"), JobStatus.next("in_progress"))
        assertEquals(listOf("in_progress", "cancelled"), JobStatus.next("on_hold"))
        assertTrue(JobStatus.next("completed").isEmpty())
        assertTrue(JobStatus.next("cancelled").isEmpty())
    }

    @Test
    fun `invoice and quote flags follow status and totals`() {
        val inv = Invoice.from(json("""{"id":"i1","number":"INV-1","status":"sent","total_minor":0,"lines":[]}"""))!!
        assertFalse(inv.canSend)
        assertTrue(inv.canMarkPaid)
        assertFalse(inv.copy(status = "paid").canMarkPaid)
        val q = Quote.from(json("""{"id":"q1","number":"Q-1","status":"approved","lines":[]}"""))!!
        assertFalse(q.canSend)
        assertTrue(q.copy(status = "draft").canSend)
    }

    @Test
    fun `send result reads link_url or url`() {
        assertEquals(SendResult("https://a", true), SendResult.from(json("""{"link_url":"https://a","emailed":true}""")))
        assertEquals(SendResult("https://b", false), SendResult.from(json("""{"url":"https://b"}""")))
        assertEquals("p9", photoIdFrom(json("""{"photo":{"photo_id":"p9"},"upload_url":"u"}""")))
    }

    @Test
    fun `customer and asset forms validate and drop blank fields`() {
        assertEquals(mapOf("name" to "required", "email" to "email"), CustomerForm(" ", "nope").errors())
        val body = CustomerForm("Ann", "", " 555 ", "").toBody()
        assertEquals(setOf("name", "phone"), body.keys)
        assertEquals("555", body["phone"]!!.jsonPrimitive.content)

        assertEquals(mapOf("year" to "year"), AssetForm(name = "Truck", year = "19").errors())
        val a = AssetForm(name = "Truck", serviceType = "diesel", year = "2018").toBody()
        assertEquals(2018, a["year"]!!.jsonPrimitive.content.toInt())
        assertEquals(setOf("name", "service_type", "year"), a.keys)
    }

    @Test
    fun `status form parses decimals and only sends a unit with a reading`() {
        val bad = StatusForm("completed", labourHours = "abc", meterReading = "-1")
        assertEquals(setOf("labour_hours", "meter_reading"), bad.errors().keys)

        val body = StatusForm("completed", labourHours = "1,5", meterReading = "", meterUnit = "km", notes = " ok ").toBody()
        assertEquals(1.5, body["labour_hours"]!!.jsonPrimitive.content.toDouble())
        assertFalse("meter_unit" in body)
        assertEquals("ok", body["notes"]!!.jsonPrimitive.content)

        val withMeter = StatusForm("completed", meterReading = "1200", meterUnit = "km").toBody()
        assertEquals("km", withMeter["meter_unit"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status form prefills from the job`() {
        val job = Job.from(json("""{"id":"j","number":"J","status":"in_progress","labour_hours":3.0,"meter_reading":812.5,"meter_unit":"mi","lines":[]}"""))!!
        val f = StatusForm.forJob(job, "completed")
        assertEquals("3", f.labourHours)
        assertEquals("812.5", f.meterReading)
        assertEquals("mi", f.meterUnit)
    }
}
