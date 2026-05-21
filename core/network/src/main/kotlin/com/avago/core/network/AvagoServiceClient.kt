package com.avago.core.network

import com.avago.core.network.model.AccountResponse
import com.avago.core.network.model.AuthResponse
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatMessagesResponse
import com.avago.core.network.model.ChatThreadResponse
import com.avago.core.network.model.CreateThreadRequest
import com.avago.core.network.model.CreateCycleCountRequest
import com.avago.core.network.model.CreateGrnRequest
import com.avago.core.network.model.CreatePartIssueRequest
import com.avago.core.network.model.CreatePurchaseOrderRequest
import com.avago.core.network.model.CycleCountResponse
import com.avago.core.network.model.DeviceUpdateRequest
import com.avago.core.network.model.EditMessageRequest
import com.avago.core.network.model.GrnResponse
import com.avago.core.network.model.InventoryReceiveRequest
import com.avago.core.network.model.InventoryReceiveResponse
import com.avago.core.network.model.InventoryUseRequest
import com.avago.core.network.model.PartIssueResponse
import com.avago.core.network.model.ProvisionRequest
import com.avago.core.network.model.PurchaseOrderResponse
import com.avago.core.network.model.ReactMessageRequest
import com.avago.core.network.model.RefreshRequest
import com.avago.core.network.model.ScoutEntityDto
import com.avago.core.network.model.ScoutQueryRequest
import com.avago.core.network.model.ScoutQueryResponse
import com.avago.core.network.model.SendMessageRequest
import com.avago.core.network.model.SignInRequest
import com.avago.core.network.model.SyncPullResponse
import com.avago.core.network.model.SyncPushRequest
import com.avago.core.network.model.SyncPushResponse
import com.avago.core.network.model.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AvagoServiceClient @Inject constructor(
    private val client: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
) {

    suspend fun provision(deviceId: String): AuthResponse =
        safeCall { client.post("$baseUrl/auth/provision") {
            setBody(ProvisionRequest(device_id = deviceId))
        }.body() }

    suspend fun signIn(firebaseToken: String, deviceId: String): AuthResponse =
        safeCall { client.post("$baseUrl/auth/signin") {
            setBody(SignInRequest(firebase_token = firebaseToken, device_id = deviceId))
        }.body() }

    suspend fun refreshTokens(refreshToken: String, deviceId: String): AuthResponse =
        safeCall { client.post("$baseUrl/auth/refresh") {
            setBody(RefreshRequest(refresh_token = refreshToken, device_id = deviceId))
        }.body() }

    suspend fun getMe(): UserResponse =
        safeCall { client.get("$baseUrl/users/me").body() }

    suspend fun getAccount(): AccountResponse =
        safeCall { client.get("$baseUrl/accounts/me").body() }

    suspend fun updateDevice(deviceId: String, request: DeviceUpdateRequest) {
        safeCall<Unit> {
            val response: HttpResponse = client.put("$baseUrl/devices/$deviceId") {
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun syncPush(accountId: String, request: SyncPushRequest): SyncPushResponse =
        safeCall { client.post("$baseUrl/accounts/$accountId/sync/push") {
            setBody(request)
        }.body() }

    suspend fun syncPull(
        accountId: String,
        entityType: String,
        lastSeq: Long,
        limit: Int = 200,
    ): SyncPullResponse =
        safeCall { client.get("$baseUrl/accounts/$accountId/sync/pull") {
            parameter("entity_type", entityType)
            parameter("last_seq", lastSeq)
            parameter("limit", limit)
        }.body() }

    suspend fun getMembers(accountId: String): List<UserResponse> =
        safeCall { client.get("$baseUrl/accounts/$accountId/members").body() }

    suspend fun inviteUser(accountId: String, email: String, role: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.post("$baseUrl/accounts/$accountId/invitations") {
                setBody(mapOf("email" to email, "role" to role))
            }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Inventory — direct REST (not sync-queue)
    // ---------------------------------------------------------------------------

    suspend fun receiveInventory(
        inventoryId: String,
        request: InventoryReceiveRequest,
    ): InventoryReceiveResponse =
        safeCall {
            client.post("$baseUrl/inventory/$inventoryId/receive") {
                setBody(request)
            }.body()
        }

    suspend fun useInventory(
        inventoryId: String,
        request: InventoryUseRequest,
    ): InventoryReceiveResponse =
        safeCall {
            client.post("$baseUrl/inventory/$inventoryId/use") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Purchase Orders
    // ---------------------------------------------------------------------------

    suspend fun createPurchaseOrder(
        accountId: String,
        request: CreatePurchaseOrderRequest,
    ): PurchaseOrderResponse =
        safeCall {
            client.post("$baseUrl/accounts/$accountId/purchase-orders") {
                setBody(request)
            }.body()
        }

    suspend fun updatePurchaseOrder(
        accountId: String,
        poId: String,
        request: CreatePurchaseOrderRequest,
    ): PurchaseOrderResponse =
        safeCall {
            client.put("$baseUrl/accounts/$accountId/purchase-orders/$poId") {
                setBody(request)
            }.body()
        }

    suspend fun approvePurchaseOrder(accountId: String, poId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/approve")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun markPurchaseOrderOrdered(accountId: String, poId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/mark-ordered")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // GRN
    // ---------------------------------------------------------------------------

    suspend fun createGrn(
        accountId: String,
        poId: String,
        request: CreateGrnRequest,
    ): GrnResponse =
        safeCall {
            client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/grns") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Part Issues (warehouse issue / move)
    // ---------------------------------------------------------------------------

    suspend fun createPartIssue(
        accountId: String,
        request: CreatePartIssueRequest,
    ): PartIssueResponse =
        safeCall {
            client.post("$baseUrl/accounts/$accountId/part-issues") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Cycle Counts
    // ---------------------------------------------------------------------------

    suspend fun createCycleCount(
        accountId: String,
        request: CreateCycleCountRequest,
    ): CycleCountResponse =
        safeCall {
            client.post("$baseUrl/accounts/$accountId/cycle-counts") {
                setBody(request)
            }.body()
        }

    suspend fun lockCycleCount(accountId: String, countId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.put("$baseUrl/accounts/$accountId/cycle-counts/$countId/lock")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun reconcileCycleCount(accountId: String, countId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/cycle-counts/$countId/reconcile")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Work Orders — direct REST calls
    // ---------------------------------------------------------------------------

    /**
     * POST /accounts/:accountId/work-orders/:woId/recurrence
     * Sets or updates the RFC 5545 RRULE on a work order.
     */
    suspend fun updateWorkOrderRecurrence(
        accountId: String,
        woId: String,
        rrule: String,
    ) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/recurrence") {
                    setBody(mapOf("rrule" to rrule))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    /**
     * PUT /accounts/:accountId/work-orders/:woId/assignments
     * Self-assign the calling user (by userId) to the work order.
     */
    suspend fun selfAssignWorkOrder(
        accountId: String,
        woId: String,
        userId: String,
    ) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.put("$baseUrl/accounts/$accountId/work-orders/$woId/assignments") {
                    setBody(mapOf("tech_id" to userId))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AI / Extraction
    // ---------------------------------------------------------------------------

    /**
     * POST /accounts/:accountId/ai/extract
     *
     * Sends raw OCR text and a document-type hint to the server-side AI extraction
     * pipeline.  Returns a JSON string of structured fields on success.
     *
     * @param accountId The active account.
     * @param text      Raw OCR text obtained from the document pages.
     * @param docType   Hint for the extraction model (e.g. "receipt", "warranty").
     */
    suspend fun extractDoc(
        accountId: String,
        text: String,
        docType: String,
    ): NetworkResult<String> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/ai/extract") {
                setBody(mapOf("text" to text, "doc_type" to docType))
            }.body()
        }

    /**
     * POST /accounts/:accountId/ai/scout
     *
     * Sends a natural-language query together with a screen-context
     * snapshot to the server-side Scout AI pipeline. Returns a
     * [ScoutQueryResponse] that tells the client which screen to open
     * and which form fields to pre-fill.
     *
     * @param accountId    The active account.
     * @param query        Free-text or transcribed voice input.
     * @param recentEntities MRU list of entities the user recently viewed.
     * @param currentScreen  Nav route of the currently visible screen.
     */
    suspend fun scoutQuery(
        accountId: String,
        query: String,
        recentEntities: List<ScoutEntityDto> = emptyList(),
        currentScreen: String? = null,
    ): NetworkResult<ScoutQueryResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/ai/scout") {
                setBody(
                    ScoutQueryRequest(
                        query = query,
                        recent_entities = recentEntities,
                        current_screen = currentScreen,
                    )
                )
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------------------

    suspend fun getThreads(accountId: String): NetworkResult<List<ChatThreadResponse>> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/chat/threads").body() }

    suspend fun createThread(
        accountId: String,
        request: CreateThreadRequest,
    ): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/chat/threads") {
                setBody(request)
            }.body()
        }

    suspend fun getMessages(
        threadId: String,
        before: String? = null,
        limit: Int = 50,
    ): NetworkResult<ChatMessagesResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/$threadId/messages") {
                before?.let { parameter("before", it) }
                parameter("limit", limit)
            }.body()
        }

    suspend fun sendMessage(
        threadId: String,
        body: String,
        photoUrl: String? = null,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/threads/$threadId/messages") {
                setBody(SendMessageRequest(body = body, photo_url = photoUrl))
            }.body()
        }

    suspend fun editMessage(
        threadId: String,
        messageId: String,
        body: String,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.put("$baseUrl/chat/threads/$threadId/messages/$messageId") {
                setBody(EditMessageRequest(body = body))
            }.body()
        }

    suspend fun deleteMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/threads/$threadId/messages/$messageId")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    suspend fun reactToMessage(
        threadId: String,
        messageId: String,
        emoji: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/threads/$threadId/messages/$messageId/reactions") {
                    setBody(ReactMessageRequest(emoji = emoji))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Wraps a network call and returns a [NetworkResult] instead of throwing.
     * Use for operations where the caller needs to distinguish success vs. failure
     * (e.g. chat send, where we need to mark an optimistic message as failed).
     */
    private suspend inline fun <reified T> safeNetworkCall(crossinline block: suspend () -> T): NetworkResult<T> =
        try {
            NetworkResult.Success(block())
        } catch (e: UnauthorizedException) {
            NetworkResult.Unauthorized
        } catch (e: io.ktor.client.plugins.ResponseException) {
            val code = e.response.status.value
            if (code == HttpStatusCode.Unauthorized.value) {
                NetworkResult.Unauthorized
            } else {
                Timber.e(e, "HTTP $code from service")
                NetworkResult.Error(code, e.response.status.description)
            }
        } catch (e: Exception) {
            Timber.e(e, "Network call failed")
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        }

    // ---------------------------------------------------------------------------
    // Internal helpers (throwing variants used by non-chat callers)
    // ---------------------------------------------------------------------------

    private suspend inline fun <reified T> safeCall(block: () -> T): T {
        return try {
            block()
        } catch (e: NetworkException) {
            throw e
        } catch (e: io.ktor.client.plugins.ResponseException) {
            val code = e.response.status.value
            if (code == HttpStatusCode.Unauthorized.value) {
                throw UnauthorizedException()
            }
            Timber.e(e, "HTTP $code from service")
            throw NetworkException(code, e.response.status.description)
        } catch (e: Exception) {
            Timber.e(e, "Network call failed")
            throw NetworkException(-1, e.message ?: "Unknown error")
        }
    }
}

class NetworkException(val code: Int, override val message: String) : Exception(message)
class UnauthorizedException : Exception("Unauthorized")
