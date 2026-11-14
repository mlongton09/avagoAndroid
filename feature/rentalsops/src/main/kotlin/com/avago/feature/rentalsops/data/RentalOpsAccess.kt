package com.avago.feature.rentalsops.data

/**
 * UI gating for rentals P2 (server permission keys). This is only about which entries to show:
 * the server enforces every call, and a 403 on a feed clears its cached rows.
 */
data class RentalOpsAccess(
    val tasks: Boolean,
    val dispatch: Boolean,
    val assign: Boolean,
    val transfers: Boolean,
    val statements: Boolean,
    val aging: Boolean,
) {
    val any: Boolean get() = tasks || assign || transfers || statements || aging

    companion object {
        val NONE = RentalOpsAccess(false, false, false, false, false, false)

        fun from(perms: Set<String>, root: Boolean): RentalOpsAccess {
            fun has(k: String) = root || k in perms
            val view = has("rental.view")
            return RentalOpsAccess(
                tasks = has("rental.drive") || has("rental.dispatch") || view,
                dispatch = has("rental.dispatch"),
                assign = has("rental.reserve"),
                transfers = has("rental.transfer"),
                statements = view,
                aging = has("rental.reports"),
            )
        }
    }
}
