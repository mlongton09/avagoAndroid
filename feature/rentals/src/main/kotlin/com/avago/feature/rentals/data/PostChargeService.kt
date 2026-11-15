package com.avago.feature.rentals.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.core.network.json.str
import com.avago.feature.rentals.domain.PostCharge
import com.avago.feature.rentals.domain.PostChargeList
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Online-only post-rental charges (tolls, tickets, citations) for one order. */
interface PostChargeService {
    suspend fun list(orderId: String): ApiResult<PostChargeList>

    /** Presign → PUT → finalize; returns the `file_upload_id` to use as `evidence_file_id`. */
    suspend fun uploadEvidence(orderId: String, filename: String, contentType: String, bytes: ByteArray): ApiResult<String>
    suspend fun create(orderId: String, body: JsonObject): ApiResult<PostCharge>
    suspend fun charge(orderId: String, id: String): ApiResult<PostCharge>
    suspend fun void(orderId: String, id: String, reason: String?): ApiResult<PostCharge>
}

@Singleton
class KtorPostChargeService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : PostChargeService {
    private inline fun <T> withAccount(block: (String) -> ApiResult<T>): ApiResult<T> {
        val a = identity.getActiveAccountId() ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        return block(a)
    }

    private fun base(a: String, orderId: String) = "/accounts/$a/rentals/orders/$orderId/post-charges"

    private fun row(o: JsonObject): PostCharge = PostCharge.from(o) ?: throw IllegalStateException("post-charge without id")

    override suspend fun list(orderId: String) = withAccount { a -> api.get(base(a, orderId)).map(PostChargeList::from) }

    override suspend fun uploadEvidence(orderId: String, filename: String, contentType: String, bytes: ByteArray): ApiResult<String> =
        withAccount { a ->
            val presign = api.post(
                "/accounts/$a/attachments/presigned-url",
                jsonBody("entity_id" to orderId, "entity_type" to "rental_order", "filename" to filename, "content_type" to contentType),
            )
            val p = when (presign) {
                is ApiResult.Err -> return@withAccount presign
                is ApiResult.Ok -> presign.value
            }
            val id = p.str("file_upload_id")
            val url = p.str("upload_url")
            if (id == null || url == null) return@withAccount ApiResult.Err(ApiError(-1, "parse_error", "presign response"))
            when (val put = api.upload(url, bytes, contentType)) {
                is ApiResult.Err -> return@withAccount put
                is ApiResult.Ok -> Unit
            }
            api.post("/accounts/$a/attachments/$id/finalize", jsonBody("file_size_bytes" to bytes.size.toLong())).map { id }
        }

    override suspend fun create(orderId: String, body: JsonObject) =
        withAccount { a -> api.post(base(a, orderId), body).map(::row) }

    override suspend fun charge(orderId: String, id: String) =
        withAccount { a -> api.post("${base(a, orderId)}/$id/charge").map(::row) }

    override suspend fun void(orderId: String, id: String, reason: String?) =
        withAccount { a -> api.post("${base(a, orderId)}/$id/void", jsonBody("reason" to reason?.trim()?.ifEmpty { null })).map(::row) }
}
