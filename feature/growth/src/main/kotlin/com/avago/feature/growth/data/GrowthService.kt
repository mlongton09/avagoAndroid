package com.avago.feature.growth.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Owner-side trust (E), network inbox (A), Connect payouts (K), referrals (G) and shop trial (H). */
interface GrowthService {
    suspend fun reviews(): ApiResult<OwnerReviews>
    suspend fun rateRenter(orderId: String, rating: RenterRating): ApiResult<Unit>
    suspend fun reply(reviewId: String, reply: String): ApiResult<Unit>

    suspend fun cases(openOnly: Boolean): ApiResult<List<TrustCase>>
    suspend fun case(id: String): ApiResult<CaseDetail>
    suspend fun postCaseMessage(id: String, body: String, ownerOnly: Boolean): ApiResult<Unit>

    suspend fun networkRequests(direction: String): ApiResult<NetworkRequests>
    suspend fun networkAction(id: String, action: String, note: String?): ApiResult<Unit>

    suspend fun connectStatus(): ApiResult<ConnectStatus>
    suspend fun connectBalance(): ApiResult<ConnectBalance>
    suspend fun payouts(after: String?): ApiResult<PayoutPage>
    suspend fun onboardingLink(): ApiResult<String>
    suspend fun dashboardLink(): ApiResult<String>

    suspend fun referrals(): ApiResult<Referrals>

    suspend fun shopTrial(): ApiResult<ShopTrial>
    suspend fun startShopTrial(): ApiResult<ShopTrial>
}

@Singleton
class KtorGrowthService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : GrowthService {
    private fun acc(): String? = identity.getActiveAccountId()
    private fun rentals(a: String, p: String) = "/accounts/$a/rentals$p"
    private fun trust(a: String, p: String) = rentals(a, "/trust$p")

    override suspend fun reviews(): ApiResult<OwnerReviews> {
        val a = acc() ?: return noAccount()
        return api.get(trust(a, "/reviews")).map(OwnerReviews::from)
    }

    override suspend fun rateRenter(orderId: String, rating: RenterRating): ApiResult<Unit> {
        val a = acc() ?: return noAccount()
        return api.post(trust(a, "/reviews/orders/$orderId/renter-rating"), ratingBody(rating)).map { }
    }

    override suspend fun reply(reviewId: String, reply: String): ApiResult<Unit> {
        val a = acc() ?: return noAccount()
        return api.post(trust(a, "/reviews/$reviewId/reply"), jsonBody("reply" to reply.trim())).map { }
    }

    override suspend fun cases(openOnly: Boolean): ApiResult<List<TrustCase>> {
        val a = acc() ?: return noAccount()
        return api.get(trust(a, "/cases"), mapOf("open" to if (openOnly) true else null)).map { o -> o.objs("items").mapNotNull(TrustCase::from) }
    }

    override suspend fun case(id: String): ApiResult<CaseDetail> {
        val a = acc() ?: return noAccount()
        return when (val r = api.get(trust(a, "/cases/$id"))) {
            is ApiResult.Ok -> CaseDetail.from(r.value)?.let { ApiResult.Ok(it) } ?: ApiResult.Err(ApiError(404, "not_found", "Case not found"))
            is ApiResult.Err -> r
        }
    }

    override suspend fun postCaseMessage(id: String, body: String, ownerOnly: Boolean): ApiResult<Unit> {
        val a = acc() ?: return noAccount()
        return api.post(trust(a, "/cases/$id/messages"), messageBody(body, ownerOnly)).map { }
    }

    override suspend fun networkRequests(direction: String): ApiResult<NetworkRequests> {
        val a = acc() ?: return noAccount()
        return api.get(rentals(a, "/network/requests"), mapOf("direction" to direction, "limit" to 100)).map(NetworkRequests::from)
    }

    override suspend fun networkAction(id: String, action: String, note: String?): ApiResult<Unit> {
        require(action in NETWORK_ACTIONS) { "unknown action $action" }
        val a = acc() ?: return noAccount()
        return api.post(rentals(a, "/network/requests/$id/$action"), jsonBody("note" to note?.trim()?.ifBlank { null })).map { }
    }

    override suspend fun connectStatus(): ApiResult<ConnectStatus> {
        val a = acc() ?: return noAccount()
        return api.get(rentals(a, "/payments/connect")).map(ConnectStatus::from)
    }

    override suspend fun connectBalance(): ApiResult<ConnectBalance> {
        val a = acc() ?: return noAccount()
        return api.get(rentals(a, "/payments/connect/balance")).map(ConnectBalance::from)
    }

    override suspend fun payouts(after: String?): ApiResult<PayoutPage> {
        val a = acc() ?: return noAccount()
        return api.get(rentals(a, "/payments/connect/payouts"), mapOf("limit" to 20, "starting_after" to after)).map(PayoutPage::from)
    }

    override suspend fun onboardingLink(): ApiResult<String> {
        val a = acc() ?: return noAccount()
        return api.post(rentals(a, "/payments/connect/onboarding")).urlOrError()
    }

    override suspend fun dashboardLink(): ApiResult<String> {
        val a = acc() ?: return noAccount()
        return api.post(rentals(a, "/payments/connect/dashboard-link")).urlOrError()
    }

    override suspend fun referrals(): ApiResult<Referrals> {
        val a = acc() ?: return noAccount()
        return api.get("/accounts/$a/growth/referrals").map(Referrals::from)
    }

    override suspend fun shopTrial(): ApiResult<ShopTrial> {
        val a = acc() ?: return noAccount()
        return api.get("/accounts/$a/shop-trial").map(ShopTrial::from)
    }

    override suspend fun startShopTrial(): ApiResult<ShopTrial> {
        val a = acc() ?: return noAccount()
        return api.post("/accounts/$a/shop-trial").map(ShopTrial::from)
    }

    companion object {
        val NETWORK_ACTIONS = setOf("approve", "decline", "cancel")

        fun ratingBody(r: RenterRating): JsonObject = jsonBody(
            "on_time" to r.onTime,
            "care" to r.care,
            "communication" to r.communication,
            "body" to r.body?.trim()?.ifBlank { null },
        )

        /** `visibility: owner` keeps the note from the renter; the default is both sides. */
        fun messageBody(body: String, ownerOnly: Boolean): JsonObject = jsonBody(
            "body" to body.trim(),
            "photos" to emptyList<String>(),
            "visibility" to if (ownerOnly) "owner" else null,
        )

        internal fun ApiResult<JsonObject>.urlOrError(): ApiResult<String> = when (this) {
            is ApiResult.Ok -> value.str("url")?.takeIf { it.startsWith("https://") }?.let { ApiResult.Ok(it) }
                ?: ApiResult.Err(ApiError(502, "no_url", "The server did not return a link"))
            is ApiResult.Err -> this
        }
    }
}

/**
 * Renter portal session (E). The single-use link token is exchanged for a session token that is
 * kept in memory only and sent as `X-Portal-Token`.
 */
@Singleton
class PortalService @Inject constructor(private val api: JsonApi) {
    @Volatile
    var session: PortalSession? = null
        private set

    private fun headers(): Map<String, String>? = session?.let { mapOf("X-Portal-Token" to it.token) }

    suspend fun open(linkToken: String): ApiResult<PortalSession> =
        when (val r = api.post("/public/rentals/portal/session", jsonBody("token" to linkToken.trim()))) {
            is ApiResult.Ok -> PortalSession.from(r.value)?.let { s ->
                session = s
                ApiResult.Ok(s)
            } ?: ApiResult.Err(ApiError(502, "no_session", "No portal session returned"))
            is ApiResult.Err -> r
        }

    suspend fun verification(): ApiResult<Verification> {
        val h = headers() ?: return signedOut()
        return api.get("/public/rentals/portal/trust/verification", headers = h).map(Verification::from)
    }

    /** Starts a Stripe Identity session; returns the hosted verification URL. */
    suspend fun startVerification(purpose: String): ApiResult<String> {
        val h = headers() ?: return signedOut()
        return when (val r = api.post("/public/rentals/portal/trust/verification", jsonBody("purpose" to purpose), headers = h)) {
            is ApiResult.Ok -> r.value.str("url")?.let { ApiResult.Ok(it) } ?: ApiResult.Err(ApiError(502, "no_url", "No verification link returned"))
            is ApiResult.Err -> r
        }
    }

    suspend fun myReviews(): ApiResult<List<PortalReview>> {
        val h = headers() ?: return signedOut()
        return api.get("/public/rentals/portal/trust/reviews", headers = h).map { o -> o.objs("items").mapNotNull(PortalReview::from) }
    }

    fun signOut() {
        session = null
    }

    private fun signedOut(): ApiResult.Err = ApiResult.Err(ApiError(401, "portal_signed_out", "Open the link from your e-mail again"))
}
