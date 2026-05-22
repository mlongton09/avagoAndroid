package com.avago.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncGate @Inject constructor() {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    /** Called by SyncEngine after the first successful pull cycle completes. */
    fun open() { _isOpen.value = true }

    /** Called on sign-out to reset for the next account. */
    fun reset() { _isOpen.value = false }

    /**
     * Suspends until the gate is open. Call from any DAO/repository write path
     * that should not execute before the first sync completes.
     */
    suspend fun awaitOpen() {
        _isOpen.filter { it }.first()
    }

    val isOpenNow: Boolean get() = _isOpen.value
}
