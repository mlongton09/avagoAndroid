package com.avago.feature.rentals.marketplace

import androidx.lifecycle.SavedStateHandle
import com.avago.feature.rentals.domain.parseObj
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.data.RenterSession
import com.avago.feature.rentals.marketplace.domain.MktAvailabilityDay
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktListing
import com.avago.feature.rentals.marketplace.domain.MktMeetSlots
import com.avago.feature.rentals.marketplace.domain.MktMyRequests
import com.avago.feature.rentals.marketplace.domain.MktOwnerListing
import com.avago.feature.rentals.marketplace.domain.MktOwnerMarketplace
import com.avago.feature.rentals.marketplace.domain.MktPlaces
import com.avago.feature.rentals.marketplace.domain.MktRenter
import com.avago.feature.rentals.marketplace.domain.MktRenterMeetup
import com.avago.feature.rentals.marketplace.domain.MktReservation
import com.avago.feature.rentals.marketplace.domain.MktSearchPage
import com.avago.feature.rentals.marketplace.domain.MktRenterBooking
import com.avago.feature.rentals.marketplace.domain.MktSlot
import com.avago.feature.rentals.marketplace.nav.MarketplaceRoutes
import com.avago.feature.rentals.marketplace.owner.MktDraftError
import com.avago.feature.rentals.marketplace.owner.MktSettingsDraft
import com.avago.feature.rentals.marketplace.owner.MktSettingsForm
import com.avago.feature.rentals.marketplace.owner.summaryIn
import com.avago.feature.rentals.marketplace.renter.MktMeetViewModel
import com.avago.feature.rentals.marketplace.renter.MktSignInStep
import com.avago.feature.rentals.marketplace.renter.MktSignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MktViewModelsTest {
    private fun json(s: String) = parseObj(s)!!
    private val now = Instant.parse("2026-11-16T18:00:00Z")
    private val s1 = MktSlot(Instant.parse("2026-11-17T17:00:00Z"), Instant.parse("2026-11-17T17:30:00Z"))
    private val s2 = MktSlot(Instant.parse("2026-11-18T17:00:00Z"), Instant.parse("2026-11-18T17:30:00Z"))

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    /** Renter repository double: scripted results, recorded calls. */
    private class FakeRepo : MarketplaceRepository {
        override val session = MutableStateFlow<RenterSession?>(null)
        var listingResult: MktResult<MktListing> = MktResult.Err(MktError.Network)
        var slots: MktResult<MktMeetSlots> = MktResult.Err(MktError.Network)
        var meetupResult: MktResult<MktRenterMeetup> = MktResult.Err(MktError.Network)
        var linkResult: MktResult<Unit> = MktResult.Ok(Unit)
        var completeResult: MktResult<MktRenter?> = MktResult.Ok(null)
        var slotCalls = 0
        val meetupBodies = mutableListOf<JsonObject>()
        val links = mutableListOf<Pair<String, String?>>()
        val completed = mutableListOf<String>()

        override suspend fun places(q: String, country: String?): MktResult<MktPlaces> = MktResult.Err(MktError.Network)
        override suspend fun search(params: List<Pair<String, String>>): MktResult<MktSearchPage> = MktResult.Err(MktError.Network)
        override suspend fun listing(id: String, lat: Double?, lng: Double?, locationId: String?) = listingResult
        override fun cachedListing(id: String): MktListing? = null
        override suspend fun availability(id: String, from: LocalDate, days: Int, locationId: String?, zone: ZoneId): MktResult<List<MktAvailabilityDay>> =
            MktResult.Err(MktError.Network)
        override suspend fun meetSlots(id: String, days: Int, locationId: String?): MktResult<MktMeetSlots> { slotCalls++; return slots }
        override suspend fun requestLink(email: String, name: String?, locale: String, next: String?): MktResult<Unit> {
            links += email to next
            return linkResult
        }
        override suspend fun completeSignIn(magicToken: String): MktResult<MktRenter?> { completed += magicToken; return completeResult }
        override suspend fun signOut() { session.value = null }
        override suspend fun me(): MktResult<MktRenter> = MktResult.Err(MktError.Unauthorized)
        override suspend fun updateMe(name: String, phone: String, locale: String?): MktResult<MktRenter> = MktResult.Err(MktError.Network)
        override suspend fun reserve(listingId: String, body: JsonObject): MktResult<MktReservation> = MktResult.Err(MktError.Network)
        override suspend fun requestMeetup(listingId: String, body: JsonObject): MktResult<MktRenterMeetup> { meetupBodies += body; return meetupResult }
        override suspend fun myRequests(): MktResult<MktMyRequests> = MktResult.Err(MktError.Network)
        override suspend fun cancelMeetup(id: String): MktResult<MktRenterMeetup?> = MktResult.Err(MktError.Network)
        override suspend fun acceptCounter(id: String): MktResult<MktRenterMeetup?> = MktResult.Err(MktError.Network)
        override suspend fun rescheduleMeetup(id: String, slots: List<MktSlot>, message: String?): MktResult<MktRenterMeetup?> = MktResult.Err(MktError.Network)
        override suspend fun changeBookingDates(orderId: String, start: Instant, end: Instant, message: String?): MktResult<MktRenterBooking?> = MktResult.Err(MktError.Network)
        override suspend fun cancelBooking(orderId: String, reason: String?): MktResult<MktRenterBooking?> = MktResult.Err(MktError.Network)
        override suspend fun withdrawChange(changeId: String): MktResult<Unit> = MktResult.Err(MktError.Network)
    }

    private fun listing() = MktListing.of(
        json(
            """{"id":"l1","currency":"CAD","listing":{"title":"Trailer"},"location":{"location_id":"loc1"},
            "fulfilment":{"pickup":false,"delivery":false,"meetup":true},"booking":{"mode":"quote"},
            "meetup":{"slot_minutes":30,"lead_hours":6,"timezone":"America/Vancouver"}}""",
        ),
    )!!

    private fun slotsOf(vararg s: MktSlot) = MktMeetSlots("loc1", "America/Vancouver", 30, s.toList())

    private fun meetVm(repo: FakeRepo) = MktMeetViewModel(repo, SavedStateHandle(mapOf("listingId" to "l1"))).also {
        it.clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    // ---- propose a meet-up ----

    @Test
    fun `meet loads the listing and the owner's slots`() {
        val repo = FakeRepo().apply { listingResult = MktResult.Ok(listing()); slots = MktResult.Ok(slotsOf(s1, s2)) }
        val s = meetVm(repo).state.value
        assertEquals(false, s.loading)
        assertEquals(listOf(s1, s2), s.slots!!.slots)
        assertEquals(ZoneId.of("America/Vancouver"), s.zone)
    }

    @Test
    fun `meet needs a slot, and a missing listing is a load error`() {
        val repo = FakeRepo().apply { listingResult = MktResult.Ok(listing()); slots = MktResult.Ok(slotsOf(s1)) }
        val vm = meetVm(repo)
        vm.submit()
        assertEquals(MktError.ofCode("pick_slot"), vm.state.value.error)
        assertTrue(repo.meetupBodies.isEmpty())

        val missing = meetVm(FakeRepo().apply { listingResult = MktResult.Err(MktError.NotFound) })
        assertEquals(MktError.NotFound, missing.state.value.loadError)
        assertNull(missing.state.value.slots)
    }

    @Test
    fun `a slot taken meanwhile reloads the offer and drops the stale selection`() {
        val repo = FakeRepo().apply { listingResult = MktResult.Ok(listing()); slots = MktResult.Ok(slotsOf(s1, s2)) }
        val vm = meetVm(repo)
        vm.toggle(s1)
        repo.meetupResult = MktResult.Err(MktError.SlotUnavailable)
        repo.slots = MktResult.Ok(slotsOf(s2))
        vm.submit()
        assertEquals(MktError.SlotUnavailable, vm.state.value.error)
        assertEquals(2, repo.slotCalls)
        assertTrue(vm.state.value.selected.isEmpty())

        vm.toggle(s2)
        repo.meetupResult = MktResult.Ok(MktRenterMeetup.of(json("""{"id":"m1","status":"pending"}"""))!!)
        vm.submit()
        assertEquals("m1", vm.state.value.done!!.id)
        assertEquals(1, repo.meetupBodies.last()["slots"]!!.jsonArray.size)
        // Done: a second tap does not send again.
        vm.submit()
        assertEquals(2, repo.meetupBodies.size)
    }

    @Test
    fun `including a rental period requires the first day`() {
        val repo = FakeRepo().apply { listingResult = MktResult.Ok(listing()); slots = MktResult.Ok(slotsOf(s1)) }
        val vm = meetVm(repo)
        vm.toggle(s1)
        vm.setIncludePeriod(true)
        vm.submit()
        assertEquals(MktError.DatesRequired, vm.state.value.error)
        assertTrue(repo.meetupBodies.isEmpty())
    }

    // ---- renter email sign-in ----

    @Test
    fun `sign-in validates the email and keeps only a marketplace next path`() {
        val repo = FakeRepo()
        val vm = MktSignInViewModel(repo, SavedStateHandle(mapOf("next" to "/marketplace/l1")))
        vm.setEmail("ann@")
        vm.send()
        assertEquals(MktError.InvalidEmail, vm.state.value.error)
        assertTrue(repo.links.isEmpty())
        vm.setEmail(" ann@example.com ")
        vm.send()
        assertEquals(MktSignInStep.Sent, vm.state.value.step)
        assertEquals("ann@example.com" to "/marketplace/l1", repo.links.single())

        val unsafe = MktSignInViewModel(FakeRepo(), SavedStateHandle(mapOf("next" to "/app/settings")))
        assertNull(unsafe.state.value.next)
    }

    @Test
    fun `magic link completes, network errors stay retryable, bad links are final`() {
        val ok = FakeRepo()
        assertEquals(MktSignInStep.Done, MktSignInViewModel(ok, SavedStateHandle(mapOf("token" to "t1"))).state.value.step)
        assertEquals(listOf("t1"), ok.completed)

        val offline = FakeRepo().apply { completeResult = MktResult.Err(MktError.Network) }
        val vm = MktSignInViewModel(offline, SavedStateHandle(mapOf("token" to "t2")))
        assertEquals(MktSignInStep.Form, vm.state.value.step)
        assertEquals(MktError.Network, vm.state.value.error)

        val used = FakeRepo().apply { completeResult = MktResult.Err(MktError.Unauthorized, 401, "invalid_token") }
        assertEquals(MktSignInStep.LinkInvalid, MktSignInViewModel(used, SavedStateHandle(mapOf("token" to "t3"))).state.value.step)

        val signedIn = FakeRepo().apply { session.value = RenterSession("s", null, null) }
        assertEquals(MktSignInStep.Done, MktSignInViewModel(signedIn, SavedStateHandle()).state.value.step)
    }

    // ---- routes / deep links ----

    @Test
    fun `web marketplace links map to app routes`() {
        assertEquals(MarketplaceRoutes.SEARCH, MarketplaceRoutes.fromWebPath("/marketplace"))
        assertEquals(MarketplaceRoutes.SEARCH, MarketplaceRoutes.fromWebPath("/marketplace/"))
        assertEquals(MarketplaceRoutes.REQUESTS, MarketplaceRoutes.fromWebPath("/marketplace/requests"))
        assertEquals(MarketplaceRoutes.listing("abc-1"), MarketplaceRoutes.fromWebPath("/marketplace/abc-1"))
        assertEquals(
            MarketplaceRoutes.signIn(next = "/marketplace/abc", token = "tok"),
            MarketplaceRoutes.fromWebPath("/marketplace/auth", mapOf("token" to "tok", "next" to "/marketplace/abc")),
        )
        // An off-site next is dropped, the token kept.
        assertEquals(
            MarketplaceRoutes.signIn(token = "tok"),
            MarketplaceRoutes.fromWebPath("/marketplace/auth", mapOf("token" to "tok", "next" to "//evil.example")),
        )
        assertNull(MarketplaceRoutes.fromWebPath("/marketplace/a/b"))
        assertNull(MarketplaceRoutes.fromWebPath("/marketplace/bad%2Fid"))
        assertNull(MarketplaceRoutes.fromWebPath("/app/settings"))
    }

    @Test
    fun `renter routes are public, owner routes are not`() {
        assertTrue(MarketplaceRoutes.isPublic(MarketplaceRoutes.SEARCH))
        assertTrue(MarketplaceRoutes.isPublic(MarketplaceRoutes.SIGN_IN))
        assertTrue(!MarketplaceRoutes.isPublic(MarketplaceRoutes.OWNER_MEETUP))
        assertTrue(!MarketplaceRoutes.isPublic("rentals/home"))
        assertTrue(MarketplaceRoutes.isOwner(MarketplaceRoutes.OWNER_SETTINGS))
        // The operations push routing builds these as literals (RentalOpsRoutes.mktMeetup / MKT_MEETUPS).
        assertEquals("rentals/mkt/owner/meetup/m-1", MarketplaceRoutes.ownerMeetup("m-1"))
        assertEquals("rentals/mkt/owner/meetups", MarketplaceRoutes.OWNER_MEETUPS)
    }

    // ---- owner settings form / listing summaries ----

    private fun settings() = MktOwnerMarketplace.of(
        json(
            """{"settings":{"enabled":true,"offer_meetup":true,"meet_availability":{"mon":[["09:00","12:00"]]},
            "lead_hours":12,"slot_minutes":30,"timezone":"America/Vancouver","server_version":3},"readiness":{}}""",
        ),
    ).settings

    @Test
    fun `settings draft round-trips and reports the first invalid field`() {
        val base = settings()
        val draft = MktSettingsDraft.of(base)
        val (ok, err) = MktSettingsForm.build(draft, base)
        assertNull(err)
        assertEquals(base.meetAvailability, ok!!.meetAvailability)
        assertEquals(12, ok.leadHours)

        fun error(d: MktSettingsDraft) = MktSettingsForm.build(d, base).second
        assertEquals(MktDraftError("windows", "tue"), error(draft.copy(windows = draft.windows + ("tue" to "17:00-09:00"))))
        assertEquals(MktDraftError("no_windows"), error(draft.copy(windows = emptyMap())))
        assertEquals(MktDraftError("lead"), error(draft.copy(leadHours = "200")))
        assertEquals(MktDraftError("radius"), error(draft.copy(deliveryRadiusKm = "-1")))
        assertEquals(MktDraftError("timezone"), error(draft.copy(timezone = "Mars/Base")))
        assertEquals(12.5, MktSettingsForm.build(draft.copy(deliveryRadiusKm = "12,5"), base).first!!.deliveryRadiusKm)
        // Meet-ups off: no hours needed.
        assertNull(error(draft.copy(offerMeetup = false, windows = emptyMap())))
    }

    @Test
    fun `listing summary is per language without fallback`() {
        val l = MktOwnerListing.of(
            json("""{"class_id":"c1","name":"Trailer","eligible":true,"listed":true,"summary_i18n":{"en-US":"Tow it","de-DE":" "}}"""),
        )!!
        assertEquals("Tow it", summaryIn(l, "en"))
        assertEquals("", summaryIn(l, "de"))
        assertNotNull(l.summaryI18n["de-DE"])
    }
}
