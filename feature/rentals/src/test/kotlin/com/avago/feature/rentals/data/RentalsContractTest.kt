package com.avago.feature.rentals.data

import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.Money
import com.avago.core.network.rentals.RentalsApiException
import com.avago.feature.rentals.domain.LineView
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.OutboxPlanner
import com.avago.feature.rentals.domain.OutboxPlanner.Disposition
import com.avago.feature.rentals.domain.PricingDisplay
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.ScheduleEvent
import com.avago.feature.rentals.domain.ServerAvailability
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.ui.marksOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Contract tests against the stage A Rentals API (AvagoSvc origin/master b9a8e21,
 * docs/rentals-api.md + the serde structs in src/routes/rentals_api). The fixtures under
 * `resources/contract` are the documented examples with concrete ids; `request_fields.json`
 * lists each request struct's fields. Stage B shapes follow the rentals-stage-b serde structs
 * (C:/wt/AvagoSvc-stageb c562904, docs/rentals-api-stage-b.md) — pending on master.
 */
class RentalsContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): JsonObject {
        val text = javaClass.classLoader!!.getResource("contract/$name")!!.readText()
        return json.parseToJsonElement(text).jsonObject
    }

    private val fields by lazy { fixture("request_fields.json") }

    private fun allowed(vararg keys: String): Set<String> =
        keys.flatMap { k -> fields[k]!!.jsonArray.map { it.jsonPrimitive.content } }.toSet()

    private fun assertKeysWithin(body: JsonObject, allowed: Set<String>, what: String) {
        val extra = body.keys - allowed
        assertTrue(extra.isEmpty(), "$what sends fields the server does not accept: $extra")
    }

    private fun assertTimestamp(v: JsonElement?) {
        assertNotNull(v)
        // chrono DateTime<Utc> accepts RFC 3339.
        OffsetDateTime.parse(v!!.jsonPrimitive.content)
    }

    private fun assertMinorNumber(v: JsonElement?) {
        val p = v!!.jsonPrimitive
        assertFalse(p.isString, "minor units are sent as JSON integers (i64)")
        assertNotNull(p.longOrNull)
    }

    private val t0 = Instant.parse("2026-05-04T08:00:00Z")
    private val t1 = Instant.parse("2026-05-07T08:00:00Z")

    private fun booking(currency: String = "EUR") = RentalRepository.BookingInput(
        customerId = "c0000000-0000-4000-8000-000000000001",
        customerName = null,
        classId = "c1a55000-0000-4000-8000-000000000001",
        assetId = "5b0f6c2e-1d3a-4c7e-9f00-000000000012",
        assetName = "EX-12",
        locationId = "10c00000-0000-4000-8000-000000000001",
        currency = currency,
        start = t0,
        end = t1,
        action = "reserve",
        notes = "gate 3",
    )

    // ── Decode: responses ────────────────────────────────────────────────────────────────

    @Nested
    inner class Fleet {
        private val res = fixture("fleet.json")
        private val items = res["items"]!!.jsonArray.map { it.jsonObject }

        @Test
        fun `cards decode into fleet units with the server status`() {
            val r = RentalRecords.record(RentalEntity.FLEET, items[0])!!
            assertEquals("5b0f6c2e-1d3a-4c7e-9f00-000000000012", r.id)
            val u = LocalFleet.unitOf(r)!!
            assertEquals("EX-12", u.name)
            assertEquals("c1a55000-0000-4000-8000-000000000001", u.classId)
            assertEquals("Mini excavator", u.className)
            assertEquals("Europe/Madrid", u.timeZone)
            assertEquals(FleetStatus.ON_RENT, u.serverStatus)
            assertTrue(u.ready)

            val notReady = LocalFleet.unitOf(RentalRecords.record(RentalEntity.FLEET, items[1])!!)!!
            assertFalse(notReady.ready)
            assertEquals(listOf("open_defect", "pm_overdue"), notReady.readiness)
            assertEquals(FleetStatus.NOT_READY, notReady.serverStatus)
        }

        @Test
        fun `asset tombstones are recognised by asset_id`() {
            val t = items[2]
            assertTrue(RentalRecords.isTombstone(t))
            assertEquals("5b0f6c2e-1d3a-4c7e-9f00-000000000099", RentalRecords.idOf(RentalEntity.FLEET, t))
            assertFalse(RentalRecords.isTombstone(items[0]))
        }

        @Test
        fun `every count key is a known status wire value`() {
            val counts = res["counts"]!!.jsonObject
            val statuses = counts.keys - "due_back_today"
            statuses.forEach { assertNotNull(FleetStatus.fromWire(it), "unknown count key $it") }
            assertEquals(FleetStatus.entries.map { it.wire }.toSet(), statuses)
            assertEquals(1440L, res["reserved_window_minutes"]!!.jsonPrimitive.longOrNull)
            assertTimestamp(res["next_cursor"])
        }

        @Test
        fun `server due_at of the current allocation is the due fallback`() {
            val recs = items.take(2).map { RentalRecords.record(RentalEntity.FLEET, it)!! }
            val due = LocalFleet.serverDueAts(recs)
            assertEquals(Instant.parse("2026-05-01T17:00:00Z"), due["a1100000-0000-4000-8000-000000000001"])
        }

        @Test
        fun `without an allocation feed the server status is kept`() {
            val recs = items.take(2).map { RentalRecords.record(RentalEntity.FLEET, it)!! }
            val out = LocalFleet.compute(recs, Instant.parse("2026-05-01T09:00:00Z"))
            assertEquals(FleetStatus.ON_RENT, out["5b0f6c2e-1d3a-4c7e-9f00-000000000012"]!!.status)
            assertEquals(FleetStatus.NOT_READY, out["5b0f6c2e-1d3a-4c7e-9f00-000000000013"]!!.status)
        }
    }

    @Nested
    inner class AvailabilitySearch {
        private val res = ServerAvailability.parse(fixture("availability_search.json"))

        @Test
        fun `classes decode the flat Priced object`() {
            assertEquals("EUR", res.currency)
            assertEquals(2, res.qty)
            val c = res.classes[0]
            assertEquals(5, c.total)
            assertEquals(3, c.free)
            assertTrue(c.enough)
            assertEquals(Money(65340, "EUR"), c.price)
            assertNull(c.priceError)
        }

        @Test
        fun `missing price keeps the price_error`() {
            val c = res.classes[1]
            assertNull(c.price)
            assertEquals("no rate card applies", c.priceError)
            assertFalse(c.enough)
        }

        @Test
        fun `doc-shaped totals with string minor units still decode`() {
            assertEquals(Money(12500, "EUR"), res.classes[2].price)
        }

        @Test
        fun `units decode conflicts and next_free_at`() {
            val busy = res.units.first { it.assetId.endsWith("0012") }
            assertFalse(busy.available)
            assertEquals(1, busy.conflicts)
            assertEquals(Instant.parse("2026-05-09T09:00:00Z"), busy.nextFreeAt)
            val free = res.units.first { it.assetId.endsWith("0014") }
            assertTrue(free.available)
            assertNull(free.nextFreeAt)
        }
    }

    @Test
    fun `schedule events decode`() {
        val events = ScheduleEvent.fromResponse(fixture("schedule.json"))
        assertEquals(2, events.size)
        val ret = events[0]
        assertEquals("return", ret.type)
        assertTrue(ret.overdue)
        assertEquals(Instant.parse("2026-04-30T17:00:00Z"), ret.at)
        assertEquals("R-0041", ret.orderNumber)
        assertEquals("EX-15", ret.assetName)
        val pickup = events[1]
        assertEquals("pickup", pickup.type)
        assertFalse(pickup.overdue)
        assertEquals("Walk-in", pickup.customerName)
    }

    @Nested
    inner class SyncFeed {
        private val page = fixture("sync_customers_page.json")
        private val items = page["items"]!!.jsonArray.map { it.jsonObject }

        @Test
        fun `page envelope uses a seq string cursor`() {
            assertEquals(RentalEntity.CUSTOMER, page["entity"]!!.jsonPrimitive.content)
            assertTrue(page["next_cursor"]!!.jsonPrimitive.isString)
            assertEquals("true", page["has_more"]!!.jsonPrimitive.content)
            assertTimestamp(page["server_time"])
        }

        @Test
        fun `rows are keyed by the feed primary key`() {
            val r = RentalRecords.record(RentalEntity.CUSTOMER, items[0])!!
            assertEquals("c0000000-0000-4000-8000-000000000001", r.id)
            assertEquals(r.id, r.customerId)
            assertEquals(101L, r.seq)
            assertEquals("ACME", r.name)
        }

        @Test
        fun `soft-deleted rows and tombstones are both deletions`() {
            assertFalse(RentalRecords.isTombstone(items[0]))
            assertTrue(RentalRecords.isTombstone(items[1]))
            assertTrue(RentalRecords.isTombstone(items[2]))
            assertEquals("c0000000-0000-4000-8000-000000000003", RentalRecords.idOf(RentalEntity.CUSTOMER, items[2]))
        }

        @Test
        fun `large minor amounts sent as strings decode`() {
            assertEquals(900719925474099300L, items[0].long("credit_limit_minor"))
        }

        @Test
        fun `every feed has a primary key and stage B feeds come last`() {
            assertEquals("rental_invoice_id", RentalEntity.pkOf(RentalEntity.INVOICE))
            assertEquals("asset_id", RentalEntity.pkOf(RentalEntity.ASSET))
            assertEquals("location_id", RentalEntity.pkOf(RentalEntity.LOCATION_SETTING))
            assertTrue(RentalEntity.FEEDS.containsAll(RentalEntity.STAGE_B_FEEDS))
            val firstStageB = RentalEntity.FEEDS.indexOfFirst { it in RentalEntity.STAGE_B_FEEDS }
            assertTrue(RentalEntity.FEEDS.drop(firstStageB).all { it in RentalEntity.STAGE_B_FEEDS })
        }
    }

    @Nested
    inner class Order {
        private val split = RentalRecords.splitOrder(fixture("order.json"))!!

        @Test
        fun `order json splits into header, lines and allocations`() {
            assertEquals("0d000000-0000-4000-8000-000000000042", split.orderId)
            assertFalse(split.order.containsKey("lines"))
            assertFalse(split.order.containsKey("allocations"))
            assertFalse(split.order.containsKey("customer"))
            assertEquals(1, split.lines.size)
            assertEquals(split.orderId, split.lines[0]["order_id"]!!.jsonPrimitive.content)
            assertEquals(split.orderId, split.allocations[0]["order_id"]!!.jsonPrimitive.content)
            assertEquals(
                mapOf("7e57c11e-0000-4000-8000-000000000001" to "11000000-0000-4000-8000-000000000042"),
                split.lineIdsByClientUuid,
            )
        }

        @Test
        fun `order view reads money, version and the embedded customer name`() {
            val v = OrderView.of(RentalRecords.record(RentalEntity.ORDER, split.order)!!)
            assertEquals("R-0042", v.number)
            assertEquals("on_rent", v.status)
            assertEquals("EUR", v.currency)
            assertEquals("ACME", v.customerName)
            assertEquals(Money(65340, "EUR"), v.total)
            assertEquals(Money(45340, "EUR"), v.balance)
            assertEquals(Money(50000, "EUR"), v.depositRequired)
            assertEquals(4, v.version)
        }

        @Test
        fun `padded CHAR(3) currency is trimmed`() {
            val padded = JsonObject(split.order + ("currency" to JsonPrimitive("JPY ")))
            assertEquals("JPY", OrderView.of(RentalRecords.record(RentalEntity.ORDER, padded)!!).currency)
        }

        @Test
        fun `line view reads numeric qty and amounts`() {
            val l = LineView.of(RentalRecords.record(RentalEntity.ORDER_LINE, split.lines[0])!!, "EUR")
            assertEquals(1.0, l.qty)
            assertEquals(Money(54000, "EUR"), l.amount)
            assertEquals("5b0f6c2e-1d3a-4c7e-9f00-000000000012", l.assetId)
        }
    }

    @Test
    fun `Priced decodes into the pricing display`() {
        val priced = json.parseToJsonElement(
            """
            {"currency":"EUR","lines":[{"key":"l1","kind":"rental","description":"Mini excavator","qty":3.0,
              "pricing":{"days":3},"unit_price_minor":18000,"amount_minor":54000,"discount_minor":0,"tax_minor":11340,
              "taxable":true,"tax_lines":[],"price_overridden":false}],
             "subtotal_minor":54000,"discount_minor":0,"tax_minor":11340,"total_minor":65340,
             "tax_summary":[],"tax_exemption":null,"deposit_required_minor":50000}
            """.trimIndent(),
        ).jsonObject
        val d = PricingDisplay.fromServer(priced)!!
        assertEquals("3", d.rows.single().qty)
        assertEquals("3d", d.rows.single().detail)
        assertEquals(Money(65340, "EUR"), d.total)
        assertEquals(Money(50000, "EUR"), d.deposit)
        assertTrue(d.confirmed)
    }

    // ── Errors ───────────────────────────────────────────────────────────────────────────

    @Nested
    inner class Errors {
        private val cases = fixture("errors.json")

        private fun ex(name: String): RentalsApiException {
            val c = cases[name]!!.jsonObject
            return RentalsApiException.from(c["status"]!!.jsonPrimitive.content.toInt(), c["body"].toString())
        }

        private fun disp(name: String, type: String = OutboxPlanner.TYPE_ORDER_ACTION): Disposition {
            val e = ex(name)
            return OutboxPlanner.disposition(type, e.status, e.code, e.message)
        }

        @Test
        fun `coded error bodies expose code and message`() {
            val e = ex("coded_400")
            assertEquals("invalid_value", e.code)
            assertEquals("unknown rental_status 'busy'", e.message)
            val details = ex("permission_denied").body!!["details"]!!.jsonObject
            assertEquals("rental.quote", details["permission"]!!.jsonPrimitive.content)
        }

        @Test
        fun `code-less bodies fall back to the error text`() {
            val nf = ex("not_found")
            assertNull(nf.code)
            assertEquals("Not found", nf.message)
            val vc = ex("version_conflict")
            assertNull(vc.code)
            assertEquals("stale version", vc.message)
            assertTrue(vc.isConflict)
            assertEquals(5L, vc.body!!["current"]!!.jsonObject["server_version"]!!.jsonPrimitive.longOrNull)
        }

        @Test
        fun `allocation conflicts carry the clashing bookings`() {
            val e = ex("allocation_conflict")
            assertEquals("allocation_conflict", e.code)
            val c = (e.body!!["conflicts"] as JsonArray).single().jsonObject
            assertEquals("R-0043", c["order_number"]!!.jsonPrimitive.content)
        }

        @Test
        fun `dispositions follow the error contract`() {
            assertEquals(Disposition.CONFLICT, disp("coded_400"))
            assertEquals(Disposition.CONFLICT, disp("permission_denied"))
            assertEquals(Disposition.CONFLICT, disp("rentals_disabled"))
            assertEquals(Disposition.CONFLICT, disp("allocation_conflict"))
            assertEquals(Disposition.CONFLICT, disp("version_conflict", OutboxPlanner.TYPE_CUSTOMER))
            assertEquals(Disposition.CONFLICT, disp("invalid_transition"))
            // A code-less 404 (route or row missing on this server) is a visible failed item.
            assertEquals(Disposition.FAILED, disp("not_found", OutboxPlanner.TYPE_ORDER_ACTION))
            OutboxPlanner.STAGE_B_TYPES.forEach { assertEquals(Disposition.FAILED, disp("not_found", it)) }
            assertEquals(Disposition.LATER, disp("not_implemented"))
            assertEquals(Disposition.RETRY, disp("idempotency_in_flight"))
            assertEquals(Disposition.RETRY, OutboxPlanner.disposition(OutboxPlanner.TYPE_ORDER, 503, null, null))
            assertEquals(Disposition.UNAUTHORIZED, OutboxPlanner.disposition(OutboxPlanner.TYPE_ORDER, 401, null, null))
        }

        @Test
        fun `non-json bodies still produce an exception`() {
            val e = RentalsApiException.from(502, "<html>bad gateway</html>")
            assertNull(e.code)
            assertEquals("HTTP 502", e.message)
            assertTrue(e.isTransient)
        }
    }

    // ── Stage B responses (rentals-stage-b; pending on master) ──────────────────────────

    @Nested
    inner class StageBResponses {
        private val res = fixture("stage_b_responses.json")
        private fun o(name: String) = res[name]!!.jsonObject

        private fun ex(name: String): RentalsApiException {
            val c = o(name)
            return RentalsApiException.from(c["status"]!!.jsonPrimitive.content.toInt(), c["body"].toString())
        }

        @Test
        fun `handover wrapper decodes handovers, order and warnings`() {
            val resp = o("handover_created")
            val h = resp["handovers"]!!.jsonArray.single().jsonObject
            val rec = RentalRecords.record(RentalEntity.HANDOVER, h)!!
            assertEquals("4a000000-0000-4000-8000-000000000001", rec.id)
            assertEquals("0d000000-0000-4000-8000-000000000001", rec.orderId)
            assertEquals("5b0f6c2e-1d3a-4c7e-9f00-000000000012", rec.assetId)
            assertEquals("checkin", rec.status)
            assertEquals(listOf("cab", "front"), marksOf(rec).map { it.zone })
            assertEquals("returned", resp["order"]!!.jsonObject["status"]!!.jsonPrimitive.content)
            assertEquals(
                "fuel_not_priced: set a fuel_capacity class spec and a per-unit fuel fee to charge refuelling; work_order_created",
                OutboxPlanner.warningsText(resp),
            )
            assertEquals(false, resp["replayed"]!!.jsonPrimitive.content.toBooleanStrict())
        }

        @Test
        fun `ledger wrapper is entry, order, warnings, replayed`() {
            val resp = o("ledger_created")
            assertEquals(setOf("entry", "order", "warnings", "replayed"), resp.keys)
            val rec = RentalRecords.record(RentalEntity.LEDGER, resp["entry"]!!.jsonObject)!!
            assertEquals("1ed00000-0000-4000-8000-000000000001", rec.id)
            assertEquals("payment", rec.status)
            assertEquals(12500L, resp["entry"]!!.jsonObject.long("amount_minor"))
            assertEquals("exceeds_balance: the payment exceeds the balance", OutboxPlanner.warningsText(resp))
            // A customer-only entry has no order (JSON null) and a replay has no warnings.
            val bare = o("ledger_customer_only")
            assertTrue(bare["order"] is kotlinx.serialization.json.JsonNull)
            assertNull(OutboxPlanner.warningsText(bare))
        }

        @Test
        fun `invoice wrapper is invoice, warnings, replayed keyed by rental_invoice_id`() {
            val resp = o("invoice_created")
            assertEquals(setOf("invoice", "warnings", "replayed"), resp.keys)
            val rec = RentalRecords.record(RentalEntity.INVOICE, resp["invoice"]!!.jsonObject)!!
            assertEquals("1a000000-0000-4000-8000-000000000001", rec.id)
            assertEquals("0d000000-0000-4000-8000-000000000001", rec.orderId)
        }

        @Test
        fun `handover conflicts expose who handed the line over`() {
            val e = ex("already_checked_out")
            assertEquals(409, e.status)
            assertEquals("already_checked_out", e.code)
            assertEquals("Sam Lee", e.body!!["details"]!!.jsonObject["by_name"]!!.jsonPrimitive.content)
            assertEquals(Disposition.CONFLICT, OutboxPlanner.disposition(OutboxPlanner.TYPE_HANDOVER, e.status, e.code, e.message))
            assertEquals("reason_required", ex("reason_required").code)
        }

        @Test
        fun `stage B error dispositions`() {
            fun d(name: String, type: String) = ex(name).let { OutboxPlanner.disposition(type, it.status, it.code, it.message) }
            assertEquals(Disposition.RETRY, d("duplicate_client_uuid", OutboxPlanner.TYPE_LEDGER))
            assertEquals(Disposition.FAILED, d("route_missing_404", OutboxPlanner.TYPE_HANDOVER))
            assertEquals(Disposition.FAILED, d("method_not_allowed_405", OutboxPlanner.TYPE_SIGNATURE))
            assertEquals(Disposition.LATER, d("swap_order_level_501", OutboxPlanner.TYPE_ORDER_ACTION))
            assertEquals(Disposition.CONFLICT, d("reason_required", OutboxPlanner.TYPE_HANDOVER))
        }

        @Test
        fun `ledger feed is rental_ledger_entry keyed by id`() {
            val page = o("feed_page")
            assertEquals(RentalEntity.LEDGER, page["entity"]!!.jsonPrimitive.content)
            assertEquals("rental_ledger_entry", RentalEntity.LEDGER)
            assertEquals("id", RentalEntity.pkOf(RentalEntity.LEDGER))
            assertTrue(RentalEntity.LEDGER in RentalEntity.FEEDS)
            val item = page["items"]!!.jsonArray.single().jsonObject
            assertFalse(RentalRecords.isTombstone(item))
            assertEquals(41L, RentalRecords.record(RentalEntity.LEDGER, item)!!.seq)
        }
    }

    // ── Encode: requests ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Requests {

        @Test
        fun `order create matches OrderBody and LineIn`() {
            val line = RentalRequests.rentalLine("c1", "a1", "EX-12", t0, t1, "7e57c11e-0000-4000-8000-000000000001")
            val body = RentalRequests.orderCreate(booking(), line)
            assertKeysWithin(body, allowed("order_create"), "OrderBody")
            assertEquals("staff", body["source"]!!.jsonPrimitive.content)
            assertTimestamp(body["start_at"])
            assertTimestamp(body["end_at"])
            val l = body["lines"]!!.jsonArray.single().jsonObject
            assertKeysWithin(l, allowed("line_in"), "LineIn")
            assertEquals("rental", l["kind"]!!.jsonPrimitive.content)
            assertEquals("7e57c11e-0000-4000-8000-000000000001", l["client_uuid"]!!.jsonPrimitive.content)
            assertFalse(l["qty"]!!.jsonPrimitive.isString, "qty is a JSON number (Decimal serde-float)")
        }

        @Test
        fun `pricing preview matches PreviewBody`() {
            val body = RentalRequests.pricingPreview(booking())
            assertKeysWithin(body, allowed("pricing_preview"), "PreviewBody")
            assertTrue("lines" in body, "PreviewBody.lines is required")
            body["lines"]!!.jsonArray.forEach { assertKeysWithin(it.jsonObject, allowed("line_in"), "LineIn") }
        }

        @Test
        fun `transitions match TransitionBody with an integer version`() {
            val body = RentalRequests.transition("customer asked", t1, t0, JsonPrimitive(4))
            assertKeysWithin(body, allowed("transition"), "TransitionBody")
            assertEquals(4L, body["version"]!!.jsonPrimitive.longOrNull)
            assertTimestamp(body["at"])
            assertFalse("version" in RentalRequests.transition(null, null, t0, null))
        }

        @Test
        fun `customer bodies only use writable columns`() {
            val input = RentalRepository.CustomerInput(
                name = "  ACME ", company = "ACME Ltd", email = "ops@acme.test", phone = "+34 600",
                address = "Calle 1", dateOfBirth = "1980-02-01", notes = "VIP",
                preferredLocale = "es", preferredCurrency = "EUR",
            )
            val create = RentalRequests.customer(input)
            assertKeysWithin(create, allowed("customer"), "customer create")
            assertEquals("ACME", create["name"]!!.jsonPrimitive.content)
            assertFalse("server_version" in create)
            val patch = RentalRequests.customer(input, serverVersion = 3)
            assertKeysWithin(patch, allowed("customer", "generic_ignored"), "customer PATCH")
            assertEquals(3L, patch["server_version"]!!.jsonPrimitive.longOrNull)
        }

        @Test
        fun `documents use alpha-2 issuing countries`() {
            val body = RentalRequests.document(
                RentalRepository.DocumentInput("licence", "D1234", "USA", "CA", "C", "2020-01-01", "2028-01-01"),
            )
            assertKeysWithin(body, allowed("customer_document"), "customer document")
            assertEquals("US", body["issuing_country"]!!.jsonPrimitive.content)
            assertEquals("CA", RentalRequests.countryAlpha2("can"))
            assertEquals("DE", RentalRequests.countryAlpha2("DE"))
            assertNull(RentalRequests.countryAlpha2("Germany"))
            val bad = RentalRequests.document(RentalRepository.DocumentInput("id", null, "XYZW", null, null, null, null))
            assertFalse("issuing_country" in bad)
        }

        @Test
        fun `make rentable matches EnableBody`() {
            val body = RentalRequests.enableAsset(Money(18000, "EUR"), Money(80000, "EUR"), null, Money(50000, "EUR"))
            assertKeysWithin(body, allowed("enable_asset"), "EnableBody")
            assertMinorNumber(body["rate_day_minor"])
            assertEquals("EUR", body["currency"]!!.jsonPrimitive.content)
            assertThrows<IllegalArgumentException> { RentalRequests.enableAsset(Money(0, "EUR")) }
        }

        @Test
        fun `stage B handover matches HandoverBody and DamageIn`() {
            val marks = listOf(
                RentalRepository.HandoverInput.Mark("m1", "front", "minor", "Scratch", isNew = false),
                RentalRepository.HandoverInput.Mark("m2", "cab", "minor", " Cracked mirror ", isNew = true, chargeMinor = 4500),
                RentalRepository.HandoverInput.Mark("m3", "rear", "major", "", isNew = true, reason = "customer goodwill"),
            )
            fun input(direction: String) = RentalRepository.HandoverInput(
                orderId = "o1", orderLineId = "l1", assetId = "a1", direction = direction, meterReading = 1203.50,
                fuelLevel = 140, marks = marks, notes = " dirty ", logId = "log1", photoIds = emptyList(),
                signatureDataUrl = null, signerName = null, occurredAt = t0, zoneId = "Europe/Madrid",
            )
            val checkin = RentalRequests.handover(input("in"), signatureId = "s1")
            assertKeysWithin(checkin, allowed("stage_b_handover"), "HandoverBody")
            listOf("kind", "order_id", "order_line_id", "meter_kind", "damage_summary").forEach {
                assertFalse(it in checkin, "$it is not a HandoverBody field (line is in the path)")
            }
            assertTimestamp(checkin["at"])
            assertEquals("1203.5", checkin["meter_value"]!!.jsonPrimitive.content, "Decimal as a plain string")
            assertEquals(100L, checkin["fuel_level_pct"]!!.jsonPrimitive.longOrNull, "i16 0..100")
            assertEquals("s1", checkin["customer_signature_id"]!!.jsonPrimitive.content)
            assertEquals("log1", checkin["inspection_log_id"]!!.jsonPrimitive.content)
            val dmg = checkin["damage"]!!.jsonArray.map { it.jsonObject }
            dmg.forEach { assertKeysWithin(it, allowed("stage_b_damage"), "DamageIn") }
            assertEquals(listOf("pre_existing", "charge", "waive"), dmg.map { it["decision"]!!.jsonPrimitive.content })
            assertEquals(listOf("front", "cab", "rear"), dmg.map { it["location"]!!.jsonPrimitive.content })
            assertMinorNumber(dmg[1]["charge_minor"])
            assertEquals("Cracked mirror", dmg[1]["label"]!!.jsonPrimitive.content)
            assertEquals("customer goodwill", dmg[2]["reason"]!!.jsonPrimitive.content, "server: waive needs a reason")
            assertFalse("charge_minor" in dmg[2])
            dmg.forEach { assertTrue(it["severity"]!!.jsonPrimitive.content in setOf("minor", "major", "out_of_service")) }

            // Check-out marks are always pre-existing (no charge decision at hand-out).
            val checkout = RentalRequests.handover(input("out"), signatureId = null)
            assertTrue(checkout["damage"]!!.jsonArray.all { it.jsonObject["decision"]!!.jsonPrimitive.content == "pre_existing" })
            assertFalse("customer_signature_id" in checkout)
        }

        @Test
        fun `stage B signature matches SignatureBody`() {
            val sig = RentalRequests.signature(" Ann ", "data:image/png;base64,iVBORw0", t0, deviceId = "dev-1")
            assertKeysWithin(sig, allowed("stage_b_signature"), "SignatureBody")
            assertEquals("iVBORw0", sig["image_png_base64"]!!.jsonPrimitive.content)
            assertFalse("image_png" in sig)
            assertFalse("order_id" in sig, "the order is in the path")
            assertEquals("Ann", sig["signer_name"]!!.jsonPrimitive.content)
            assertEquals("customer", sig["signer_role"]!!.jsonPrimitive.content)
            assertEquals("in_person", sig["method"]!!.jsonPrimitive.content)
            assertTimestamp(sig["signed_at"])
            assertEquals("abc", RentalRequests.signature("x", "abc", t0)["image_png_base64"]!!.jsonPrimitive.content)
        }

        @Test
        fun `stage B ledger and invoice bodies match LedgerBody and DraftBody`() {
            RentalRepository.PaymentInput.KINDS.forEach { kind ->
                val ledger = RentalRequests.ledgerEntry(
                    RentalRepository.PaymentInput(
                        orderId = "o1", kind = kind, method = "card_external", amount = Money(2000, "EUR"),
                        reference = "T-1", cardBrand = "visa", last4 = "4242", payerName = "Ann", payerEmail = null,
                        isAuthorization = false, notes = null, receivedAt = t0,
                    ),
                )
                assertKeysWithin(ledger, allowed("stage_b_ledger"), "LedgerBody")
                assertMinorNumber(ledger["amount_minor"])
                assertTimestamp(ledger["received_at"])
            }
            val invoice = RentalRequests.invoice()
            assertKeysWithin(invoice, allowed("stage_b_invoice"), "DraftBody")
            assertFalse("doc_type" in invoice)
        }

        @Test
        fun `ledger kinds and methods are stage B enum values`() {
            val kinds = setOf(
                "payment", "refund", "deposit_received", "deposit_applied", "deposit_refunded",
                "deposit_forfeited", "credit_note", "write_off", "customer_credit_applied",
            )
            val methods = setOf("cash", "card_external", "bank_transfer", "cheque", "mobile_money", "direct_debit", "voucher", "other")
            assertTrue(kinds.containsAll(RentalRepository.PaymentInput.KINDS))
            assertEquals(methods, RentalRepository.PaymentInput.METHODS.toSet())
        }
    }
}
