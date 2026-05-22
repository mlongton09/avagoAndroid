package com.avago.core.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.network.model.AiSkillResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing [ScoutPaletteSheet] and [VoiceInputSheet].
 *
 * Owns the request lifecycle so that configuration changes (rotation,
 * etc.) don't cancel in-flight Scout calls. The UI collects [state]
 * and calls [query] or [reset].
 */
@HiltViewModel
class ScoutViewModel @Inject constructor(
    private val extractor: AiExtractor,
    private val contextHost: ScoutContextHost,
) : ViewModel() {

    sealed class ScoutState {
        /** No query in flight; palette is empty. */
        object Idle : ScoutState()

        /** Request dispatched; spinner is shown. */
        object Loading : ScoutState()

        /** Server replied successfully. */
        data class Result(val response: ScoutResponse) : ScoutState()

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
            _state.value = extractor.nlSearch(trimmed, ctx).fold(
                onSuccess = { ScoutState.Result(it) },
                onFailure = { ScoutState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    /** Reset to [ScoutState.Idle] — call after navigation is completed. */
    fun reset() {
        _state.value = ScoutState.Idle
    }
}
