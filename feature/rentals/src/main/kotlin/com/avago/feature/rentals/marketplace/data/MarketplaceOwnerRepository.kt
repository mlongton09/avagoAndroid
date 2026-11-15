package com.avago.feature.rentals.marketplace.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.rentals.RentalsApi
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.marketplace.domain.MktBlockedRenter
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktOwnerListing
import com.avago.feature.rentals.marketplace.domain.MktOwnerMarketplace
import com.avago.feature.rentals.marketplace.domain.MktOwnerMeetup
import com.avago.feature.rentals.marketplace.domain.MktOwnerMeetupPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** A cached inbox page and the device time it was fetched. */
data class CachedInbox(val at: Long, val page: MktOwnerMeetupPage)

/** Owner (staff) marketplace operations on the active account (§7). Actions are online-only. */
interface MarketplaceOwnerRepository {
    suspend fun settings(): MktResult<MktOwnerMarketplace>
    suspend fun saveSettings(body: JsonObject): MktResult<MktOwnerMarketplace>
    suspend fun listings(): MktResult<List<MktOwnerListing>>
    suspend fun saveListing(classId: String, body: JsonObject): MktResult<MktOwnerListing?>
    suspend fun saveLocation(locationId: String, body: JsonObject): MktResult<Unit>

    /** Last good first page of [status] (display cache, §10.8). */
    fun cachedInbox(status: String): Flow<CachedInbox?>
    suspend fun refreshInbox(status: String): MktResult<MktOwnerMeetupPage>
    suspend fun inboxPage(status: String, cursor: String): MktResult<MktOwnerMeetupPage>
    suspend fun meetup(id: String): MktResult<MktOwnerMeetup>
    /** accept | counter | decline | block-renter; returns the updated request when the server sends it. */
    suspend fun meetupAction(id: String, action: String, body: JsonObject): MktResult<MktOwnerMeetup?>
    suspend fun blocks(): MktResult<List<MktBlockedRenter>>
    suspend fun unblock(renterId: String): MktResult<Unit>
}

@Singleton
class MarketplaceOwnerRepositoryImpl @Inject constructor(
    private val api: RentalsApi,
    private val identity: IdentityManager,
    private val online: RentalsOnline,
) : MarketplaceOwnerRepository {

    private suspend fun <T> owner(tag: String, block: suspend (String) -> T): MktResult<T> {
        val acc = identity.getActiveAccountId() ?: return MktResult.Err(MktError.Unauthorized, 401, "unauthorized")
        return mktCall(tag) { block(acc) }
    }

    override suspend fun settings() = owner("settings") { MktOwnerMarketplace.of(api.marketplaceSettings(it)) }

    override suspend fun saveSettings(body: JsonObject) = owner("save-settings") { acc ->
        val o = api.updateMarketplaceSettings(acc, body)
        // PUT may answer with the settings only; re-read when readiness is missing.
        if (o["readiness"] == null) MktOwnerMarketplace.of(api.marketplaceSettings(acc)) else MktOwnerMarketplace.of(o)
    }

    override suspend fun listings() = owner("listings") { MktOwnerListing.list(api.marketplaceListings(it)) }

    override suspend fun saveListing(classId: String, body: JsonObject) =
        owner("save-listing") { MktOwnerListing.of(api.updateMarketplaceListing(it, classId, body)) }

    override suspend fun saveLocation(locationId: String, body: JsonObject) =
        owner("save-location") { api.updateMarketplaceLocation(it, locationId, body); Unit }

    override fun cachedInbox(status: String): Flow<CachedInbox?> =
        online.observe(online.meetupInboxKey(status)).map { c -> c?.let { CachedInbox(it.at, MktOwnerMeetupPage.of(it.data)) } }

    override suspend fun refreshInbox(status: String): MktResult<MktOwnerMeetupPage> =
        when (val r = online.refreshMeetupInbox(status)) {
            is RentalsOnline.Result.Ok -> MktResult.Ok(MktOwnerMeetupPage.of(r.value))
            is RentalsOnline.Result.Refused -> {
                if (r.status == 403) online.clearMeetupInbox(status)
                MktResult.Err(MktError.of(r.status, r.code), r.status, r.code)
            }
            is RentalsOnline.Result.Offline -> MktResult.Err(MktError.Network)
        }

    override suspend fun inboxPage(status: String, cursor: String) =
        owner("inbox-page") { MktOwnerMeetupPage.of(api.meetups(it, status, cursor)) }

    override suspend fun meetup(id: String): MktResult<MktOwnerMeetup> = owner("meetup") { acc ->
        val o = api.meetup(acc, id)
        MktOwnerMeetup.of(o) ?: throw com.avago.core.network.rentals.RentalsApiException(404, "not_found", "no meet-up", o)
    }

    override suspend fun meetupAction(id: String, action: String, body: JsonObject) =
        owner(action) { MktOwnerMeetup.of(api.meetupAction(it, id, action, body)) }

    override suspend fun blocks() = owner("blocks") { MktBlockedRenter.list(api.marketplaceBlocks(it)) }

    override suspend fun unblock(renterId: String) = owner("unblock") { api.unblockRenter(it, renterId); Unit }
}
