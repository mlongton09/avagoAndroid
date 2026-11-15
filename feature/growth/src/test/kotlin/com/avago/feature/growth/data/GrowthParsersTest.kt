package com.avago.feature.growth.data

import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.growth.data.KtorGrowthService.Companion.urlOrError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrowthParsersTest {
    private fun json(s: String): JsonObject = Json.parseToJsonElement(s) as JsonObject

    @Test
    fun `owner reviews parse scores, reply rule and summary`() {
        val r = OwnerReviews.from(
            json(
                """{"reviews":[
                  {"id":"r1","status":"published","item_condition":5,"listing_accuracy":4,"owner_communication":5,"handover_delivery":4,"body":"Great"},
                  {"id":"r2","status":"published","item_condition":3,"owner_reply":"Thanks"},
                  {"id":"r3","status":"hidden","item_condition":1}],
                 "to_rate":[{"order_id":"o1","order_number":"R-1","customer_name":"Ann","window_ends_at":"2026-12-01T00:00:00Z"},{"order_number":"bad"}],
                 "summary":{"review_count":3,"new_owner":false,"owner":{"count":3,"average":4.25},"asset":{"average":4.0}}}""",
            ),
        )
        assertEquals(4.5, r.reviews[0].average)
        assertTrue(r.reviews[0].canReply)
        assertFalse(r.reviews[1].canReply)
        assertFalse(r.reviews[2].canReply)
        assertEquals(listOf("o1"), r.toRate.map { it.orderId })
        assertEquals(RatingSummary(3, 4.25, 4.0, false), r.summary)
    }

    @Test
    fun `case detail parses events and open state`() {
        val d = CaseDetail.from(
            json("""{"id":"c1","case_type":"damage","status":"awaiting_owner","subject":"Scratch","sla_breached":true,"events":[{"id":"e1","kind":"message","actor":"renter","visibility":"both","body":"Hi","photos":["p1","p2"]}]}"""),
        )!!
        assertTrue(d.case.open)
        assertTrue(d.case.slaBreached)
        assertEquals(2, d.events.single().photos)
        assertFalse(TrustCase.from(json("""{"id":"c2","status":"resolved"}"""))!!.open)
    }

    @Test
    fun `network requests derive counterparty and allowed actions`() {
        val n = NetworkRequests.from(
            json(
                """{"pending_incoming":1,"requests":[
                  {"id":"n1","direction":"incoming","status":"pending","borrower_name":"B Shop","lender_name":"Me","class_name":"Excavator","amount_minor":12000,"currency":"eur"},
                  {"id":"n2","direction":"outgoing","status":"pending","borrower_name":"Me","lender_name":"L Shop","asset_name":"Mini digger"}]}""",
            ),
        )
        assertEquals(1, n.pendingIncoming)
        val (inc, out) = n.requests
        assertEquals("B Shop", inc.counterparty)
        assertEquals("Excavator", inc.itemName)
        assertTrue(inc.canApprove)
        assertEquals("L Shop", out.counterparty)
        assertFalse(out.canApprove)
        assertTrue(out.canCancel)
    }

    @Test
    fun `connect status reads requirements and onboarding need`() {
        val s = ConnectStatus.from(
            json("""{"connect_available":true,"onboarding_state":"restricted","account":{"payouts_enabled":false,"details_submitted":true,"requirements":{"currently_due":["a","b"],"past_due":["a"]}}}"""),
        )
        assertTrue(s.available)
        assertEquals(2, s.currentlyDue)
        assertEquals(1, s.pastDue)
        assertTrue(s.needsOnboarding)
        assertFalse(ConnectStatus.from(json("""{"connect_available":true,"onboarding_state":"complete","account":null}""")).needsOnboarding)
    }

    @Test
    fun `payout page exposes cursor only when more pages exist`() {
        val more = PayoutPage.from(json("""{"items":[{"id":"po_1","amount_minor":5000,"currency":"eur","status":"paid","arrival_date":1763000000}],"has_more":true,"next_cursor":"po_1"}"""))
        assertEquals("po_1", more.nextCursor)
        assertEquals(1763000000L, more.items.single().arrivalDate)
        assertNull(PayoutPage.from(json("""{"items":[],"has_more":false,"next_cursor":"po_9"}""")).nextCursor)
        val bal = ConnectBalance.from(json("""{"available":[{"currency":"eur","amount_minor":100}],"pending":[{"amount_minor":5}]}"""))
        assertEquals(listOf(Money(100, "eur")), bal.available)
        assertTrue(bal.pending.isEmpty())
    }

    @Test
    fun `referrals and trial parse counts and checklist`() {
        val r = Referrals.from(
            json("""{"code":"ABC","link":"https://www.avagomate.com/r/ABC","activation":{"hold_days":30},"counts":{"signed_up":3,"activated":2,"rewarded":1,"blocked":0},"referrals":[{"id":"x","account_name":"Shop","status":"activated"}]}"""),
        )
        assertEquals(30, r.holdDays)
        assertEquals(listOf(3, 2, 1, 0), listOf(r.signedUp, r.activated, r.rewarded, r.blocked))
        assertEquals("activated", r.referrals.single().status)

        val t = ShopTrial.from(json("""{"eligible":false,"active":true,"days_left":5,"trial_days":14,"checklist":[{"key":"assets","done":true,"count":4},{"key":"team","done":false}],"checklist_done":1}"""))
        assertTrue(t.visible)
        assertEquals(4L, t.checklist[0].count)
        assertFalse(ShopTrial.from(json("""{"eligible":false,"active":false}""")).visible)
    }

    @Test
    fun `portal session, verification and my reviews`() {
        val s = PortalSession.from(json("""{"token":"tok","me":{"customer":{"name":"Ann"},"storefront":{"slug":"s","title":"Hire Co"}}}"""))!!
        assertEquals(PortalSession("tok", "Ann", "Hire Co"), s)
        val v = Verification.from(json("""{"levels":{"email_verified":true,"id_verified":true},"good_standing":true,"verification_available":true}"""))
        assertTrue(v.verifiedRenter)
        assertFalse(Verification.from(json("""{"levels":{"id_verified":true},"good_standing":false}""")).verifiedRenter)
        val pr = PortalReview.from(json("""{"order_id":"o1","can_submit":false,"revealed":true,"my_review":{"status":"published","body":"ok","owner_reply":"thx"},"owner_rating":{"on_time":5,"care":4,"communication":3}}"""))!!
        assertEquals(4.0, pr.ownerRatingAverage)
        assertEquals("thx", pr.ownerReply)
    }

    @Test
    fun `request bodies`() {
        val rb = KtorGrowthService.ratingBody(RenterRating(5, 4, 3, "  "))
        assertEquals(setOf("on_time", "care", "communication"), rb.keys)
        assertFalse(RenterRating(0, 4, 3, null).valid)
        val mb = KtorGrowthService.messageBody(" hi ", ownerOnly = true)
        assertEquals("hi", mb["body"]!!.jsonPrimitive.content)
        assertEquals("owner", mb["visibility"]!!.jsonPrimitive.content)
        assertEquals(0, mb["photos"]!!.jsonArray.size)
        assertFalse("visibility" in KtorGrowthService.messageBody("x", ownerOnly = false))
        assertEquals(5, rb["on_time"]!!.jsonPrimitive.int)
    }

    @Test
    fun `url responses must be https`() {
        assertEquals(ApiResult.Ok("https://connect.stripe.com/x"), ApiResult.Ok(json("""{"url":"https://connect.stripe.com/x"}""")).urlOrError())
        assertTrue(ApiResult.Ok(json("""{"url":"http://evil"}""")).urlOrError() is ApiResult.Err)
        val err = ApiResult.Err(ApiError(409, "connect_onboarding_incomplete", ""))
        assertEquals(err, err.urlOrError())
    }

    @Test
    fun `portal link parsing`() {
        val tok = "abcdefghijklmnop1234"
        assertEquals(PortalLink("hire-co", tok), PortalLink.parse("https://www.avagomate.com/rent/hire-co/portal?token=$tok"))
        assertEquals(PortalLink("hire-co", tok), PortalLink.parse("https://www.avagomate.com/rent/hire-co/portal/?x=1&token=$tok"))
        assertNull(PortalLink.parse("https://www.avagomate.com/rent/hire-co/portal"))
        assertNull(PortalLink.parse("https://www.avagomate.com/rent/hire-co?token=$tok"))
        assertNull(PortalLink.parse("http://www.avagomate.com/rent/hire-co/portal?token=$tok"))
        assertNull(PortalLink.parse("https://www.avagomate.com/rent/hire-co/portal?token=short"))
        assertNull(PortalLink.parse(null))
    }
}
