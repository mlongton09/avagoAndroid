package com.avago.core.data.v2

/**
 * The typed `403` codes of P4-01b as `/v2` answers them (sync-wire.md error codes): the token is
 * valid, but its user or its membership on the account is not active, so no retry with the same
 * token can succeed. `/v2` answers an invited/removed membership with the generic `forbidden`;
 * on a sync route (open to every active member) that means [MEMBERSHIP_INACTIVE].
 */
object V2AccessDenial {
    const val MEMBERSHIP_SUSPENDED = "membership_suspended"
    const val MEMBERSHIP_INACTIVE = "membership_inactive"
    const val USER_SUSPENDED = "user_suspended"
    const val USER_DEACTIVATED = "user_deactivated"

    val CODES: Set<String> = setOf(MEMBERSHIP_SUSPENDED, MEMBERSHIP_INACTIVE, USER_SUSPENDED, USER_DEACTIVATED)
}
