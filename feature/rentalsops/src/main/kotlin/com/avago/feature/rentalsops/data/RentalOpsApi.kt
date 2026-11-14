package com.avago.feature.rentalsops.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Outcome of a Rentals P2 call; never throws except for cancellation. */
sealed class ApiResult {
    data class Ok(val body: JsonObject) : ApiResult()

    /** Non-2xx with the server's stable `code` (e.g. `allocation_conflict`, `proof_required`). */
    data class Http(val status: Int, val code: String?, val message: String, val details: JsonObject? = null) : ApiResult()

    /** No response (offline, DNS, timeout). */
    data class Transport(val message: String) : ApiResult()
}

/**
 * Client for the rentals P2 endpoints under `/accounts/{a}/rentals`.
 *
 * Stage A/B endpoints (sync feeds, availability, order-line PATCH, statement, AR aging) are
 * implemented on the server. Stage C endpoints (dispatch tasks/my-tasks/actions, transfers,
 * assign-unit) follow the handlers in AvagoSvc `src/routes/rentals_api/{dispatch,transfers,
 * class_booking}.rs`; at the time of writing they are not yet mounted in `rentals_api/mod.rs`
 * (TODO stage C), so a server without them answers 404/405 and the outbox keeps events queued
 * (or uses the stage A fallbacks documented in feature/rentalsops/README.md).
 *
 * TODO(P1 reconcile): P1 adds core/network/rentals/RentalsApi with overlapping
 * syncFeed/availabilitySearch/patchLine — switch to it after P1 merges.
 */
@Singleton
class RentalOpsApi @Inject constructor(
    private val client: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun r(accountId: String, path: String) = "$baseUrl/accounts/$accountId/rentals$path"

    // ---- Stage A/B -------------------------------------------------------------------

    suspend fun syncFeed(accountId: String, entity: String, cursor: String?, limit: Int = 500): ApiResult = call {
        client.get(r(accountId, "/sync/$entity")) {
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }
    }

    suspend fun availability(accountId: String, from: String, to: String, classId: String, locationId: String?): ApiResult = call {
        client.get(r(accountId, "/availability/search")) {
            parameter("from", from)
            parameter("to", to)
            parameter("class_id", classId)
            locationId?.let { parameter("location_id", it) }
            parameter("qty", 1)
        }
    }

    /** Class → unit assignment: PATCH the order line with `asset_id` (409 `allocation_conflict`). */
    suspend fun assignUnit(accountId: String, orderId: String, lineId: String, assetId: String, idempotencyKey: String): ApiResult = call {
        client.patch(r(accountId, "/orders/$orderId/lines/$lineId")) {
            idem(idempotencyKey)
            body(JsonObject(mapOf("asset_id" to JsonPrimitive(assetId))))
        }
    }

    suspend fun statement(accountId: String, customerId: String, from: String?, to: String?): ApiResult = call {
        client.get(r(accountId, "/customers/$customerId/statement")) {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }
    }

    suspend fun arAging(accountId: String, asOf: String? = null): ApiResult = call {
        client.get(r(accountId, "/reports/ar-aging")) { asOf?.let { parameter("as_of", it) } }
    }

    /** Existing asset move endpoint (works today); used when stage C transfers are not routed. */
    suspend fun moveAsset(accountId: String, assetId: String, toLocationId: String, reason: String?, idempotencyKey: String): ApiResult = call {
        client.post("$baseUrl/accounts/$accountId/assets/$assetId/transfer") {
            idem(idempotencyKey)
            body(
                JsonObject(
                    buildMap {
                        put("to_location_id", JsonPrimitive(toLocationId))
                        reason?.let { put("move_reason", JsonPrimitive(it)) }
                    },
                ),
            )
        }
    }

    // ---- Stage C (handlers in AvagoSvc-stagec routes/rentals_api/{dispatch,transfers,class_booking}.rs;
    //      TODO(stage C): router mounting + docs/rentals-api-stage-c.md still pending) --------

    /**
     * Driver feed: `GET /rentals/dispatch/my-tasks?cursor` — same envelope as the sync feeds;
     * tasks taken away from the caller come back as `{id, _deleted: true}`.
     */
    suspend fun myTasksFeed(accountId: String, cursor: String?, limit: Int = 500): ApiResult = call {
        client.get(r(accountId, "/dispatch/my-tasks")) {
            parameter("cursor", cursor ?: "0")
            parameter("limit", limit)
        }
    }

    /**
     * `POST /rentals/dispatch/tasks/{id}/{start|arrive|complete|fail}` with
     * `{at, lat, lng, reason, photo_file_ids, signature_file_id, signed_name, notes}`.
     * Replaying an action that already happened returns the task unchanged.
     */
    suspend fun taskAction(accountId: String, taskId: String, action: String, body: JsonObject, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/dispatch/tasks/$taskId/$action")) {
            idem(idempotencyKey)
            body(body)
        }
    }

    /** `POST /rentals/transfers` `{asset_id, to_location_id, ship_at, arrive_by, reason, client_uuid}` (201; server id). */
    suspend fun createTransfer(accountId: String, body: JsonObject, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/transfers")) {
            idem(idempotencyKey)
            body(body)
        }
    }

    /** `POST /rentals/transfers/{id}/{ship|receive|cancel}` `{at, reason}`. */
    suspend fun transferAction(accountId: String, transferId: String, action: String, body: JsonObject, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/transfers/$transferId/$action")) {
            idem(idempotencyKey)
            body(body)
        }
    }

    /**
     * `POST /rentals/orders/{o}/lines/{l}/assign-unit` `{asset_id}` — stage C class booking
     * (409 `allocation_conflict`). Older servers: fall back to [assignUnit] (line PATCH).
     */
    suspend fun assignUnitC(accountId: String, orderId: String, lineId: String, assetId: String, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/orders/$orderId/lines/$lineId/assign-unit")) {
            idem(idempotencyKey)
            body(JsonObject(mapOf("asset_id" to JsonPrimitive(assetId))))
        }
    }

    // ---- plumbing ---------------------------------------------------------------------

    private fun HttpRequestBuilder.idem(key: String) = header("Idempotency-Key", key)

    private fun HttpRequestBuilder.body(o: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(o)
    }

    private suspend fun call(block: suspend () -> HttpResponse): ApiResult = try {
        val resp = block()
        val text = resp.bodyAsText()
        ApiResult.Ok(if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        val text = runCatching { e.response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val code = (parsed?.get("code") as? JsonPrimitive)?.content
            ?: ((parsed?.get("error") as? JsonObject)?.get("code") as? JsonPrimitive)?.content
        val msg = (parsed?.get("message") as? JsonPrimitive)?.content
            ?: (parsed?.get("error") as? JsonPrimitive)?.content
            ?: text.take(300)
        ApiResult.Http(e.response.status.value, code, msg, parsed?.get("details") as? JsonObject)
    } catch (e: Exception) {
        ApiResult.Transport(e.message ?: e.javaClass.simpleName)
    }
}
