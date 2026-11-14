package com.avago.feature.rentalsops.push

/**
 * Maps a rentals FCM data payload to a notification category and in-app route.
 *
 * TODO(stage C): the server's push payload shape is not final. Stage C stores
 * `rental_notifications(event, data)` with events such as `rental.task_assigned`
 * `{task_id, kind, order_id}`; this router accepts `type` values starting with `rental.` or
 * `rental_` (and an explicit `event` key) and reads `title`, `body`, `task_id`, `order_id`,
 * `transfer_id`, `line_id` when present.
 */
object RentalPushRouter {

    enum class Category(val key: String) {
        TASKS("tasks"),
        TRANSFERS("transfers"),
        ASSIGNMENTS("assignments"),
        DUE("due"),
        OTHER("other"),
    }

    data class Spec(
        val event: String,
        val category: Category,
        val route: String,
        val title: String?,
        val body: String?,
        /** Stable per subject so updates replace rather than stack. */
        val notificationKey: String,
    )

    fun handles(type: String?): Boolean =
        type != null && (type.startsWith("rental.") || type.startsWith("rental_"))

    fun route(data: Map<String, String>): Spec? {
        val type = data["type"]
        if (!handles(type)) return null
        val event = (data["event"]?.takeIf { handles(it) } ?: type!!).replace('_', '.').removePrefix("rental.")
        val taskId = data["task_id"]?.takeIf { it.isNotBlank() }
        val transferId = data["transfer_id"]?.takeIf { it.isNotBlank() }
        val lineId = data["line_id"]?.takeIf { it.isNotBlank() }
        val category = when {
            event.startsWith("task") || event.startsWith("delivery") || event.startsWith("pickup") -> Category.TASKS
            event.startsWith("transfer") -> Category.TRANSFERS
            event.startsWith("unit.assign") || event.startsWith("assign") || event.contains("allocation") -> Category.ASSIGNMENTS
            event.contains("overdue") || event.contains("due") || event.contains("return") || event.contains("off.rent") -> Category.DUE
            else -> Category.OTHER
        }
        val route = when {
            category == Category.TASKS && taskId != null -> RentalOpsRoutes.task(taskId)
            category == Category.TASKS -> RentalOpsRoutes.TASKS
            category == Category.TRANSFERS -> RentalOpsRoutes.TRANSFERS
            category == Category.ASSIGNMENTS && lineId != null -> RentalOpsRoutes.assignLine(lineId)
            category == Category.ASSIGNMENTS -> RentalOpsRoutes.ASSIGN
            else -> RentalOpsRoutes.HOME
        }
        val subject = taskId ?: transferId ?: lineId ?: data["order_id"] ?: event
        return Spec(
            event = event,
            category = category,
            route = route,
            title = data["title"]?.takeIf { it.isNotBlank() },
            body = data["body"]?.takeIf { it.isNotBlank() } ?: data["message"]?.takeIf { it.isNotBlank() },
            notificationKey = "rentalops:$event:$subject",
        )
    }
}

/** In-app routes owned by this module (all start with [PREFIX]). */
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

    /** Intent extra MainActivity forwards to navigation (only [PREFIX] routes are accepted). */
    const val EXTRA_ROUTE = "com.avago.extra.RENTALOPS_ROUTE"

    fun task(id: String) = "rentalops/task/$id"
    fun assignLine(id: String) = "rentalops/assign/$id"

    /** Guards deep links: only this module's static routes or `task/{id}` / `assign/{id}` with a safe id. */
    fun isSafe(route: String?): Boolean {
        if (route == null || !route.startsWith(PREFIX) || route.length > 200) return false
        val rest = route.removePrefix(PREFIX)
        val parts = rest.split('/')
        return when (parts.size) {
            1 -> parts[0] in setOf("home", "tasks", "assign", "transfers", "statement", "aging", "settings")
            2 -> parts[0] in setOf("task", "assign") && parts[1].matches(Regex("[A-Za-z0-9-]{1,64}"))
            else -> false
        }
    }
}
