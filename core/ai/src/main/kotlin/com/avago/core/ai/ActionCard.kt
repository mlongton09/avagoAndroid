package com.avago.core.ai

import kotlinx.serialization.Serializable

/**
 * Action card returned by the server when a Scout response requires
 * explicit user confirmation before the skill is committed.
 *
 * Mirrors iOS AIActionCard. The server sets [expiresAt] to ~5 minutes
 * from the response timestamp; [isExpired] guards stale confirmations.
 *
 * @param dangerous  When true, the action cannot be undone (e.g. delete
 *                   an asset). The confirmation dialog shows an extra
 *                   warning and requires a second explicit tap.
 */
@Serializable
data class ActionCard(
    val title: String,
    val summary: String? = null,
    val skillName: String,
    val dangerous: Boolean = false,
    val expiresAt: Long? = null, // epoch-millis
) {
    val isExpired: Boolean
        get() = expiresAt != null && System.currentTimeMillis() > expiresAt
}
