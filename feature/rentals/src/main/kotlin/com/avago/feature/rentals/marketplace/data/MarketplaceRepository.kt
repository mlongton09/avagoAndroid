package com.avago.feature.rentals.marketplace.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.avago.core.network.rentals.MarketplaceApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.instant
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.parseObj
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.marketplace.domain.MktAvailability
import com.avago.feature.rentals.marketplace.domain.MktAvailabilityDay
import com.avago.feature.rentals.marketplace.domain.MktBodies
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktListing
import com.avago.feature.rentals.marketplace.domain.MktMeetSlots
import com.avago.feature.rentals.marketplace.domain.MktMyRequests
import com.avago.feature.rentals.marketplace.domain.MktPlaces
import com.avago.feature.rentals.marketplace.domain.MktRenter
import com.avago.feature.rentals.marketplace.domain.MktRenterBooking
import com.avago.feature.rentals.marketplace.domain.MktRenterMeetup
import com.avago.feature.rentals.marketplace.domain.MktReservation
import com.avago.feature.rentals.marketplace.domain.MktSearchPage
import com.avago.feature.rentals.marketplace.domain.MktSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of an online marketplace call: the marketplace is online-only (§10.8). */
sealed interface MktResult<out T> {
    data class Ok<T>(val value: T) : MktResult<T>

    /** [status] null = no answer (offline / transport). */
    data class Err(val error: MktError, val status: Int? = null, val code: String? = null) : MktResult<Nothing>
}

inline fun <T, R> MktResult<T>.map(f: (T) -> R): MktResult<R> = when (this) {
    is MktResult.Ok -> MktResult.Ok(f(value))
    is MktResult.Err -> this
}

/** Runs [block], mapping server refusals and transport failures to [MktResult.Err]. */
suspend fun <T> mktCall(tag: String, block: suspend () -> T): MktResult<T> = try {
    MktResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: RentalsApiException) {
    MktResult.Err(MktError.of(e.status, e.code), e.status, e.code)
} catch (e: Exception) {
    Timber.d(e, "[Marketplace] $tag failed")
    MktResult.Err(MktError.Network)
}

/** A signed-in renter (separate from staff accounts; never shares the staff token store). */
data class RenterSession(val token: String, val expiresAt: Instant?, val renter: MktRenter?)

/** Persistence of the renter session token (EncryptedSharedPreferences in the app). */
interface RenterTokenStore {
    fun read(): String?
    fun write(json: String)
    fun clear()
}

@Singleton
class EncryptedRenterTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RenterTokenStore {
    /** Null when the keystore is unusable: the session then lives in memory only (never plain text). */
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Timber.w(it, "[Marketplace] encrypted session store unavailable") }.getOrNull()
    }

    override fun read(): String? = prefs?.getString(KEY, null)
    override fun write(json: String) { prefs?.edit()?.putString(KEY, json)?.apply() }
    override fun clear() { prefs?.edit()?.remove(KEY)?.apply() }

    companion object {
        /** Excluded from backups (app/res/xml backup rules): the key does not survive a restore. */
        const val PREFS_FILE = "avago_marketplace_session"
        private const val KEY = "session"
    }
}

/** Renter-side marketplace operations. */
interface MarketplaceRepository {
    val session: StateFlow<RenterSession?>

    suspend fun places(q: String, country: String?): MktResult<MktPlaces>
    suspend fun search(params: List<Pair<String, String>>): MktResult<MktSearchPage>
    suspend fun listing(id: String, lat: Double?, lng: Double?, locationId: String?): MktResult<MktListing>
    /** Last listing loaded (detail → reserve / meet sheets), without a network call. */
    fun cachedListing(id: String): MktListing?
    suspend fun availability(id: String, from: LocalDate, days: Int, locationId: String?, zone: ZoneId): MktResult<List<MktAvailabilityDay>>
    suspend fun meetSlots(id: String, days: Int, locationId: String?): MktResult<MktMeetSlots>

    suspend fun requestLink(email: String, name: String?, locale: String, next: String?): MktResult<Unit>
    suspend fun completeSignIn(magicToken: String): MktResult<MktRenter?>
    suspend fun signOut()
    suspend fun me(): MktResult<MktRenter>
    suspend fun updateMe(name: String, phone: String, locale: String?): MktResult<MktRenter>

    suspend fun reserve(listingId: String, body: JsonObject): MktResult<MktReservation>
    suspend fun requestMeetup(listingId: String, body: JsonObject): MktResult<MktRenterMeetup>
    suspend fun myRequests(): MktResult<MktMyRequests>
    suspend fun cancelMeetup(id: String): MktResult<MktRenterMeetup?>
    suspend fun acceptCounter(id: String): MktResult<MktRenterMeetup?>

    /** §6.5: new proposed times for a meet-up (replaces a pending proposal, asks the owner when accepted). */
    suspend fun rescheduleMeetup(id: String, slots: List<MktSlot>, message: String?): MktResult<MktRenterMeetup?>
    suspend fun changeBookingDates(orderId: String, start: Instant, end: Instant, message: String?): MktResult<MktRenterBooking?>
    suspend fun cancelBooking(orderId: String, reason: String?): MktResult<MktRenterBooking?>
    suspend fun withdrawChange(changeId: String): MktResult<Unit>
}

@Singleton
class MarketplaceRepositoryImpl @Inject constructor(
    private val api: MarketplaceApi,
    private val store: RenterTokenStore,
) : MarketplaceRepository {
    private val _session = MutableStateFlow(restore())
    override val session: StateFlow<RenterSession?> = _session.asStateFlow()

    private val listings = object : LinkedHashMap<String, MktListing>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MktListing>?) = size > 20
    }

    private fun restore(): RenterSession? {
        val o = parseObj(runCatching { store.read() }.getOrNull()) ?: return null
        val token = o.str("token") ?: return null
        val expires = o.instant("expires_at")
        if (expires != null && expires.isBefore(Instant.now())) {
            store.clear()
            return null
        }
        return RenterSession(token, expires, MktRenter.of(o.obj("renter")))
    }

    private fun persist(s: RenterSession, renterJson: JsonObject?) {
        _session.value = s
        val json = buildJsonObject {
            put("token", s.token)
            s.expiresAt?.let { put("expires_at", it.toString()) }
            renterJson?.let { put("renter", it) }
        }
        store.write(RentalJson.encodeToString(JsonObject.serializer(), json))
    }

    private fun clearSession() {
        _session.value = null
        store.clear()
    }

    /** Renter call: no token → Unauthorized; a 401 clears the stored session (§10.4). */
    private suspend fun <T> renter(tag: String, block: suspend (String) -> T): MktResult<T> {
        val token = _session.value?.token ?: return MktResult.Err(MktError.Unauthorized, 401, "unauthorized")
        val r = mktCall(tag) { block(token) }
        if (r is MktResult.Err && r.status == 401) clearSession()
        return r
    }

    override suspend fun places(q: String, country: String?) = mktCall("places") { MktPlaces.of(api.places(q, country)) }

    override suspend fun search(params: List<Pair<String, String>>) = mktCall("search") { MktSearchPage.of(api.search(params)) }

    override suspend fun listing(id: String, lat: Double?, lng: Double?, locationId: String?): MktResult<MktListing> {
        val r = mktCall("listing") { MktListing.of(api.listing(id, lat, lng, locationId)) }
        return when (r) {
            is MktResult.Err -> r
            is MktResult.Ok -> r.value?.let { l -> synchronized(listings) { listings[id] = l }; MktResult.Ok(l) }
                ?: MktResult.Err(MktError.NotFound, 404, "not_found")
        }
    }

    override fun cachedListing(id: String): MktListing? = synchronized(listings) { listings[id] }

    override suspend fun availability(id: String, from: LocalDate, days: Int, locationId: String?, zone: ZoneId) =
        mktCall("availability") { MktAvailability.of(api.availability(id, from.toString(), days.coerceIn(1, 62), locationId), zone) }

    override suspend fun meetSlots(id: String, days: Int, locationId: String?) =
        mktCall("meet-slots") { MktMeetSlots.of(api.meetSlots(id, null, days.coerceIn(1, 31), locationId)) }

    override suspend fun requestLink(email: String, name: String?, locale: String, next: String?) =
        mktCall("magic-link") { api.magicLink(MktBodies.magicLink(email, name, locale, next)); Unit }

    override suspend fun completeSignIn(magicToken: String): MktResult<MktRenter?> = mktCall("session") {
        val o = api.createSession(MktBodies.session(magicToken))
        val token = o.str("token") ?: throw RentalsApiException(401, "unauthorized", "no session token", o)
        val renterJson = o.obj("renter")
        val renter = MktRenter.of(renterJson)
        persist(RenterSession(token, o.instant("expires_at"), renter), renterJson)
        renter
    }

    override suspend fun signOut() {
        val token = _session.value?.token
        clearSession()
        // Best effort revoke; the local token is gone either way.
        if (token != null) mktCall("logout") { api.logout(token) }
    }

    override suspend fun me(): MktResult<MktRenter> = renter("me") { t ->
        val o = api.me(t)
        val r = MktRenter.of(o) ?: throw RentalsApiException(404, "not_found", "no renter", o)
        _session.value?.let { persist(it.copy(renter = r), o) }
        r
    }

    override suspend fun updateMe(name: String, phone: String, locale: String?): MktResult<MktRenter> = renter("update-me") { t ->
        val o = api.updateMe(t, MktBodies.profile(name, phone, locale))
        val r = MktRenter.of(o) ?: throw RentalsApiException(404, "not_found", "no renter", o)
        _session.value?.let { persist(it.copy(renter = r), o) }
        r
    }

    override suspend fun reserve(listingId: String, body: JsonObject) =
        renter("reserve") { t -> MktReservation.of(api.reserve(t, listingId, body)) }

    override suspend fun requestMeetup(listingId: String, body: JsonObject): MktResult<MktRenterMeetup> = renter("meetup") { t ->
        val o = api.requestMeetup(t, listingId, body)
        MktRenterMeetup.of(o) ?: throw RentalsApiException(500, null, "unreadable meet-up", o)
    }

    override suspend fun myRequests() = renter("requests") { t -> MktMyRequests.of(api.myRequests(t)) }

    override suspend fun cancelMeetup(id: String) = renter("cancel") { t -> MktRenterMeetup.of(api.cancelMeetup(t, id)) }

    override suspend fun acceptCounter(id: String) = renter("accept-counter") { t -> MktRenterMeetup.of(api.acceptCounter(t, id)) }

    override suspend fun rescheduleMeetup(id: String, slots: List<MktSlot>, message: String?) =
        renter("reschedule") { t -> MktRenterMeetup.of(api.rescheduleMeetup(t, id, MktBodies.reschedule(slots, message))) }

    override suspend fun changeBookingDates(orderId: String, start: Instant, end: Instant, message: String?) =
        renter("change-dates") { t -> MktRenterBooking.of(api.changeBookingDates(t, orderId, MktBodies.changeDates(start, end, message))) }

    override suspend fun cancelBooking(orderId: String, reason: String?) =
        renter("cancel-booking") { t -> MktRenterBooking.of(api.cancelBooking(t, orderId, MktBodies.cancelBooking(reason))) }

    override suspend fun withdrawChange(changeId: String) = renter("withdraw-change") { t -> api.withdrawChange(t, changeId); Unit }

    companion object {
        /** `en-US` / `de-DE` for the magic-link email and the renter profile (the server supports en and de). */
        fun renterLocale(locale: Locale = Locale.getDefault()): String = if (locale.language == "de") "de-DE" else "en-US"
    }
}
