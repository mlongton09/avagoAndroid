package com.avago.core.ai

import android.content.Context
import com.avago.core.auth.SecureTokenStore
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ScoutEntityDto
import dagger.hilt.android.qualifiers.ApplicationContext
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
     * Routes to POST /accounts/:id/ai/scout with the user's free-text
     * query and a snapshot of recent on-screen context.
     *
     * @param query   Free-text or transcribed voice input from the user.
     * @param ctx     Screen-context snapshot built by [ScoutContextHost].
     * @return        [ScoutResponse] directing the app to a target screen
     *                and pre-populated form fields.
     */
    suspend fun nlSearch(query: String, ctx: ScoutContext): Result<ScoutResponse> {
        val accountId = tokenStore.activeAccountId
            ?: return Result.failure(IllegalStateException("No active account"))

        // Map domain model → wire DTOs before handing off to the network layer.
        val entityDtos = ctx.recentEntities.map { e ->
            ScoutEntityDto(
                type = e.type,
                id = e.id,
                display_name = e.displayName,
                metadata = e.metadata,
            )
        }

        return when (
            val r = serviceClient.scoutQuery(
                accountId = accountId,
                query = query,
                recentEntities = entityDtos,
                currentScreen = ctx.currentScreen,
            )
        ) {
            is NetworkResult.Success -> Result.success(
                ScoutResponse(
                    targetScreen = r.data.target_screen,
                    fields = r.data.fields,
                    envelopeId = r.data.envelope_id,
                    message = r.data.message,
                )
            )
            is NetworkResult.Error -> {
                Timber.w("scoutQuery HTTP ${r.code}: ${r.message}")
                Result.failure(Exception(r.message))
            }
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }
}
