package com.avago.feature.rentals.domain

import com.avago.core.data.domain.HandoverOverride
import com.avago.core.data.domain.RentalFailure
import com.avago.core.data.domain.RentalSignature
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/** Wave 2 G1 (e-sign / meter) + G2 (credit gate) client logic. */
class Wave2GatesTest {

    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val statusJson = json(
        """
        {"agreement_id":"ag1","agreement_signed":false,"require_signed_agreement_before_checkout":true,
         "items":[{"id":"r1","status":"pending","purpose":"agreement","consent_text_version":"v2",
           "signers":[
             {"id":"s2","signer_order":2,"signer_name":"Rep","signer_role":"staff","method":"in_person","status":"pending"},
             {"id":"s1","signer_order":1,"signer_name":"Ann","signer_role":"customer","method":"in_person","status":"pending"},
             {"id":"s3","signer_order":1,"signer_name":"Bob","signer_role":"customer","method":"remote","status":"pending"}
           ]}]}
        """,
    )

    @Nested
    inner class EsignTests {
        @Test
        fun `only in-person signers of the lowest open order are capturable`() {
            val s = Esign.parse(statusJson)!!
            assertTrue(s.blocksCheckout)
            assertEquals(listOf("s1"), s.signable!!.capturable.map { it.id })
        }

        private fun sig(requestId: String?) = RentalSignature(
            id = "g-$requestId", orderId = "o1", documentId = null, signerName = "Ann", signerRole = "customer",
            method = "in_person", signedAt = Instant.parse("2025-01-02T03:04:05Z"), requestId = requestId,
            signerId = "s1", handoverId = null,
        )

        @Test
        fun `local capture marks signer and moves to next order`() {
            val s = Esign.withLocal(Esign.parse(statusJson)!!, listOf(sig("r1")))
            val r = s.requests.single()
            assertEquals(Esign.STATUS_SIGNED_LOCAL, r.signers.first { it.id == "s1" }.status)
            // remote signer s3 still open at order 1 → no in-person signer capturable yet
            assertNull(s.signable)
            assertFalse(s.agreementSigned)
        }

        @Test
        fun `a request opened and signed on this device signs the agreement`() {
            val s = Esign.withLocal(Esign.parse(statusJson)!!, listOf(sig("r-new")))
            assertTrue(s.agreementSigned)
            assertFalse(s.blocksCheckout)
            assertEquals(Esign.parse(statusJson)!!.requests, s.requests)
        }

        @Test
        fun `validate mirrors server 400 codes`() {
            assertEquals(Esign.Capture.ConsentRequired, Esign.validate("data:image/png;base64,AAAA", null, consent = false))
            assertEquals(Esign.Capture.SignatureRequired, Esign.validate(null, " ", consent = true))
            assertEquals(Esign.Capture.Ok, Esign.validate(null, "Ann Smith", consent = true))
            val big = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(ByteArray(Esign.MAX_PNG_BYTES + 1))
            assertEquals(Esign.Capture.ImageTooLarge, Esign.validate(big, null, consent = true))
        }

        @Test
        fun `png bytes accept a data url or plain base64`() {
            assertEquals("ABC", String(Esign.pngBytes("data:image/png;base64,QUJD")!!))
            assertEquals("ABC", String(Esign.pngBytes("QUJD")!!))
            assertNull(Esign.pngBytes("data:image/png;base64,***"))
        }
    }

    @Nested
    inner class MeterTests {
        private val info = MeterInfo.parse(
            json("""{"meter":{"kind":"hours","requirement":"required","unit":"hours"},"items":[{"value":"120.5","source":"checkout"}]}"""),
        )!!

        @Test
        fun `required meter and validation`() {
            assertTrue(info.required)
            assertEquals(MeterInfo.Check.REQUIRED, info.check(" ", "in"))
            assertEquals(MeterInfo.Check.INVALID, info.check("-1", "in"))
            assertEquals(MeterInfo.Check.INVALID, info.check("abc", "in"))
            assertEquals(MeterInfo.Check.OK, info.check("120,5", "in"))
        }

        @Test
        fun `check-in below checkout needs override, check-out below last is only a warning`() {
            assertEquals(MeterInfo.Check.BELOW_CHECKOUT, info.check("100", "in"))
            assertEquals(MeterInfo.Check.BELOW_PREVIOUS, info.check("100", "out"))
        }

        @Test
        fun `no meter section means no meter`() {
            assertNull(MeterInfo.parse(json("""{"items":[]}""")))
            assertFalse(MeterInfo.parse(json("""{"meter":{"requirement":"none"}}"""))!!.hasMeter)
        }
    }

    @Nested
    inner class GateTests {
        private fun failure(code: String, op: String = RentalFailure.OP_CHECKOUT) =
            RentalFailure("c1", op, "o1", "l1", code, "Sign first", 0L)

        @Test
        fun `a refused hand-over gate carries its override permission`() {
            val g = HandoverGates.of(failure("signature_required"))!!
            assertEquals("signature_required", g.code)
            assertEquals(Esign.PERMISSION_OVERRIDE, g.permission)
            assertEquals("Sign first", g.message)
            assertEquals(HandoverGates.PERMISSION_CREDIT_OVERRIDE, HandoverGates.of(failure("customer_on_hold"))!!.permission)
            assertNull(HandoverGates.of(failure("version_conflict")))
            assertNull(HandoverGates.of(failure("signature_required", "rental.rental_orders.reserve")))
        }

        @Test
        fun `each gate maps to its override and incomplete fixes are rejected`() {
            fun gate(code: String) = HandoverGates.of(failure(code))!!
            assertEquals(HandoverOverride.Signature, HandoverGates.override(gate("signature_required"), null, null))
            assertEquals(HandoverOverride.Credit("Manager OK"), HandoverGates.override(gate("customer_blocked"), " Manager OK ", null))
            assertNull(HandoverGates.override(gate("customer_blocked"), "  ", null))
            assertEquals(HandoverOverride.Meter("Meter replaced"), HandoverGates.override(gate("meter_below_checkout"), "Meter replaced", null))
            assertEquals(HandoverOverride.Ready("checked"), HandoverGates.override(gate("unit_not_ready"), "checked", null))
            assertNull(HandoverGates.override(gate("meter_required"), null, "-3"))
            assertEquals(HandoverOverride.Reading(BigDecimal("12.50")), HandoverGates.override(gate("meter_required"), null, "12,50"))
        }
    }

    @Nested
    inner class ChecksTests {
        private val meter = MeterInfo("hours", "required", "hours", null, BigDecimal("50"), "checkout")
        private val credit = CreditCheck.parse(
            json(
                """{"allowed":false,"can_override":true,"account_status":"on_hold",
                   "blocks":[{"code":"customer_on_hold","message":"x","kind":"credit"},{"code":"required_documents_missing","kind":"documents"}],
                   "documents":[{"kind":"coi","status":"missing"},{"kind":"id","status":"valid"}]}""",
            ),
        )!!
        private val base = HandoverChecks.Input(
            direction = "out", meter = meter, meterText = "60", meterOverrideReason = "", canOverrideMeter = false,
            esign = Esign.parse(statusJson), overrideSignature = false,
            credit = credit, creditOverrideReason = "", canOverrideCredit = true,
        )

        @Test
        fun `checkout lists signature and credit blockers`() {
            assertEquals(listOf(HandoverChecks.Blocker.SIGNATURE, HandoverChecks.Blocker.CREDIT_NEEDS_REASON), HandoverChecks.blockers(base))
            assertTrue(HandoverChecks.blockers(base.copy(overrideSignature = true, creditOverrideReason = "ok")).isEmpty())
            assertEquals(listOf("coi"), credit.missingDocuments.map { it.kind })
            assertTrue(credit.hasDocumentBlock)
        }

        @Test
        fun `credit block without permission cannot be overridden`() {
            val b = HandoverChecks.blockers(base.copy(canOverrideCredit = false, overrideSignature = true, creditOverrideReason = "x"))
            assertEquals(listOf(HandoverChecks.Blocker.CREDIT_NOT_PERMITTED), b)
        }

        @Test
        fun `unknown status never blocks and check-in ignores checkout gates`() {
            assertTrue(HandoverChecks.blockers(base.copy(esign = null, credit = null)).isEmpty())
            val checkIn = base.copy(direction = "in", meterText = "40")
            assertEquals(listOf(HandoverChecks.Blocker.METER_NOT_PERMITTED), HandoverChecks.blockers(checkIn))
            assertEquals(
                listOf(HandoverChecks.Blocker.METER_NEEDS_REASON),
                HandoverChecks.blockers(checkIn.copy(canOverrideMeter = true)),
            )
            assertTrue(HandoverChecks.meterOverrideNeeded(checkIn))
        }

        @Test
        fun `pending local signature does not block`() {
            val sig = RentalSignature("g1", "o1", null, "Ann", "customer", "in_person", Instant.EPOCH, "r1", "s1", null)
            val local = Esign.withLocal(Esign.parse(statusJson)!!, listOf(sig))
            assertFalse(HandoverChecks.blockers(base.copy(esign = local, creditOverrideReason = "r")).contains(HandoverChecks.Blocker.SIGNATURE))
        }

        @Test
        fun `freshness window`() {
            assertTrue(HandoverChecks.fresh(1_000, 1_000 + HandoverChecks.FRESH_MS))
            assertFalse(HandoverChecks.fresh(1_000, 2_000 + HandoverChecks.FRESH_MS))
            assertFalse(HandoverChecks.fresh(null, 0))
        }

        @Test
        fun `missing allowed field means allowed`() {
            assertTrue(CreditCheck.parse(json("{}"))!!.allowed)
        }
    }
}