package com.avago.core.ai

import android.content.Context
import com.avago.core.auth.SecureTokenStore
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AsyncPipelineStep] that converts raw OCR text into structured Markdown
 * by routing through the server-side AI extraction endpoint.
 *
 * Mirrors iOS TextFormattingStep.swift. iOS uses on-device FoundationModels
 * (Apple Intelligence, iOS 26+). On Android the same server-side proxy (Gemini)
 * handles formatting until on-device Gemini Nano ships stable.
 *
 * Pipeline position:
 * ```
 * TextExtractionStep → String → TextFormattingStep → String (Markdown)
 * ```
 */
@Singleton
class TextFormattingStep @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceClient: AvagoServiceClient,
    private val tokenStore: SecureTokenStore,
) : AsyncPipelineStep<TextFormattingStep.Input, String> {

    data class Input(
        val rawText: String,
        val categories: List<String> = emptyList(),
        val docType: String = "receipt",
        val locale: String = "en-us",
    )

    override suspend fun process(input: Input): Result<String> {
        if (input.rawText.isBlank()) return Result.failure(IllegalArgumentException("Empty input text"))

        val accountId = tokenStore.activeAccountId
            ?: return Result.failure(IllegalStateException("No active account"))

        val augmented = buildAugmentedPrompt(input)

        return when (val r = serviceClient.extractDoc(accountId, augmented, input.docType)) {
            is NetworkResult.Success -> Result.success(r.data)
            is NetworkResult.Error -> {
                Timber.w("[TextFormattingStep] extract failed (HTTP ${r.code}): ${r.message}")
                Result.failure(Exception(r.message))
            }
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    private fun buildAugmentedPrompt(input: Input): String = buildString {
        val prompt = loadPrompt(input.locale, input.docType)
            ?: loadPrompt("en-us", input.docType)
        if (prompt != null) {
            append(prompt)
            append("\n\n---\n\n")
        }
        if (input.categories.isNotEmpty()) {
            append("Categories: ${input.categories.joinToString(", ")}\n\n")
        }
        append(input.rawText)
    }

    private fun loadPrompt(locale: String, docType: String): String? {
        val typeCap = docType.replaceFirstChar { it.uppercaseChar() }
        val fileName = "Prompt${typeCap}Scan.$locale.md"
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (_: Exception) { null }
    }
}
