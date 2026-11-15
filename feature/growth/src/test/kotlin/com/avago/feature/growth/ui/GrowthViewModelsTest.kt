package com.avago.feature.growth.ui

import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.growth.R
import com.avago.feature.growth.data.CaseDetail
import com.avago.feature.growth.data.ConnectBalance
import com.avago.feature.growth.data.ConnectStatus
import com.avago.feature.growth.data.GrowthService
import com.avago.feature.growth.data.Load
import com.avago.feature.growth.data.NetworkRequest
import com.avago.feature.growth.data.NetworkRequests
import com.avago.feature.growth.data.Notice
import com.avago.feature.growth.data.OwnerReviews
import com.avago.feature.growth.data.Payout
import com.avago.feature.growth.data.PayoutPage
import com.avago.feature.growth.data.RatingSummary
import com.avago.feature.growth.data.Referrals
import com.avago.feature.growth.data.RenterRating
import com.avago.feature.growth.data.ShopTrial
import com.avago.feature.growth.data.TrustCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Fake that fails every call unless a test overrides it. */
private open class FakeGrowth : GrowthService {
    fun <T> nope(status: Int = 500): ApiResult<T> = ApiResult.Err(ApiError(status, "not_faked", "not faked"))
    override suspend fun reviews(): ApiResult<OwnerReviews> = nope()
    override suspend fun rateRenter(orderId: String, rating: RenterRating): ApiResult<Unit> = nope()
    override suspend fun reply(reviewId: String, reply: String): ApiResult<Unit> = nope()
    override suspend fun cases(openOnly: Boolean): ApiResult<List<TrustCase>> = nope()
    override suspend fun case(id: String): ApiResult<CaseDetail> = nope()
    override suspend fun postCaseMessage(id: String, body: String, ownerOnly: Boolean): ApiResult<Unit> = nope()
    override suspend fun networkRequests(direction: String): ApiResult<NetworkRequests> = nope()
    override suspend fun networkAction(id: String, action: String, note: String?): ApiResult<Unit> = nope()
    override suspend fun connectStatus(): ApiResult<ConnectStatus> = nope()
    override suspend fun connectBalance(): ApiResult<ConnectBalance> = nope()
    override suspend fun payouts(after: String?): ApiResult<PayoutPage> = nope()
    override suspend fun onboardingLink(): ApiResult<String> = nope()
    override suspend fun dashboardLink(): ApiResult<String> = nope()
    override suspend fun referrals(): ApiResult<Referrals> = nope()
    override suspend fun shopTrial(): ApiResult<ShopTrial> = nope()
    override suspend fun startShopTrial(): ApiResult<ShopTrial> = nope()
}

private fun request(id: String, direction: String = "incoming") =
    NetworkRequest(id, direction, "pending", null, null, null, "B", "L", "Excavator", null, null, null, null, null)

private fun status(state: String) = ConnectStatus(true, state, true, true, true, null, 0, 0, null, "eur")

private fun payout(id: String) = Payout(id, 100, "eur", "paid", null, null, null)

@OptIn(ExperimentalCoroutinesApi::class)
class GrowthViewModelsTest {
    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `reviews view-model rejects incomplete ratings and refreshes after saving`() = runTest {
        val rated = mutableListOf<String>()
        var loads = 0
        val vm = ReviewsViewModel(object : FakeGrowth() {
            override suspend fun reviews(): ApiResult<OwnerReviews> {
                loads++
                return ApiResult.Ok(OwnerReviews(emptyList(), emptyList(), RatingSummary(0, null, null, true)))
            }
            override suspend fun rateRenter(orderId: String, rating: RenterRating): ApiResult<Unit> {
                rated += orderId
                return ApiResult.Ok(Unit)
            }
        })
        val notices = mutableListOf<Notice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }

        vm.rate("o1", RenterRating(0, 5, 5, null))
        assertTrue(rated.isEmpty())
        vm.rate("o1", RenterRating(5, 5, 5, null))
        assertEquals(listOf("o1"), rated)
        assertEquals(2, loads)
        assertEquals(listOf<Notice>(Notice.Done(R.string.growth_rating_saved)), notices)
    }

    @Test
    fun `network inbox switches direction and reports failed actions`() = runTest {
        val asked = mutableListOf<String>()
        val vm = NetworkInboxViewModel(object : FakeGrowth() {
            override suspend fun networkRequests(direction: String): ApiResult<NetworkRequests> {
                asked += direction
                return ApiResult.Ok(NetworkRequests(listOf(request("n1", direction)), 1))
            }
            override suspend fun networkAction(id: String, action: String, note: String?) =
                ApiResult.Err(ApiError(409, "request_expired", "Expired"))
        })
        val notices = mutableListOf<Notice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }

        vm.setDirection("outgoing")
        vm.setDirection("outgoing")
        assertEquals(listOf("incoming", "outgoing"), asked)
        assertEquals("outgoing", (vm.state.value as Load.Ready).value.requests.single().direction)

        vm.decide("n1", "cancel", null)
        assertEquals("request_expired", (notices.single() as Notice.Failed).error.code)
        assertEquals(false, vm.busy.value)
    }

    @Test
    fun `payouts skip Stripe calls before onboarding and page with the cursor`() = runTest {
        var state = "not_started"
        val pages = mutableListOf<String?>()
        val vm = PayoutsViewModel(object : FakeGrowth() {
            override suspend fun connectStatus() = ApiResult.Ok(status(state))
            override suspend fun connectBalance() = ApiResult.Ok(ConnectBalance(emptyList(), emptyList()))
            override suspend fun payouts(after: String?): ApiResult<PayoutPage> {
                pages += after
                return if (after == null) ApiResult.Ok(PayoutPage(listOf(payout("p1")), "p1")) else ApiResult.Ok(PayoutPage(listOf(payout("p2")), null))
            }
        })
        assertTrue(pages.isEmpty())
        assertNull(vm.balance.value)

        state = "complete"
        vm.refresh()
        vm.loadMore()
        assertEquals(listOf(null, "p1"), pages)
        assertEquals(listOf("p1", "p2"), vm.payouts.value.map { it.id })
        assertNull(vm.hasMore.value)
    }

    @Test
    fun `home hides the trial card when the trial is forbidden`() = runTest {
        val vm = GrowthHomeViewModel(object : FakeGrowth() {
            override suspend fun shopTrial(): ApiResult<ShopTrial> = nope(403)
            override suspend fun networkRequests(direction: String) = ApiResult.Ok(NetworkRequests(emptyList(), 3))
        })
        assertNull(vm.trial.value)
        assertEquals(3, vm.pendingIncoming.value)
    }
}
