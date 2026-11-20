package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Certification
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerDraft
import com.avago.core.data.domain.WorkerRate
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class WorkerCommandBuildersTest {

    private val today = LocalDate.of(2025, 6, 2)
    private val rate = WorkerRate(id = "r1", costRate = BigDecimal("40.00"), currency = "EUR", validFrom = LocalDate.of(2025, 1, 1), version = 2)
    private val worker = Worker(
        id = "w1", userId = "u1", displayName = "Ann", kind = "employee", role = null, status = "active", skills = listOf("hvac"),
        maxActiveWorkOrders = null, available = true, homeLocationId = null, notes = null, version = 9,
        certifications = listOf(Certification("EPA 608")), hourlyRate = rate, attributes = """{"shirt":"L"}""",
    )

    private fun draft() = WorkerDraft(
        displayName = worker.displayName, skills = worker.skills, maxActiveWorkOrders = null, available = true,
        homeLocationId = null, certifications = worker.certifications,
    )

    private fun build(d: WorkerDraft, w: Worker = worker) = MaintCommands.updateWorker(w, d, 10, today, "r-new", "u1")

    @Test
    fun `an unchanged profile sends nothing`() {
        assertNull(build(draft()))
        assertNull(build(draft().copy(hourlyRate = BigDecimal("40"), rateCurrency = "eur")))
    }

    @Test
    fun `certifications are sent and merged into the attributes overlay`() {
        val certs = listOf(Certification(" CDL ", issuer = "DMV", expiresOn = LocalDate.of(2026, 3, 1)))
        val command = build(draft().copy(certifications = certs))!!
        val sent = command.payload.jsonObject["set"]!!.jsonObject["certifications"]!!.jsonArray.single().jsonObject
        assertEquals("CDL", sent["name"]!!.jsonPrimitive.content)
        assertEquals("2026-03-01", sent["expires_on"]!!.jsonPrimitive.content)
        val op = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single()
        val attributes = Json.parseToJsonElement(op.set["attributes"] as String).jsonObject
        assertEquals("L", attributes["shirt"]!!.jsonPrimitive.content)
        assertEquals("DMV", attributes["certifications"]!!.jsonArray.single().jsonObject["issuer"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a new rate closes the current row and starts one today`() {
        val command = build(draft().copy(hourlyRate = BigDecimal("45.5")))!!
        assertEquals("45.5", command.payload.jsonObject["set"]!!.jsonObject["hourly_rate"]!!.jsonPrimitive.content)
        val ops = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.filter { it.table == "worker_rates" }
        assertEquals(listOf("r1", "r-new"), ops.map { it.key["id"] })
        assertEquals("2025-06-02", ops[0].set["valid_during_end"])
        assertEquals("2025-06-02", ops[1].set["valid_during_start"])
        assertEquals("EUR", ops[1].set["currency"])
        assertEquals("45.5", ops[1].set["cost_rate"])
    }

    @Test
    fun `a same-day rate change edits today's row`() {
        val w = worker.copy(hourlyRate = rate.copy(validFrom = today))
        val command = build(draft().copy(rateCurrency = "USD"), w)!!
        val op = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single { it.table == "worker_rates" }
        assertEquals("r1", op.key["id"])
        assertEquals("USD", op.set["currency"])
        assertEquals("40.00", op.set["cost_rate"])
    }

    @Test
    fun `a negative rate or a malformed currency is refused`() {
        assertThrows(IllegalArgumentException::class.java) { build(draft().copy(hourlyRate = BigDecimal("-1"))) }
        assertThrows(IllegalArgumentException::class.java) { build(draft().copy(rateCurrency = "EURO")) }
    }
}
