package com.avago.core.ai

import android.content.Context
import com.avago.core.auth.SecureTokenStore
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.AiSkillResponse
import com.avago.core.network.model.ScoutEntityDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI extraction + Scout natural-language query facade.
 *
 * All calls go through the server-side AI proxy today (the server
 * in turn calls Gemini). An on-device Gemini Nano path would be wired
 * here once the AI Edge SDK ships stable and target devices carry Nano.
 *
 * Depends on [SecureTokenStore] instead of a separate IdentityManager
 * to stay consistent with the rest of the Android codebase.
 */
@Singleton
class AiExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceClient: AvagoServiceClient,
    private val tokenStore: SecureTokenStore,
) {

    private data class SkillsCache(val accountId: String, val skills: List<AiSkillResponse>, val fetchedAt: Long)
    @Volatile private var skillsCache: SkillsCache? = null

    /**
     * Extract structured JSON from raw OCR text.
     *
     * Routes to POST /accounts/:id/ai/extract.
     *
     * @param text    Raw OCR output from [DocScanPipeline] / AvagoTextRecognizer.
     * @param docType Hint for the model (e.g. "receipt", "warranty", "invoice").
     * @return        JSON string of extracted fields on success.
     */
    suspend fun extract(text: String, docType: String): Result<String> {
        val accountId = tokenStore.activeAccountId
            ?: return Result.failure(IllegalStateException("No active account"))
        return when (val r = serviceClient.extractDoc(accountId, text, docType)) {
            is NetworkResult.Success -> Result.success(r.data)
            is NetworkResult.Error -> {
                Timber.w("extractDoc HTTP ${r.code}: ${r.message}")
                Result.failure(Exception(r.message))
            }
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    /**
     * Natural-language Scout query.
     *
     * Routes to POST /ai/extract with the user's free-text query and a
     * snapshot of recent on-screen context.
     *
     * @param query   Free-text or transcribed voice input from the user.
     * @param ctx     Screen-context snapshot built by [ScoutContextHost].
     * @return        [ScoutResponse] with the matched skill, pre-filled
     *                form fields, and an optional action card.
     */
    suspend fun nlSearch(query: String, ctx: ScreenContext): Result<ScoutResponse> {
        val accountId = tokenStore.activeAccountId
            ?: return Result.failure(IllegalStateException("No active account"))

        // Map domain model → wire DTOs before handing off to the network layer.
        val entityDtos = ctx.recentEntities.map { e ->
            ScoutEntityDto(
                type = e.kind,
                id = e.id,
                display_name = e.label ?: "",
                metadata = emptyMap(),
            )
        }

        return when (
            val r = serviceClient.scoutQuery(
                accountId = accountId,
                query = query,
                recentEntities = entityDtos,
                currentScreen = ctx.screen,
            )
        ) {
            is NetworkResult.Success -> {
                val data = r.data
                // Flatten the payload JSON object into Map<String, String?>.
                // The model output is a flat (or near-flat) key/value object for
                // form-fill skills. Nested objects are serialized to JSON strings
                // so callers can inspect them without a separate parse step.
                val fields: Map<String, String?> = when (val p = data.payload) {
                    is JsonObject -> p.entries.associate { (k, v) ->
                        k to when {
                            v == JsonNull -> null
                            v is JsonPrimitive -> v.content
                            else -> v.toString()
                        }
                    }
                    else -> emptyMap()
                }
                // target_screen may live inside the payload (form-fill skills),
                // so check there after checking the action_card for action skills.
                val targetScreen = fields["target_screen"]
                Result.success(
                    ScoutResponse(
                        targetScreen = targetScreen,
                        skillName = data.skill_name,
                        fields = fields - "target_screen",
                        envelopeId = data.request_id,
                        actionCard = data.action_card?.let { ac ->
                            ActionCard(
                                title = ac.title,
                                summary = ac.summary,
                                skillName = ac.skill_name,
                                dangerous = ac.dangerous,
                                expiresAt = ac.expires_at,
                            )
                        },
                    )
                )
            }
            is NetworkResult.Error -> {
                Timber.w("scoutQuery HTTP ${r.code}: ${r.message}")
                Result.failure(Exception(r.message))
            }
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    /**
     * Fetch the list of available AI skills for the active account.
     *
     * Routes to GET /accounts/:id/ai/skills. Result is cached for 10 minutes
     * to avoid hammering the server on every screen that hosts a ScoutViewModel.
     */
    suspend fun getSkills(): Result<List<AiSkillResponse>> {
        val accountId = tokenStore.activeAccountId
            ?: return Result.failure(IllegalStateException("No active account"))
        val cached = skillsCache
        if (cached != null && cached.accountId == accountId &&
            System.currentTimeMillis() - cached.fetchedAt < 10 * 60 * 1000L) {
            return Result.success(cached.skills)
        }
        return when (val r = serviceClient.getAiSkills(accountId)) {
            is NetworkResult.Success -> {
                skillsCache = SkillsCache(accountId, r.data, System.currentTimeMillis())
                Result.success(r.data)
            }
            is NetworkResult.Error -> {
                Timber.w("getAiSkills HTTP ${r.code}: ${r.message}")
                Result.failure(Exception(r.message))
            }
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }
}
