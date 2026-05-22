package com.avago.core.ai

import kotlinx.serialization.Serializable

/**
 * Screen-context envelope sent to the server with every Scout query.
 * Mirrors iOS ScreenContext / ScoutContext.
 */
@Serializable
data class ScoutContext(
    val recentEntities: List<ScoutEntity> = emptyList(),
    val currentScreen: String? = null,
)

/**
 * A single entity the user recently viewed — asset, work order, etc.
 * Used by the server to narrow Scout's search scope without the user
 * having to restate what they were looking at.
 */
@Serializable
data class ScoutEntity(
    /** Discriminator: "asset" | "work_order" | "log_entry" | "part" */
    val type: String,
    val id: String,
    val displayName: String,
    /** Arbitrary extra metadata (e.g. asset make/model, WO status). */
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Server response from POST /accounts/:id/ai/scout.
 *
 * [targetScreen] is a nav route string (e.g. "create_work_order").
 * [fields]       is a map of form-field key → pre-filled value.
 * [envelopeId]   lets the server correlate follow-up calls.
 * [message]      is an optional human-readable reply to show the user.
 * [actionCard]   when present, the user must confirm before the skill executes.
 */
@Serializable
data class ScoutResponse(
    val targetScreen: String? = null,
    val fields: Map<String, String?> = emptyMap(),
    val envelopeId: String,
    val message: String? = null,
    val actionCard: ActionCard? = null,
)
