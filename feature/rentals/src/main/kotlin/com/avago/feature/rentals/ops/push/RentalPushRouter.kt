package com.avago.feature.rentals.ops.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Maps a stage C rental push (`rentals-api-stage-c.md` §7) to a channel, text and in-app route.
 *
 * The server sends data-only FCM messages; every value is a string (FCM flattens JSON), so a
 * JSON null arrives as the literal `"null"` and `loc_args` as a JSON-encoded array
 * (`["R-1042","pickup"]`). Both are normalised here.
 *
 * ```
 * { type: "rental.task_assigned", account_id, order_id, entity_type, entity_id, task_id, kind,
 *   number, title, body, category: "RENTAL_PICKUP",
 *   title_loc_key: "rental_task_assigned_pickup_title", body_loc_key: …, loc_args: ["R-1042","pickup"] }
 * ```
 */
object RentalPushRouter {

    /** Server `category` → Android channel (one channel per category so users can mute each). */
    enum class Category(val wire: String, val channelId: String) {
        BOOKING_REQUEST("RENTAL_BOOKING_REQUEST", "rental_booking_request"),
        PICKUP_REQUESTED("RENTAL_PICKUP_REQUESTED", "rental_pickup_requested"),
        DELIVERY("RENTAL_DELIVERY", "rental_delivery"),
        PICKUP("RENTAL_PICKUP", "rental_pickup"),
        TRANSFER("RENTAL_TRANSFER", "rental_transfer"),
        RETURN_DUE("RENTAL_RETURN_DUE", "rental_return_due"),
        INVOICE("RENTAL_INVOICE", "rental_invoice"),
        /** Wave 2 G2: document expiring/expired, credit application submitted. */
        CREDIT("RENTAL_CREDIT", "rental_credit"),
        /** Wave 2 G2: dunning put a customer account on hold. */
        COLLECTIONS("RENTAL_COLLECTIONS", "rental_collections"),
        /** Wave 2 G3: a sub-rented unit is due back to its supplier. */
        SUBRENTAL_DUE("RENTAL_SUBRENTAL_DUE", "rental_subrental_due"),
        /** Central marketplace: a renter asked to meet (or cancelled / accepted a counter). */
        MEETUP_REQUEST("RENTAL_MEETUP_REQUEST", "rental_meetup_request"),
        ;

        companion object {
            fun fromWire(s: String?): Category? = entries.firstOrNull { it.wire == s }
        }
    }

    /** Stage C rental events (`notify.rs EVENTS`), the keys of `notification-prefs`. */
    val EVENTS = listOf(
        "rental.booking_request",
        "rental.off_rent_requested",
        "rental.task_assigned",
        "rental.task_completed",
        "rental.task_failed",
        "rental.overdue",
        "rental.cycle_invoiced",
        // Wave 2 (G2 credit/collections, G3 sub-rent).
        "rental.doc_expiring",
        "rental.credit_application",
        "rental.dunning_hold",
        "rental.subrental_return_due",
        // Central marketplace (rentals-api-marketplace.md §8).
        "rental.marketplace_meetup",
    )

    data class Spec(
        /** Full event name, e.g. `rental.task_assigned`. */
        val event: String,
        val category: Category,
        val route: String,
        /** Server English fallback text. */
        val title: String?,
        val body: String?,
        val titleLocKey: String?,
        val bodyLocKey: String?,
        val locArgs: List<String>,
        val orderNumber: String?,
        val accountId: String?,
        /** Stable per subject so updates replace rather than stack. */
        val notificationKey: String,
    )

    fun handles(type: String?): Boolean = type != null && type.startsWith("rental.")

    /** FCM string value → null for absent, blank or the flattened JSON null `"null"`. */
    fun value(data: Map<String, String>, key: String): String? =
        data[key]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private val lenient = Json { isLenient = true }

    /** `loc_args` as sent over FCM (JSON array string); a bare string counts as one argument. */
    fun locArgs(raw: String?): List<String> {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return emptyList()
        if (!s.startsWith("[")) return listOf(s)
        val arr = runCatching { lenient.parseToJsonElement(s) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }
    }

    fun route(data: Map<String, String>): Spec? {
        val event = value(data, "type")?.takeIf(::handles) ?: return null
        val kind = value(data, "kind")
        val taskId = value(data, "task_id") ?: value(data, "entity_id").takeIf { value(data, "entity_type") == "rental_delivery_task" }
        val orderId = value(data, "order_id")
        val customerId = value(data, "customer_id")
        val meetupId = value(data, "meetup_id") ?: value(data, "entity_id").takeIf { value(data, "entity_type") == "rental_meetup" }
        val category = Category.fromWire(value(data, "category")) ?: fallbackCategory(event, kind) ?: return null
        val route = when {
            event.startsWith("rental.task_") && taskId != null -> RentalOpsRoutes.task(taskId)
            event.startsWith("rental.task_") -> RentalOpsRoutes.TASKS
            // Marketplace meet-up: the owner's request detail, else the meet-up inbox.
            event == "rental.marketplace_meetup" && meetupId != null && RentalOpsRoutes.SAFE_ID.matches(meetupId) ->
                RentalOpsRoutes.mktMeetup(meetupId)
            event == "rental.marketplace_meetup" -> RentalOpsRoutes.MKT_MEETUPS
            orderId != null && RentalOpsRoutes.SAFE_ID.matches(orderId) -> RentalOpsRoutes.p1Order(orderId)
            // G2 credit / collections pushes are about a customer (document, application, hold).
            customerId != null && RentalOpsRoutes.SAFE_ID.matches(customerId) -> RentalOpsRoutes.p1Customer(customerId)
            event == "rental.cycle_invoiced" -> RentalOpsRoutes.AGING
            else -> RentalOpsRoutes.P1_HOME
        }
        val subject = taskId ?: meetupId ?: orderId ?: value(data, "entity_id") ?: customerId ?: event
        return Spec(
            event = event,
            category = category,
            route = route,
            title = value(data, "title"),
            body = value(data, "body") ?: value(data, "message"),
            titleLocKey = value(data, "title_loc_key"),
            bodyLocKey = value(data, "body_loc_key"),
            locArgs = locArgs(data["loc_args"]),
            // Not loc_args[0]: the server drops unknown values, so with no number [0] is the kind.
            orderNumber = value(data, "number") ?: value(data, "order_number"),
            accountId = value(data, "account_id"),
            notificationKey = "rental:$event:$subject",
        )
    }

    /** Older/partial payloads without `category`: derive it as the server does. */
    private fun fallbackCategory(event: String, kind: String?): Category? = when {
        event == "rental.booking_request" -> Category.BOOKING_REQUEST
        event == "rental.off_rent_requested" -> Category.PICKUP_REQUESTED
        event.startsWith("rental.task_") -> when (kind) {
            "pickup" -> Category.PICKUP
            "transfer" -> Category.TRANSFER
            else -> Category.DELIVERY
        }
        event == "rental.overdue" -> Category.RETURN_DUE
        event == "rental.cycle_invoiced" -> Category.INVOICE
        event == "rental.doc_expiring" || event == "rental.credit_application" -> Category.CREDIT
        event == "rental.dunning_hold" -> Category.COLLECTIONS
        event == "rental.subrental_return_due" -> Category.SUBRENTAL_DUE
        event == "rental.marketplace_meetup" -> Category.MEETUP_REQUEST
        // G6 payment events carry no server category; they belong with invoices.
        event.startsWith("rental.payment_") || event == "rental.autopay_failed" || event == "rental.deposit_auth_expiring" -> Category.INVOICE
        else -> null
    }
}

/** In-app routes owned by this module (all start with [PREFIX]) plus the P1 targets pushes open. */
object RentalOpsRoutes {
    const val PREFIX = "rentalops/"
    const val HOME = "rentalops/home"
    const val TASKS = "rentalops/tasks"
    const val TASK = "rentalops/task/{taskId}"
    const val ASSIGN = "rentalops/assign"
    const val ASSIGN_LINE = "rentalops/assign/{lineId}"
    const val TRANSFERS = "rentalops/transfers"
    const val STATEMENT = "rentalops/statement"
    const val AGING = "rentalops/aging"
    const val SETTINGS = "rentalops/settings"
    /** Wave 2 G5 driver route (`routing/my-route`). */
    const val ROUTE = "rentalops/route"

    /**
     * P1 (feature:rentals) routes, as literals because this module must not depend on
     * feature:rentals. Keep in sync with `RentalsRoute.HOME` / `RentalsRoute.order()`.
     */
    const val P1_PREFIX = "rentals/"
    const val P1_HOME = "rentals/home"
    fun p1Order(orderId: String) = "rentals/order/$orderId"
    fun p1Customer(customerId: String) = "rentals/customer/$customerId"

    /**
     * Marketplace owner routes (feature:rentals `MarketplaceRoutes.OWNER_MEETUPS` /
     * `MarketplaceRoutes.ownerMeetup()`), literals for the same reason.
     */
    const val MKT_MEETUPS = "rentals/mkt/owner/meetups"
    fun mktMeetup(meetupId: String) = "rentals/mkt/owner/meetup/$meetupId"

    /** Intent extra MainActivity forwards to navigation (only [isSafe] routes are accepted). */
    const val EXTRA_ROUTE = "com.avago.extra.RENTALOPS_ROUTE"

    internal val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")

    fun task(id: String) = "rentalops/task/$id"
    fun assignLine(id: String) = "rentalops/assign/$id"

    /**
     * Guards deep links: this module's static routes, `task/{id}` / `assign/{id}`, P1's
     * `rentals/home` / `rentals/order/{id}` / `rentals/customer/{id}`, and the marketplace
     * owner inbox `rentals/mkt/owner/meetups` / `rentals/mkt/owner/meetup/{id}`, each with a safe id.
     */
    fun isSafe(route: String?): Boolean {
        if (route == null || route.length > 200) return false
        if (route.startsWith(P1_PREFIX)) {
            val parts = route.removePrefix(P1_PREFIX).split('/')
            return when (parts.size) {
                1 -> parts[0] == "home"
                2 -> parts[0] in setOf("order", "customer") && SAFE_ID.matches(parts[1])
                3 -> route == MKT_MEETUPS
                4 -> parts[0] == "mkt" && parts[1] == "owner" && parts[2] == "meetup" && SAFE_ID.matches(parts[3])
                else -> false
            }
        }
        if (!route.startsWith(PREFIX)) return false
        val parts = route.removePrefix(PREFIX).split('/')
        return when (parts.size) {
            1 -> parts[0] in setOf("home", "tasks", "route", "assign", "transfers", "statement", "aging", "settings")
            2 -> parts[0] in setOf("task", "assign") && SAFE_ID.matches(parts[1])
            else -> false
        }
    }
}
