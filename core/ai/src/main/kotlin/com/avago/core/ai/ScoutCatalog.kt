package com.avago.core.ai

import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.AiSkillResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared skill-catalog cache for the Scout AI assistant.
 *
 * Fetches GET /accounts/:id/ai/skills lazily on first need and refreshes every 5 minutes.
 * The slash menu and empty-state suggestion chips draw from this cache.
 *
 * Behaves correctly when AI is unavailable: an empty skill list hides the slash menu and chips
 * without surfacing an error.
 *
 * Mirrors iOS ScoutCatalog.swift.
 */
@Singleton
class ScoutCatalog @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
    private val aiAvailability: AIAvailability,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _skills = MutableStateFlow<List<AiSkillResponse>>(emptyList())
    val skills: StateFlow<List<AiSkillResponse>> = _skills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastLoadedMs: Long = 0L

    private companion object {
        const val STALE_AFTER_MS = 5 * 60 * 1000L
    }

    /** Trigger a load if the cache is empty or older than 5 minutes. Idempotent. */
    fun loadIfNeeded() {
        if (_isLoading.value) return
        val now = System.currentTimeMillis()
        if (lastLoadedMs > 0 && now - lastLoadedMs < STALE_AFTER_MS) return
        if (!aiAvailability.isAvailable.value) return
        val accountId = identityManager.activeAccountId.value ?: return

        scope.launch {
            _isLoading.value = true
            try {
                when (val result = serviceClient.getAiSkills(accountId)) {
                    is NetworkResult.Success -> {
                        _skills.value = result.data
                        lastLoadedMs = System.currentTimeMillis()
                        Timber.d("[ScoutCatalog] Loaded ${result.data.size} skills")
                    }
                    else -> Timber.d("[ScoutCatalog] Skills load failed — leaving cache as-is")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Filter the cache for a slash-menu prefix. Empty prefix returns the whole catalog. */
    fun filtered(prefix: String): List<AiSkillResponse> {
        val trimmed = prefix.trim().lowercase()
        if (trimmed.isEmpty()) return _skills.value
        return _skills.value.filter {
            it.skill_id.lowercase().contains(trimmed) ||
                (it.name?.lowercase()?.contains(trimmed) == true)
        }
    }

    /** First few distinct example phrasings for the empty-state chip surface. */
    fun suggestedPhrasings(limit: Int = 3): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (skill in _skills.value) {
            val phrase = skill.example_phrasings.firstOrNull() ?: continue
            out.add(skill.skill_id to phrase)
            if (out.size >= limit) break
        }
        return out
    }
}
