package com.avago.core.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton ring-buffer of recently visited entities.
 *
 * Feature screens call [track] as the user navigates (e.g. opens an
 * asset detail, opens a work order). [currentContext] snapshots the
 * buffer into a [ScoutContext] that is sent with every Scout query so
 * the server can narrow results without the user retyping what they
 * were looking at.
 *
 * Capacity is capped at [MAX_ENTITIES]. Adding a duplicate id evicts
 * the old entry and re-inserts at the front (MRU semantics matching
 * iOS ScoutContextHost).
 */
@Singleton
class ScoutContextHost @Inject constructor() {

    private val _recentEntities = ArrayDeque<ScoutEntity>(MAX_ENTITIES)
    private var _currentScreen: String = ""

    /** Push or promote [entity] to the front of the MRU buffer. */
    fun track(entity: ScoutEntity) {
        _recentEntities.removeIf { it.id == entity.id }
        _recentEntities.addFirst(entity)
        if (_recentEntities.size > MAX_ENTITIES) _recentEntities.removeLast()
    }

    /** Update the name of the currently visible screen (nav route). */
    fun setCurrentScreen(screen: String) {
        _currentScreen = screen
    }

    /** Snapshot the current context for inclusion in a Scout request. */
    fun currentContext(): ScoutContext = ScoutContext(
        recentEntities = _recentEntities.toList(),
        currentScreen = _currentScreen.ifBlank { null },
    )

    companion object {
        private const val MAX_ENTITIES = 5
    }
}
