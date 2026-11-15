package com.avago.feature.rentals.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

        @Test
        fun `local capture marks signer and moves to next order`() {
            val s = Esign.withLocalCaptures(Esign.parse(statusJson)!!, setOf("r1" to "s1"))
            val r = s.requests.single()
            assertEquals(Esign.STATUS_SIGNED_LOCAL, r.signers.first { it.id == "s1" }.status)
            // remote signer s3 still open at order 1 → no in-person signer capturable yet
            assertNull(s.signable)
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
        fun `sign body strips data url prefix and carries consent version`() {
            val b = Esign.signBody("s1", "data:image/png;base64,QUJD", null, "v2", Instant.parse("2025-01-02T03:04:05Z"), null)
            assertEquals("QUJD", b["image_png_base64"]!!.jsonPrimitive.content)
            assertEquals("v2", b["consent_text_version"]!!.jsonPrimitive.content)
            assertTrue(b["consent"]!!.jsonPrimitive.boolean)
            assertNull(b["typed_name"])
        }

        @Test
        fun `queued capture dropped only on 409 and 410`() {
            assertTrue(Esign.dropOnConflict(409))
            assertTrue(Esign.dropOnConflict(410))
            assertFalse(Esign.dropOnConflict(400))
            assertFalse(Esign.dropOnConflict(503))
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
    inner class GatePatchTests {
        private val body = buildJsonObject { put("kind", "checkout"); put("asset_id", "a1") }

        @Test
        fun `notice round trip keeps code permission and message`() {
            val n = HandoverGates.notice("signature_required", "Sign first", null)
            val g = HandoverGates.parse(n)!!
            assertEquals("signature_required", g.code)
            assertEquals(Esign.PERMISSION_OVERRIDE, g.permission)
            assertEquals("Sign first", g.message)
            assertNull(HandoverGates.parse("sync_failed:x"))
        }

        @Test
        fun `can_override false clears the permission`() {
            val n = HandoverGates.notice("customer_on_hold", "On hold", buildJsonObject { put("can_override", false) })
            assertNull(HandoverGates.parse(n)!!.permission)
        }

        @Test
        fun `overrides accumulate across successive gates`() {
            val sig = HandoverGates.parse(HandoverGates.notice("signature_required", null, null))!!
            val credit = HandoverGates.parse(HandoverGates.notice("credit_limit_exceeded", null, null))!!
            val meter = HandoverGates.parse(HandoverGates.notice("meter_below_checkout", null, null))!!
            val b1 = HandoverGates.patch(body, sig, null, null)!!
            val b2 = HandoverGates.patch(b1, credit, "Manager OK", null)!!
            val b3 = HandoverGates.patch(b2, meter, "Meter replaced", null)!!
            assertEquals(JsonPrimitive(true), b3["override_signature"])
            assertEquals(JsonPrimitive(true), b3["override_credit"])
            assertEquals(JsonPrimitive(true), b3["override_docs"])
            assertEquals("Manager OK", b3["override_reason"]!!.jsonPrimitive.content)
            assertEquals(JsonPrimitive(true), b3["meter_override"])
            assertEquals("Meter replaced", b3["meter_override_reason"]!!.jsonPrimitive.content)
            assertEquals("a1", b3["asset_id"]!!.jsonPrimitive.content)
        }

        @Test
        fun `incomplete fixes are rejected`() {
            val credit = HandoverGates.parse(HandoverGates.notice("customer_blocked", null, null))!!
            assertNull(HandoverGates.patch(body, credit, "  ", null))
            val req = HandoverGates.parse(HandoverGates.notice("meter_required", null, null))!!
            assertNull(HandoverGates.patch(body, req, null, "-3"))
            assertEquals("12.5", HandoverGates.patch(body, req, null, "12,50")!!["meter_value"]!!.jsonPrimitive.content)
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
            val local = Esign.withLocalCaptures(Esign.parse(statusJson)!!, setOf("r1" to "s1"))
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
            assertNotNull(CreditCheck.PHOTO_KINDS.firstOrNull { it == "business_licence" })
        }
    }
}