package com.avago.core.data

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide dispatcher that bridges Scout responses to active form screens.
 *
 * Any Composable screen that accepts Scout pre-fill calls [register] when it
 * enters composition and [unregister] when it leaves. When a Scout result
 * arrives with [fields], the caller invokes [dispatch] and the router forwards
 * the payload to the matching handler — or buffers it so it can be replayed
 * when the target screen mounts.
 *
 * Usage in a form ViewModel:
 * ```kotlin
 * @HiltViewModel
 * class MyFormViewModel @Inject constructor(
 *     private val formFillRouter: FormFillRouter,
 * ) : ViewModel() {
 *     init {
 *         formFillRouter.register("my_screen_id") { fields ->
 *             applyScoutFields(fields)
 *         }
 *     }
 *     override fun onCleared() {
 *         formFillRouter.unregister("my_screen_id")
 *         super.onCleared()
 *     }
 * }
 * ```
 *
 * Moved from core:ai to core:data so all feature modules can access it without
 * pulling in the full AI dependency graph.
 */
@Singleton
class FormFillRouter @Inject constructor() {

    private val handlers = mutableMapOf<String, (Map<String, String?>) -> List<String>>()

    /** Pending payloads buffered when no handler is mounted yet (one per screen). */
    private val pending = mutableMapOf<String, Map<String, String?>>()

    /**
     * Register a form screen as the target for fill operations.
     *
     * If a pending payload exists for [screenId] it is replayed immediately.
     *
     * @param screenId  Nav route that uniquely identifies this form screen.
     * @param handler   Block that applies [fields] to the form and returns
     *                  human-readable names of changed fields.
     */
    fun register(screenId: String, handler: (Map<String, String?>) -> List<String>) {
        handlers[screenId] = handler
        pending.remove(screenId)?.let { buffered ->
            Timber.d("FormFillRouter: replaying buffered payload for '$screenId'")
            handler(buffered)
        }
    }

    /** Remove the handler when the screen leaves composition. */
    fun unregister(screenId: String) {
        handlers.remove(screenId)
    }

    /**
     * Dispatch a Scout payload to the handler for [targetScreen].
     *
     * If no handler is registered the payload is buffered and replayed when a
     * matching handler registers.
     *
     * @return Human-readable list of changed field names, or empty if buffered.
     */
    fun dispatch(targetScreen: String, fields: Map<String, String?>): List<String> {
        if (fields.isEmpty()) return emptyList()
        val handler = handlers[targetScreen]
        return if (handler != null) {
            Timber.d("FormFillRouter: dispatching ${fields.size} fields to '$targetScreen'")
            handler(fields)
        } else {
            Timber.d("FormFillRouter: no handler for '$targetScreen', buffering")
            pending[targetScreen] = fields
            emptyList()
        }
    }

    /** Clear all pending buffers (call on sign-out or account switch). */
    fun clearPending() {
        pending.clear()
    }
}
