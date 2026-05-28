package com.avago.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is the avago AI bot reachable on this server, in this session."
 *
 * Default state on launch: available = true (optimistic). If an AI call returns 404, 503 with
 * "ai_bot disabled", or a connection error, we flip to unavailable for the rest of the session.
 * On every app foreground, optimistically reset to true — if AI is genuinely down the next tap
 * will flip it back; cost is one extra failed call after a foreground.
 *
 * Mirrors iOS AIAvailability.swift.
 */
@Singleton
class AIAvailability @Inject constructor() {

    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /** Call when an AI call returns 404, 503, or a sustained connection error. */
    fun markUnavailable(reason: String) {
        if (_isAvailable.value) {
            Timber.w("[AIAvailability] disabled for session — reason: $reason")
        }
        _isAvailable.value = false
    }

    /** Call when an AI call succeeds — flips back to available after a transient failure. */
    fun markAvailable() {
        if (!_isAvailable.value) {
            Timber.d("[AIAvailability] re-enabled — successful AI call")
        }
        _isAvailable.value = true
    }

    /** Optimistic reset, intended to be called on app foreground. */
    fun resetForSession() {
        if (!_isAvailable.value) {
            Timber.d("[AIAvailability] foreground reset — re-optimistic")
        }
        _isAvailable.value = true
    }
}
