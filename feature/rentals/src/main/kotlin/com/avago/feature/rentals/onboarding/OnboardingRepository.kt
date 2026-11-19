package com.avago.feature.rentals.onboarding

import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import kotlinx.coroutines.flow.first

/** No asset/class/rate create chains and deliberately no shop-import commit method. */
interface OnboardingService {
    suspend fun get(account: String): ApiResult<JsonObject>
    suspend fun save(account: String, revision: Long, draft: OnboardingDraft): ApiResult<JsonObject>
    suspend fun publish(account: String, revision: Long): ApiResult<JsonObject>
}

class OnboardingRepository @Inject constructor(
    private val api: JsonApi,
    private val locationRepository: LocationRepository,
) : OnboardingService {
    private fun path(account: String) = "/accounts/$account/rentals/onboarding"
    override suspend fun get(account: String) = api.get(path(account))
    /** Active locations of the signed-in account (from the v2 store). */
    suspend fun locations(): List<Location> = locationRepository.observeLocations().first()
    override suspend fun save(account: String, revision: Long, draft: OnboardingDraft) =
        api.put(path(account), saveBody(revision, draft))
    override suspend fun publish(account: String, revision: Long) =
        api.post("${path(account)}/publish", publishBody(revision), timeoutMs = 110_000)
    suspend fun parse(account: String, file: String, base64: String, sheet: String? = null) =
        api.post("/accounts/$account/shop-import/parse", buildJsonObject {
            put("entity", "assets"); put("file_name", file); put("content_base64", base64)
            sheet?.let { put("sheet", it) }
        }, timeoutMs = 110_000)
    companion object {
        fun saveBody(revision: Long, draft: OnboardingDraft) = buildJsonObject {
            put("revision", revision); put("draft", draft.body())
        }
        fun publishBody(revision: Long) = buildJsonObject { put("revision", revision) }
    }
}

/** A timed-out publish retries the exact saved revision, not another save/create cycle. */
suspend fun persistOnboarding(service: OnboardingService, account: String, local: DraftSession,
    draft: OnboardingDraft, publish: Boolean): DraftSession {
    var state = local
    if (!local.publishedSuccessfully && local.savedDraft != draft.body()) {
        val saved = service.save(account, local.revision, draft)
        state = state.receive(saved, replaceEditor = false)
        if (saved is ApiResult.Err) return state
    }
    if (publish) state = when (val receipt = service.publish(account, state.revision)) {
        is ApiResult.Ok -> state.published(receipt.value)
        is ApiResult.Err -> state.receive(receipt, replaceEditor = false)
    }
    return state
}

/** Used by the screen controller; error/conflict responses never replace the local editor. */
data class DraftSession(
    val editor: OnboardingEditor = OnboardingEditor(),
    val revision: Long = 0,
    val receipt: JsonObject? = null,
    val error: String? = null,
    val savedDraft: JsonObject? = null,
) {
    fun receive(
        result: ApiResult<JsonObject>,
        replaceEditor: Boolean,
        rebase: Boolean = true,
    ): DraftSession = when (result) {
        is ApiResult.Err -> copy(error = when (result.error.status) {
            404, 405, 501 -> "unsupported_service: ${result.error.status} ${result.error.message}"
            403 -> "permission_or_feature: ${result.error.message}"
            409 -> "revision_conflict: ${result.error.code.orEmpty()} ${result.error.message}"
            else -> "${result.error.status}: ${result.error.message}"
        })
        is ApiResult.Ok -> {
            val response = result.value
            val draft = response["draft"] as? JsonObject
            val remotePublished = response["published"] as? JsonObject
            val conflict = !rebase && !replaceEditor &&
                response["revision"]?.jsonPrimitive?.longOrNull != revision
            if (conflict && remotePublished == null) {
                copy(error = "revision_conflict: local draft retained; explicitly refresh revision before saving")
            } else copy(
                revision = response.getValue("revision").jsonPrimitive.long,
                editor = if (replaceEditor && draft != null) OnboardingEditor.from(OnboardingDraft.parse(draft))
                    else editor,
                receipt = remotePublished, savedDraft = draft, error = null,
            )
        }
    }
    fun published(response: JsonObject) = copy(
        receipt = response,
        error = if (response["storefront_published"]?.jsonPrimitive?.booleanOrNull == true) null
            else "publication_receipt_missing",
    )
    val publishedSuccessfully get() = receipt?.get("storefront_published")?.jsonPrimitive?.booleanOrNull == true
}
