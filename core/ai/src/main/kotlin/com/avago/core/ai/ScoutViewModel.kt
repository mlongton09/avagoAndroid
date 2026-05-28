package com.avago.core.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FormFillRouter
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.model.AiSkillResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing [ScoutPaletteSheet] and [VoiceInputSheet].
 *
 * Owns the request lifecycle so that configuration changes (rotation,
 * etc.) don't cancel in-flight Scout calls. The UI collects [state]
 * and calls [query] or [reset].
 *
 * HITL-off path: when the user has disabled Human-in-the-Loop, a
 * successful Scout reply is forwarded to [ScoutSkillExecutor] first.
 * If the executor commits the change directly [ScoutState.Executed] is
 * emitted (no form navigation needed); otherwise the normal Result /
 * form-fill flow proceeds.
 */
@HiltViewModel
class ScoutViewModel @Inject constructor(
    private val extractor: AiExtractor,
    private val contextHost: ScoutContextHost,
    private val executor: ScoutSkillExecutor,
    private val prefs: UserPreferencesRepository,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    sealed class ScoutState {
        /** No query in flight; palette is empty. */
        object Idle : ScoutState()

        /** Request dispatched; spinner is shown. */
        object Loading : ScoutState()

        /** Server replied successfully. */
        data class Result(val response: ScoutResponse) : ScoutState()

        /**
         * HITL-off: executor committed the change directly.
         * UI should show a brief confirmation toast then dismiss.
         */
        data class Executed(val message: String) : ScoutState()

        /** Server returned an error. */
        data class Error(val message: String) : ScoutState()
    }

    private val _state = MutableStateFlow<ScoutState>(ScoutState.Idle)
    val state: StateFlow<ScoutState> = _state

    private val _skills = MutableStateFlow<List<AiSkillResponse>>(emptyList())
    val skills: StateFlow<List<AiSkillResponse>> = _skills

    init {
        loadSkills()
    }

    private fun loadSkills() {
        viewModelScope.launch {
            extractor.getSkills().onSuccess { _skills.value = it }
        }
    }

    /**
     * Dispatch a Scout query.  No-op while a request is already in
     * flight (guards against double-tap on quick-action chips).
     */
    fun query(text: String) {
        if (_state.value is ScoutState.Loading) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _state.value = ScoutState.Loading
            val ctx = contextHost.currentContext()
            val result = extractor.nlSearch(trimmed, ctx)
            _state.value = result.fold(
                onSuccess = { response ->
                    val hitlEnabled = prefs.enableHumanInLoopFlow.first()
                    // Only try direct execution when HITL is off AND the server
                    // didn't send an action card (action cards always need explicit
                    // user confirmation, regardless of HITL setting).
                    if (!hitlEnabled && response.actionCard == null) {
                        val skillName = response.skillName ?: ""
                        val executed = executor.executeIfPossible(skillName, response.fields)
                        if (executed) {
                            return@fold ScoutState.Executed("Done")
                        }
                    }
                    ScoutState.Result(response)
                },
                onFailure = { ScoutState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    /**
     * Called by the UI when the user confirms an action card for a skill
     * that has no form target (e.g. work-order-action, work-order-assign).
     * Runs the executor directly and emits [ScoutState.Executed].
     */
    fun executeAction(fields: Map<String, String?>) {
        viewModelScope.launch {
            val skillName = fields["skill_name"] ?: fields["_skill"] ?: return@launch
            val executed = executor.executeIfPossible(skillName, fields)
            _state.value = if (executed) ScoutState.Executed("Done")
                           else ScoutState.Error("Could not execute action")
        }
    }

    /**
     * Buffer Scout fields in [FormFillRouter] for [targetScreen].
     * Called by the UI just before navigating, so the payload is ready
     * when the target form's ViewModel registers with the router in its init.
     */
    fun dispatchFormFill(targetScreen: String, fields: Map<String, String?>) {
        formFillRouter.dispatch(targetScreen, fields)
    }

    /** Reset to [ScoutState.Idle] — call after navigation is completed. */
    fun reset() {
        _state.value = ScoutState.Idle
    }
}
