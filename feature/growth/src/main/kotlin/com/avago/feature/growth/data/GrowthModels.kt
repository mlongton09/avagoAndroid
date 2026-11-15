package com.avago.feature.growth.data

import com.avago.core.network.json.bool
import com.avago.core.network.json.double
import com.avago.core.network.json.int
import com.avago.core.network.json.long
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonObject

// ---- Reviews (E, AvagoSvc docs/rentals-api-trust.md) ----------------------------------

/** A renter's published review of this owner. Scores are 1–5. */
data class OwnerReview(
    val id: String,
    val orderNumber: String?,
    val customerName: String?,
    val status: String,
    val itemCondition: Int?,
    val listingAccuracy: Int?,
    val ownerCommunication: Int?,
    val handoverDelivery: Int?,
    val body: String?,
    val revealedAt: String?,
    val ownerReply: String?,
    val reportedAt: String?,
) {
    /** Mean of the four scores the renter gave. */
    val average: Double? get() = listOfNotNull(itemCondition, listingAccuracy, ownerCommunication, handoverDelivery)
        .takeIf { it.isNotEmpty() }?.average()

    /** The owner may reply once, to a published review. */
    val canReply: Boolean get() = status == "published" && ownerReply.isNullOrBlank()

    companion object {
        fun from(o: JsonObject): OwnerReview? {
            val id = o.str("id") ?: return null
            return OwnerReview(
                id = id,
                orderNumber = o.str("order_number"),
                customerName = o.str("customer_name"),
                status = o.str("status") ?: "published",
                itemCondition = o.int("item_condition"),
                listingAccuracy = o.int("listing_accuracy"),
                ownerCommunication = o.int("owner_communication"),
                handoverDelivery = o.int("handover_delivery"),
                body = o.str("body"),
                revealedAt = o.str("revealed_at"),
                ownerReply = o.str("owner_reply"),
                reportedAt = o.str("reported_at"),
            )
        }
    }
}

/** A completed booking still waiting for the owner's private renter rating. */
data class ToRate(val orderId: String, val orderNumber: String?, val customerName: String?, val windowEndsAt: String?) {
    companion object {
        fun from(o: JsonObject): ToRate? = o.str("order_id")?.let { ToRate(it, o.str("order_number"), o.str("customer_name"), o.str("window_ends_at")) }
    }
}

data class RatingSummary(val reviewCount: Int, val ownerAverage: Double?, val assetAverage: Double?, val newOwner: Boolean) {
    companion object {
        fun from(o: JsonObject?) = RatingSummary(
            reviewCount = o?.int("review_count") ?: 0,
            ownerAverage = o?.obj("owner")?.double("average"),
            assetAverage = o?.obj("asset")?.double("average"),
            newOwner = o?.bool("new_owner") == true,
        )
    }
}

data class OwnerReviews(val reviews: List<OwnerReview>, val toRate: List<ToRate>, val summary: RatingSummary) {
    companion object {
        fun from(o: JsonObject) = OwnerReviews(
            reviews = o.objs("reviews").mapNotNull(OwnerReview::from),
            toRate = o.objs("to_rate").mapNotNull(ToRate::from),
            summary = RatingSummary.from(o.obj("summary")),
        )
    }
}

/** Private owner → renter rating; each score 1–5. */
data class RenterRating(val onTime: Int, val care: Int, val communication: Int, val body: String?) {
    val valid: Boolean get() = listOf(onTime, care, communication).all { it in 1..5 }
}

// ---- Cases ------------------------------------------------------------------------------

data class TrustCase(
    val id: String,
    val caseNumber: String?,
    val caseType: String,
    val severity: String?,
    val status: String,
    val subject: String,
    val description: String?,
    val orderNumber: String?,
    val customerName: String?,
    val resolveDue: String?,
    val slaBreached: Boolean,
    val outcomeCode: String?,
    val outcomeNote: String?,
    val createdAt: String?,
) {
    val open: Boolean get() = status != "resolved" && status != "closed"

    companion object {
        fun from(o: JsonObject): TrustCase? {
            val id = o.str("id") ?: return null
            return TrustCase(
                id = id,
                caseNumber = o.str("case_number"),
                caseType = o.str("case_type") ?: "other",
                severity = o.str("severity"),
                status = o.str("status") ?: "open",
                subject = o.str("subject") ?: "",
                description = o.str("description"),
                orderNumber = o.str("order_number"),
                customerName = o.str("customer_name"),
                resolveDue = o.str("resolve_due"),
                slaBreached = o.bool("sla_breached") == true,
                outcomeCode = o.str("outcome_code"),
                outcomeNote = o.str("outcome_note"),
                createdAt = o.str("created_at"),
            )
        }
    }
}

data class CaseEvent(val id: String, val kind: String, val actor: String?, val visibility: String?, val body: String?, val photos: Int, val createdAt: String?) {
    companion object {
        fun from(o: JsonObject): CaseEvent? = o.str("id")?.let {
            CaseEvent(it, o.str("kind") ?: "message", o.str("actor"), o.str("visibility"), o.str("body"), o.arrSize("photos"), o.str("created_at"))
        }
    }
}

data class CaseDetail(val case: TrustCase, val events: List<CaseEvent>) {
    companion object {
        fun from(o: JsonObject): CaseDetail? = TrustCase.from(o)?.let { CaseDetail(it, o.objs("events").mapNotNull(CaseEvent::from)) }
    }
}

// ---- Network requests (A, docs/rentals-api-instant-network.md §3.3) -------------------

data class NetworkRequest(
    val id: String,
    val direction: String,
    val status: String,
    val startAt: String?,
    val endAt: String?,
    val expiresAt: String?,
    val borrowerName: String?,
    val lenderName: String?,
    val itemName: String?,
    val amountMinor: Long?,
    val currency: String?,
    val borrowerNote: String?,
    val lenderNote: String?,
    val settlementStatus: String?,
) {
    val incoming: Boolean get() = direction == "incoming"
    val pending: Boolean get() = status == "pending"
    val canApprove: Boolean get() = incoming && pending
    val canCancel: Boolean get() = pending

    /** The other shop: the borrower for incoming requests, the lender for outgoing ones. */
    val counterparty: String? get() = if (incoming) borrowerName else lenderName

    companion object {
        fun from(o: JsonObject): NetworkRequest? {
            val id = o.str("id") ?: return null
            return NetworkRequest(
                id = id,
                direction = o.str("direction") ?: "incoming",
                status = o.str("status") ?: "pending",
                startAt = o.str("start_at"),
                endAt = o.str("end_at"),
                expiresAt = o.str("expires_at"),
                borrowerName = o.str("borrower_name"),
                lenderName = o.str("lender_name"),
                itemName = o.str("asset_name") ?: o.str("class_name"),
                amountMinor = o.long("amount_minor"),
                currency = o.str("currency"),
                borrowerNote = o.str("borrower_note"),
                lenderNote = o.str("lender_note"),
                settlementStatus = o.str("settlement_status"),
            )
        }
    }
}

data class NetworkRequests(val requests: List<NetworkRequest>, val pendingIncoming: Int) {
    companion object {
        fun from(o: JsonObject) = NetworkRequests(o.objs("requests").mapNotNull(NetworkRequest::from), o.int("pending_incoming") ?: 0)
    }
}

// ---- Connect payouts (K) ----------------------------------------------------------------

data class ConnectStatus(
    val available: Boolean,
    val onboardingState: String,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val disabledReason: String?,
    val currentlyDue: Int,
    val pastDue: Int,
    val businessName: String?,
    val defaultCurrency: String?,
) {
    /** Onboarding can be (re)started; Stripe needs more from the owner. */
    val needsOnboarding: Boolean get() = onboardingState != "complete" && onboardingState != "pending_verification"

    companion object {
        fun from(o: JsonObject): ConnectStatus {
            val acct = o.obj("account")
            val req = acct?.obj("requirements")
            return ConnectStatus(
                available = o.bool("connect_available") == true,
                onboardingState = o.str("onboarding_state") ?: "not_started",
                chargesEnabled = acct?.bool("charges_enabled") == true,
                payoutsEnabled = acct?.bool("payouts_enabled") == true,
                detailsSubmitted = acct?.bool("details_submitted") == true,
                disabledReason = acct?.str("disabled_reason"),
                currentlyDue = req?.arrSize("currently_due") ?: 0,
                pastDue = req?.arrSize("past_due") ?: 0,
                businessName = acct?.str("business_name"),
                defaultCurrency = acct?.str("default_currency"),
            )
        }
    }
}

data class Money(val amountMinor: Long, val currency: String)

data class ConnectBalance(val available: List<Money>, val pending: List<Money>) {
    companion object {
        private fun list(o: JsonObject, k: String) = o.objs(k).mapNotNull { m ->
            val c = m.str("currency") ?: return@mapNotNull null
            Money(m.long("amount_minor") ?: 0L, c)
        }

        fun from(o: JsonObject) = ConnectBalance(list(o, "available"), list(o, "pending"))
    }
}

/** Stripe payout; [arrivalDate] and [created] are Unix seconds. */
data class Payout(val id: String, val amountMinor: Long, val currency: String, val status: String, val arrivalDate: Long?, val created: Long?, val failureMessage: String?) {
    companion object {
        fun from(o: JsonObject): Payout? = o.str("id")?.let {
            Payout(it, o.long("amount_minor") ?: 0L, o.str("currency") ?: "", o.str("status") ?: "", o.long("arrival_date"), o.long("created"), o.str("failure_message"))
        }
    }
}

data class PayoutPage(val items: List<Payout>, val nextCursor: String?) {
    companion object {
        fun from(o: JsonObject) = PayoutPage(o.objs("items").mapNotNull(Payout::from), o.str("next_cursor").takeIf { o.bool("has_more") == true })
    }
}

// ---- Referrals (G) ----------------------------------------------------------------------

data class Referral(val id: String, val accountName: String?, val status: String, val blockReason: String?, val signedUpAt: String?, val rewardedAt: String?) {
    companion object {
        fun from(o: JsonObject): Referral? = o.str("id")?.let {
            Referral(it, o.str("account_name"), o.str("status") ?: "signed_up", o.str("block_reason"), o.str("signed_up_at"), o.str("rewarded_at"))
        }
    }
}

data class Referrals(
    val code: String,
    val link: String,
    val rewardChoice: String?,
    val monthlyCap: Int?,
    val holdDays: Int?,
    val signedUp: Int,
    val activated: Int,
    val rewarded: Int,
    val blocked: Int,
    val referrals: List<Referral>,
) {
    companion object {
        fun from(o: JsonObject): Referrals {
            val counts = o.obj("counts")
            return Referrals(
                code = o.str("code").orEmpty(),
                link = o.str("link").orEmpty(),
                rewardChoice = o.str("reward_choice"),
                monthlyCap = o.int("monthly_cap"),
                holdDays = o.obj("activation")?.int("hold_days"),
                signedUp = counts?.int("signed_up") ?: 0,
                activated = counts?.int("activated") ?: 0,
                rewarded = counts?.int("rewarded") ?: 0,
                blocked = counts?.int("blocked") ?: 0,
                referrals = o.objs("referrals").mapNotNull(Referral::from),
            )
        }
    }
}

// ---- Shop trial (H) ---------------------------------------------------------------------

data class TrialStep(val key: String, val done: Boolean, val count: Long?)

data class ShopTrial(
    val eligible: Boolean,
    val ineligibleReason: String?,
    val active: Boolean,
    val daysLeft: Int?,
    val trialDays: Int,
    val endsAt: String?,
    val checklist: List<TrialStep>,
    val checklistDone: Int,
) {
    /** Show the card while a trial runs or can be started. */
    val visible: Boolean get() = active || eligible

    companion object {
        fun from(o: JsonObject) = ShopTrial(
            eligible = o.bool("eligible") == true,
            ineligibleReason = o.str("ineligible_reason"),
            active = o.bool("active") == true,
            daysLeft = o.int("days_left"),
            trialDays = o.int("trial_days") ?: 14,
            endsAt = o.str("ends_at"),
            checklist = o.objs("checklist").mapNotNull { s -> s.str("key")?.let { TrialStep(it, s.bool("done") == true, s.long("count")) } },
            checklistDone = o.int("checklist_done") ?: 0,
        )
    }
}

// ---- Renter portal (E) ------------------------------------------------------------------

data class PortalSession(val token: String, val customerName: String?, val shopName: String?) {
    companion object {
        fun from(o: JsonObject): PortalSession? = o.str("token")?.let {
            val me = o.obj("me")
            PortalSession(it, me?.obj("customer")?.str("name"), me?.obj("storefront")?.str("title"))
        }
    }
}

data class Verification(
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val paymentVerified: Boolean,
    val idVerified: Boolean,
    val licenceVerified: Boolean,
    val goodStanding: Boolean,
    val verifiedName: String?,
    val available: Boolean,
) {
    /** "Verified renter": identity checked and in good standing. */
    val verifiedRenter: Boolean get() = idVerified && goodStanding

    companion object {
        fun from(o: JsonObject): Verification {
            val l = o.obj("levels")
            return Verification(
                emailVerified = l?.bool("email_verified") == true,
                phoneVerified = l?.bool("phone_verified") == true,
                paymentVerified = l?.bool("payment_verified") == true,
                idVerified = l?.bool("id_verified") == true,
                licenceVerified = l?.bool("licence_verified") == true,
                goodStanding = o.bool("good_standing") != false,
                verifiedName = o.str("verified_name"),
                available = o.bool("verification_available") == true,
            )
        }
    }
}

data class PortalReview(
    val orderId: String,
    val orderNumber: String?,
    val windowEndsAt: String?,
    val canSubmit: Boolean,
    val revealed: Boolean,
    val myStatus: String?,
    val myBody: String?,
    val ownerReply: String?,
    val ownerRatingAverage: Double?,
    val ownerRatingBody: String?,
) {
    companion object {
        fun from(o: JsonObject): PortalReview? {
            val id = o.str("order_id") ?: return null
            val mine = o.obj("my_review")
            val theirs = o.obj("owner_rating")
            val scores = listOfNotNull(theirs?.int("on_time"), theirs?.int("care"), theirs?.int("communication"))
            return PortalReview(
                orderId = id,
                orderNumber = o.str("order_number"),
                windowEndsAt = o.str("window_ends_at"),
                canSubmit = o.bool("can_submit") == true,
                revealed = o.bool("revealed") == true,
                myStatus = mine?.str("status"),
                myBody = mine?.str("body"),
                ownerReply = mine?.str("owner_reply"),
                ownerRatingAverage = scores.takeIf { it.isNotEmpty() }?.average(),
                ownerRatingBody = theirs?.str("body"),
            )
        }
    }
}

internal fun JsonObject.arrSize(key: String): Int = (this[key] as? kotlinx.serialization.json.JsonArray)?.size ?: 0