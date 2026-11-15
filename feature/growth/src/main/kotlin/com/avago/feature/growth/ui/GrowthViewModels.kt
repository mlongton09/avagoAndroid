package com.avago.feature.growth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiResult
import com.avago.feature.growth.R
import com.avago.feature.growth.data.CaseDetail
import com.avago.feature.growth.data.ConnectBalance
import com.avago.feature.growth.data.ConnectStatus
import com.avago.feature.growth.data.GrowthService
import com.avago.feature.growth.data.Load
import com.avago.feature.growth.data.NetworkRequests
import com.avago.feature.growth.data.Notice
import com.avago.feature.growth.data.OwnerReviews
import com.avago.feature.growth.data.Payout
import com.avago.feature.growth.data.PortalReview
import com.avago.feature.growth.data.PortalService
import com.avago.feature.growth.data.PortalSession
import com.avago.feature.growth.data.Referrals
import com.avago.feature.growth.data.RenterRating
import com.avago.feature.growth.data.ShopTrial
import com.avago.feature.growth.data.TrustCase
import com.avago.feature.growth.data.Verification
import com.avago.feature.growth.data.toLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GrowthHomeViewModel @Inject constructor(private val growth: GrowthService) : NoticeViewModel() {
    private val _trial = MutableStateFlow<ShopTrial?>(null)

    /** Null while loading, on error or when the account may not see it (403). */
    val trial: StateFlow<ShopTrial?> = _trial.asStateFlow()
    private val _pendingIncoming = MutableStateFlow(0)
    val pendingIncoming: StateFlow<Int> = _pendingIncoming.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _trial.value = growth.shopTrial().valueOrNull()?.takeIf { it.visible }
            _pendingIncoming.value = growth.networkRequests("incoming").valueOrNull()?.pendingIncoming ?: 0
        }
    }

    fun startTrial() = act(R.string.growth_trial_started, { growth.startShopTrial() }) { _trial.value = it.takeIf { t -> t.visible } }
}

@HiltViewModel
class ReviewsViewModel @Inject constructor(private val growth: GrowthService) : NoticeViewModel() {
    private val _state = MutableStateFlow<Load<OwnerReviews>>(Load.Loading)
    val state: StateFlow<Load<OwnerReviews>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is Load.Ready) _state.value = Load.Loading
            _state.value = growth.reviews().toLoad()
        }
    }

    fun rate(orderId: String, rating: RenterRating) {
        if (!rating.valid) return
        act(R.string.growth_rating_saved, { growth.rateRenter(orderId, rating) }) { refresh() }
    }

    fun reply(reviewId: String, text: String) {
        if (text.isBlank()) return
        act(R.string.growth_reply_posted, { growth.reply(reviewId, text) }) { refresh() }
    }
}

@HiltViewModel
class CasesViewModel @Inject constructor(private val growth: GrowthService) : NoticeViewModel() {
    private val _state = MutableStateFlow<Load<List<TrustCase>>>(Load.Loading)
    val state: StateFlow<Load<List<TrustCase>>> = _state.asStateFlow()
    private val _openOnly = MutableStateFlow(true)
    val openOnly: StateFlow<Boolean> = _openOnly.asStateFlow()

    init { refresh() }

    fun setOpenOnly(v: Boolean) {
        if (_openOnly.value == v) return
        _openOnly.value = v
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = Load.Loading
            _state.value = growth.cases(_openOnly.value).toLoad()
        }
    }
}

@HiltViewModel
class CaseDetailViewModel @Inject constructor(
    private val growth: GrowthService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val caseId: String = checkNotNull(savedState["caseId"])
    private val _state = MutableStateFlow<Load<CaseDetail>>(Load.Loading)
    val state: StateFlow<Load<CaseDetail>> = _state.asStateFlow()

    /** Bumped after each sent message so the screen can clear its draft. */
    private val _sent = MutableStateFlow(0)
    val sent: StateFlow<Int> = _sent.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is Load.Ready) _state.value = Load.Loading
            _state.value = growth.case(caseId).toLoad()
        }
    }

    fun send(body: String, ownerOnly: Boolean) {
        if (body.isBlank()) return
        act(R.string.growth_message_sent, { growth.postCaseMessage(caseId, body, ownerOnly) }) {
            _sent.value += 1
            refresh()
        }
    }
}

@HiltViewModel
class NetworkInboxViewModel @Inject constructor(private val growth: GrowthService) : NoticeViewModel() {
    private val _direction = MutableStateFlow("incoming")
    val direction: StateFlow<String> = _direction.asStateFlow()
    private val _state = MutableStateFlow<Load<NetworkRequests>>(Load.Loading)
    val state: StateFlow<Load<NetworkRequests>> = _state.asStateFlow()

    init { refresh() }

    fun setDirection(d: String) {
        if (d == _direction.value) return
        _direction.value = d
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = Load.Loading
            _state.value = growth.networkRequests(_direction.value).toLoad()
        }
    }

    fun decide(id: String, action: String, note: String?) {
        val done = when (action) {
            "approve" -> R.string.growth_request_approved
            "decline" -> R.string.growth_request_declined
            else -> R.string.growth_request_cancelled
        }
        act(done, { growth.networkAction(id, action, note) }) { refresh() }
    }
}

@HiltViewModel
class PayoutsViewModel @Inject constructor(private val growth: GrowthService) : NoticeViewModel() {
    private val _status = MutableStateFlow<Load<ConnectStatus>>(Load.Loading)
    val status: StateFlow<Load<ConnectStatus>> = _status.asStateFlow()
    private val _balance = MutableStateFlow<ConnectBalance?>(null)
    val balance: StateFlow<ConnectBalance?> = _balance.asStateFlow()
    private val _payouts = MutableStateFlow<List<Payout>>(emptyList())
    val payouts: StateFlow<List<Payout>> = _payouts.asStateFlow()
    private val _cursor = MutableStateFlow<String?>(null)
    val hasMore: StateFlow<String?> = _cursor.asStateFlow()
    private val _links = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Hosted Stripe pages to open in the browser. */
    val links: SharedFlow<String> = _links

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _status.value = Load.Loading
            val s = growth.connectStatus()
            _status.value = s.toLoad()
            val st = s.valueOrNull() ?: return@launch
            // Balance and payouts come straight from Stripe and only exist once an account is connected.
            if (!st.available || st.onboardingState == "not_started") {
                _balance.value = null
                _payouts.value = emptyList()
                _cursor.value = null
                return@launch
            }
            _balance.value = growth.connectBalance().valueOrNull()
            when (val p = growth.payouts(null)) {
                is ApiResult.Ok -> {
                    _payouts.value = p.value.items
                    _cursor.value = p.value.nextCursor
                }
                is ApiResult.Err -> {
                    _payouts.value = emptyList()
                    _cursor.value = null
                }
            }
        }
    }

    fun loadMore() {
        val after = _cursor.value ?: return
        act(null, { growth.payouts(after) }) { page ->
            _payouts.value = _payouts.value + page.items
            _cursor.value = page.nextCursor
        }
    }

    fun openOnboarding() = act(null, { growth.onboardingLink() }) { _links.emit(it) }

    fun openDashboard() = act(null, { growth.dashboardLink() }) { _links.emit(it) }
}

@HiltViewModel
class ReferralsViewModel @Inject constructor(private val growth: GrowthService) : NoticeViewModel() {
    private val _state = MutableStateFlow<Load<Referrals>>(Load.Loading)
    val state: StateFlow<Load<Referrals>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = Load.Loading
            _state.value = growth.referrals().toLoad()
        }
    }
}

/** Renter portal opened from the e-mailed magic link: verification status and "my reviews". */
@HiltViewModel
class PortalViewModel @Inject constructor(
    private val portal: PortalService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    private val linkToken: String? = savedState.get<String>("token")?.takeIf { it.isNotBlank() }
    private val _session = MutableStateFlow<Load<PortalSession>>(Load.Loading)
    val session: StateFlow<Load<PortalSession>> = _session.asStateFlow()
    private val _verification = MutableStateFlow<Load<Verification>>(Load.Loading)
    val verification: StateFlow<Load<Verification>> = _verification.asStateFlow()
    private val _reviews = MutableStateFlow<Load<List<PortalReview>>>(Load.Loading)
    val reviews: StateFlow<Load<List<PortalReview>>> = _reviews.asStateFlow()
    private val _links = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val links: SharedFlow<String> = _links

    init { start() }

    fun start() {
        viewModelScope.launch {
            _session.value = Load.Loading
            // A link token is single-use: reuse the in-memory session when the screen is recreated.
            val existing = portal.session
            _session.value = when {
                existing != null -> Load.Ready(existing)
                linkToken != null -> portal.open(linkToken).toLoad()
                else -> Load.Failed(com.avago.core.network.json.ApiError(401, "portal_signed_out", ""))
            }
            if (_session.value is Load.Ready) loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _verification.value = portal.verification().toLoad()
            _reviews.value = portal.myReviews().toLoad()
        }
    }

    fun verify(purpose: String) = act(null, { portal.startVerification(purpose) }) { _links.emit(it) }

    override fun onCleared() {
        // Session token lives only as long as the portal screen.
        portal.signOut()
    }
}
