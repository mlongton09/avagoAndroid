package com.avago.core.ai

import timber.log.Timber

/**
 * AI bot telemetry events — fire-and-forget logging of Scout interactions.
 *
 * Each event maps to a kind label that matches the iOS dashboard contract.
 * Mirrors iOS Telemetry.swift (Data/AI/Telemetry.swift).
 */
object AiTelemetry {

    sealed class Event {
        data class InvocationInitiated(val surface: String, val skillHint: String? = null) : Event()
        object VoiceStarted : Event()
        data class VoiceCompleted(val durationMs: Int, val charCount: Int) : Event()
        data class FormFillApplied(val skill: String) : Event()
        data class FormFillUndone(val skill: String) : Event()
        data class ActionConfirmed(val skill: String) : Event()
        data class ActionCancelled(val skill: String) : Event()
        data class ThrottleHit(val kind: String) : Event()
        data class Feedback(val skill: String, val sentiment: String) : Event()
    }

    private val Event.kindLabel: String
        get() = when (this) {
            is Event.InvocationInitiated -> "ai_invocation_initiated"
            is Event.VoiceStarted        -> "ai_voice_started"
            is Event.VoiceCompleted      -> "ai_voice_completed"
            is Event.FormFillApplied     -> "ai_form_fill_applied"
            is Event.FormFillUndone      -> "ai_form_fill_undone"
            is Event.ActionConfirmed     -> "ai_action_confirmed"
            is Event.ActionCancelled     -> "ai_action_cancelled"
            is Event.ThrottleHit         -> "ai_throttle_hit"
            is Event.Feedback            -> "ai_feedback"
        }

    private val Event.payload: Map<String, Any>
        get() = when (this) {
            is Event.InvocationInitiated -> buildMap {
                put("surface", surface)
                skillHint?.let { put("skill_hint", it) }
            }
            is Event.VoiceStarted        -> emptyMap()
            is Event.VoiceCompleted      -> mapOf("duration_ms" to durationMs, "char_count" to charCount)
            is Event.FormFillApplied     -> mapOf("skill" to skill)
            is Event.FormFillUndone      -> mapOf("skill" to skill)
            is Event.ActionConfirmed     -> mapOf("skill" to skill)
            is Event.ActionCancelled     -> mapOf("skill" to skill)
            is Event.ThrottleHit         -> mapOf("kind" to kind)
            is Event.Feedback            -> mapOf("skill" to skill, "sentiment" to sentiment)
        }

    /** Post an event. Fire-and-forget — never block the AI flow on telemetry. */
    fun post(event: Event) {
        Timber.d("[ai.telemetry] ${event.kindLabel} ${event.payload}")
        // Future: fan out to analytics SDK here (Firebase, Mixpanel, etc.)
    }
}
