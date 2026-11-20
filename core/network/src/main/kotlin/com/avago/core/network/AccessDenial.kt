package com.avago.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A typed `403` (P4-01b): the token is valid, but its user or its membership on the account is
 * not active. Retrying with the same token can never succeed. A membership denial is answered by
 * switching to another workspace (or signing out); a user denial only by signing out.
 */
enum class AccessDenial(val wire: String, val requiresSignOut: Boolean) {
    MEMBERSHIP_SUSPENDED("membership_suspended", requiresSignOut = false),
    MEMBERSHIP_INACTIVE("membership_inactive", requiresSignOut = false),
    USER_SUSPENDED("user_suspended", requiresSignOut = true),
    USER_DEACTIVATED("user_deactivated", requiresSignOut = true),
    ;

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromCode(code: String?): AccessDenial? = entries.firstOrNull { it.wire == code }

        /**
         * The denial a `403` body names: REST bodies carry it in `code` (with the message in
         * `error`), `/v2` bodies in `error`. Null for any other 403 (a role too low, `/v2`
         * `forbidden`) or a body that is not JSON.
         */
        fun fromBody(body: String?): AccessDenial? {
            val obj = body?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return null
            return fromCode(obj.text("code")) ?: fromCode(obj.text("error"))
        }

        private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

        private val ACCOUNT_PATH = Regex("""^(?:/v2)?/accounts/([^/]+)""")

        /** The account a request path is scoped to (`/accounts/{id}/…`, `/v2/accounts/{id}/…`), if any. */
        fun accountOf(path: String): String? = ACCOUNT_PATH.find(path)?.groupValues?.get(1)
    }
}

/** Receives every typed `403` the authenticated client sees ([AccessDenial]). */
fun interface AccessDenialReporter {
    /** [accountId] is the account in the request path, or null for a user-level route. */
    fun onAccessDenied(denial: AccessDenial, accountId: String?)
}
