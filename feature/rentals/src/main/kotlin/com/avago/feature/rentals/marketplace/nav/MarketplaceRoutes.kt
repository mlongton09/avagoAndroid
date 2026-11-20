package com.avago.feature.rentals.marketplace.nav

import java.net.URLEncoder

/**
 * Avago Marketplace destinations (docs/rentals-marketplace-android.md). Renter routes are public:
 * they work without a staff sign-in ([isPublic]); owner routes need the staff permissions.
 */
object MarketplaceRoutes {
    const val PREFIX = "rentals/mkt/"
    private const val OWNER_PREFIX = "rentals/mkt/owner/"

    const val SEARCH = "rentals/mkt/search"
    const val LISTING = "rentals/mkt/listing/{listingId}?lat={lat}&lng={lng}&loc={loc}"
    const val RESERVE = "rentals/mkt/listing/{listingId}/reserve"
    const val MEET = "rentals/mkt/listing/{listingId}/meet"
    const val SIGN_IN = "rentals/mkt/signin?next={next}&token={token}"
    const val PROFILE = "rentals/mkt/profile"
    const val REQUESTS = "rentals/mkt/requests"

    const val OWNER_SETTINGS = "rentals/mkt/owner/settings"
    const val OWNER_LISTINGS = "rentals/mkt/owner/listings"
    const val OWNER_MEETUPS = "rentals/mkt/owner/meetups"
    const val OWNER_MEETUP = "rentals/mkt/owner/meetup/{meetupId}"
    const val OWNER_BLOCKS = "rentals/mkt/owner/blocks"

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    fun listing(id: String, lat: Double? = null, lng: Double? = null, locationId: String? = null): String =
        "rentals/mkt/listing/${enc(id)}?lat=${lat?.toString().orEmpty()}&lng=${lng?.toString().orEmpty()}&loc=${enc(locationId.orEmpty())}"

    fun reserve(id: String) = "rentals/mkt/listing/${enc(id)}/reserve"
    fun meet(id: String) = "rentals/mkt/listing/${enc(id)}/meet"

    /** Sign-in form ([next] = web path to continue at) or magic-link completion ([token]). */
    fun signIn(next: String? = null, token: String? = null): String =
        "rentals/mkt/signin?next=${enc(next.orEmpty())}&token=${enc(token.orEmpty())}"

    fun ownerMeetup(id: String) = "rentals/mkt/owner/meetup/${enc(id)}"

    /** Renter screens work signed out of the staff app (no redirect to staff sign-in, no app chrome). */
    fun isPublic(route: String?): Boolean = route != null && route.startsWith(PREFIX) && !route.startsWith(OWNER_PREFIX)

    fun isOwner(route: String?): Boolean = route?.startsWith(OWNER_PREFIX) == true

    private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    /**
     * Maps a web marketplace link (`/marketplace`, `/marketplace/{id}`, `/marketplace/requests`,
     * `/marketplace/auth?token=…&next=…`) to an app route; null when the app has no equivalent.
     */
    fun fromWebPath(path: String, query: Map<String, String> = emptyMap()): String? {
        val parts = path.trim().trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (parts.firstOrNull() != "marketplace") return null
        return when {
            parts.size == 1 -> SEARCH
            parts.size == 2 && parts[1] == "requests" -> REQUESTS
            parts.size == 2 && parts[1] == "auth" -> {
                val token = query["token"]?.takeIf { it.isNotBlank() && it.length <= 512 }
                signIn(next = query["next"]?.takeIf { it.startsWith("/marketplace") && !it.contains("//") }, token = token)
            }
            parts.size == 2 && SAFE_ID.matches(parts[1]) -> listing(parts[1])
            else -> null
        }
    }
}
