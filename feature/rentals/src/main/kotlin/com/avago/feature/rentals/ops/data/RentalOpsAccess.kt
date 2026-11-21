package com.avago.feature.rentals.ops.data

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
    /** Statement PDF / e-mail (stage C, `rental.reports`); viewing needs only `rental.view`. */
    val sendStatements: Boolean = false,
    /** Holds `rental.drive` (has a personal delivery list). */
    val drive: Boolean = false,
) {
    val any: Boolean get() = tasks || assign || transfers || statements || aging

    /** Back-office tools grouped under the rentals "Operations" entry. */
    val operations: Boolean get() = dispatch || assign || transfers || statements || aging

    /** Drivers without `rental.view` have no counter screens: Rentals lands on My deliveries. */
    val driverOnly: Boolean get() = drive && !statements

    companion object {
        val NONE = RentalOpsAccess(false, false, false, false, false, false)

        fun from(perms: Set<String>, root: Boolean): RentalOpsAccess {
            fun has(k: String) = root || k in perms
            val view = has("rental.view")
            return RentalOpsAccess(
                tasks = has("rental.drive") || has("rental.dispatch") || view,
                dispatch = has("rental.dispatch"),
                // The work queue (unassigned-lines) is rental.view; assign-unit is rental.reserve.
                assign = has("rental.reserve") && view,
                transfers = has("rental.transfer"),
                statements = view,
                aging = has("rental.reports"),
                sendStatements = has("rental.reports"),
                drive = has("rental.drive"),
            )
        }
    }
}
