package com.avago.core.network

import com.avago.core.network.model.AccountResponse
import com.avago.core.network.model.AiSkillResponse
import com.avago.core.network.model.AuditEventResponse
import com.avago.core.network.model.AuthResponse
import com.avago.core.network.model.BudgetPillResponse
import com.avago.core.network.model.BulkInvitation
import com.avago.core.network.model.CreateRentalRequest
import com.avago.core.network.model.DispatchConfigResponse
import com.avago.core.network.model.DocOcrResponse
import com.avago.core.network.model.EffortStatsResponse
import com.avago.core.network.model.GeocodeRequest
import com.avago.core.network.model.GeocodeResponse
import com.avago.core.network.model.PhotoResponse
import com.avago.core.network.model.PhotoUploadUrlResponse
import com.avago.core.network.model.RentalResponse
import com.avago.core.network.model.RolePermissionResponse
import com.avago.core.network.model.UpdatePreferencesRequest
import com.avago.core.network.model.UserPreferencesResponse
import com.avago.core.network.model.VinDecodeResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.avago.core.network.model.ChatMediaPresignResponse
import com.avago.core.network.model.ChatMemberResponse
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatMessagesResponse
import com.avago.core.network.model.ChatPageResponse
import com.avago.core.network.model.ChatPrefsRequest
import com.avago.core.network.model.ChatPrefsResponse
import com.avago.core.network.model.ChatRosterEntry
import com.avago.core.network.model.ChatSyncResponse
import com.avago.core.network.model.ChatThreadResponse
import com.avago.core.network.model.CreateThreadRequest
import com.avago.core.network.model.LinkPreviewResponse
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
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AvagoServiceClient @Inject constructor(
    private val client: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
) {

    private val _permissionsStaleEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val permissionsStaleEvents: SharedFlow<String> = _permissionsStaleEvents.asSharedFlow()

    fun notifyPermissionsStale(accountId: String) {
        _permissionsStaleEvents.tryEmit(accountId)
    }

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

    suspend fun switchAccount(targetAccountId: String): NetworkResult<AuthResponse> =
        safeNetworkCall {
            client.post("$baseUrl/auth/switch-account") {
                setBody(mapOf("account_id" to targetAccountId))
            }.body()
        }

    suspend fun getMe(): UserResponse =
        safeCall { client.get("$baseUrl/users/me").body() }

    suspend fun getAccount(): AccountResponse =
        safeCall { client.get("$baseUrl/accounts/me").body() }

    suspend fun getMyAccounts(): NetworkResult<List<AccountResponse>> =
        safeNetworkCall { client.get("$baseUrl/users/me/accounts").body() }

    suspend fun getAccountMembers(accountId: String): NetworkResult<List<UserResponse>> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/members").body() }

    // ---------------------------------------------------------------------------
    // Account Management
    // ---------------------------------------------------------------------------

    /** PUT /users/me — update the current user's display name. */
    suspend fun updateMe(displayName: String): NetworkResult<UserResponse> =
        safeNetworkCall {
            client.put("$baseUrl/users/me") {
                setBody(mapOf("display_name" to displayName))
            }.body()
        }

    /** POST /accounts — create a new account. */
    suspend fun createAccount(accountName: String? = null): NetworkResult<AccountResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts") {
                setBody(mapOf("name" to accountName))
            }.body()
        }

    /** DELETE /accounts/{accountId} — soft-delete an account. */
    suspend fun deleteAccount(accountId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.delete("$baseUrl/accounts/$accountId")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** DELETE /accounts/{accountId}/customer-content — wipe all customer data for an account. */
    suspend fun deleteCustomerContent(accountId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/accounts/$accountId/customer-content")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /accounts/{accountId} — rename an account. */
    suspend fun updateAccountName(accountId: String, name: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.put("$baseUrl/accounts/$accountId") {
                setBody(mapOf("name" to name))
            }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /accounts/{accountId}/load-sample-data — seed demo data into the account. */
    suspend fun loadSampleData(accountId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/load-sample-data")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /accounts/{accountId}/invitations/batch — bulk-invite users by email. */
    suspend fun bulkInviteUsers(
        accountId: String,
        invitations: List<BulkInvitation>,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/invitations/batch") {
                    setBody(mapOf("invitations" to invitations))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Permissions & Dispatch Config
    // ---------------------------------------------------------------------------

    /** GET /accounts/{accountId}/admin/role-permissions — fetch the role permission matrix. */
    suspend fun getRolePermissions(accountId: String): NetworkResult<List<RolePermissionResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/admin/role-permissions").body()
        }

    /** GET /accounts/{accountId}/dispatch-config — fetch dispatch board configuration. */
    suspend fun getDispatchConfig(accountId: String): NetworkResult<DispatchConfigResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/dispatch-config").body()
        }

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

    /** POST /accounts/{accountId}/work-orders/{woId}/status — transition a WO status. */
    suspend fun transitionWorkOrderStatus(
        accountId: String,
        woId: String,
        status: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/status") {
                    setBody(mapOf("status" to status))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PATCH /accounts/{accountId}/work-orders/{woId} — partial update of a work order. */
    suspend fun patchWorkOrder(
        accountId: String,
        woId: String,
        fields: JsonObject,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.patch("$baseUrl/accounts/$accountId/work-orders/$woId") {
                    setBody(fields)
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /accounts/{accountId}/work-orders/{woId}/assignments/claim — self-claim a WO. */
    suspend fun claimWorkOrder(accountId: String, woId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/assignments/claim")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /accounts/{accountId}/work-orders/{woId}/reschedule — reschedule a WO's due date. */
    suspend fun rescheduleWorkOrder(
        accountId: String,
        woId: String,
        to: String,
        scope: String? = null,
        reason: String? = null,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/reschedule") {
                    setBody(mapOf("to" to to, "scope" to scope, "reason" to reason))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /accounts/{accountId}/work-orders/{woId}/assignments/{assignmentId}/accept */
    suspend fun acceptAssignment(
        accountId: String,
        woId: String,
        assignmentId: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/accounts/$accountId/work-orders/$woId/assignments/$assignmentId/accept")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /accounts/{accountId}/work-orders/{woId}/assignments/{assignmentId}/decline */
    suspend fun declineAssignment(
        accountId: String,
        woId: String,
        assignmentId: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/accounts/$accountId/work-orders/$woId/assignments/$assignmentId/decline")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** GET /accounts/{accountId}/work-orders/effort-stats — effort percentile stats for a category. */
    suspend fun getEffortStats(
        accountId: String,
        category: String,
        assetType: String? = null,
    ): NetworkResult<EffortStatsResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/effort-stats") {
                parameter("category", category)
                assetType?.let { parameter("asset_type", it) }
            }.body()
        }

    /** GET /accounts/{accountId}/work-orders/{woId}/audit — fetch audit trail for a WO. */
    suspend fun getWorkOrderAudit(
        accountId: String,
        woId: String,
    ): NetworkResult<List<AuditEventResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/audit").body()
        }

    /** GET /accounts/{accountId}/work-orders/{woId}/budget-pill — fetch budget summary for a WO. */
    suspend fun getWorkOrderBudgetPill(
        accountId: String,
        woId: String,
    ): NetworkResult<BudgetPillResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/budget-pill").body()
        }

    // ---------------------------------------------------------------------------
    // VIN Decode & Geocode
    // ---------------------------------------------------------------------------

    /** POST /accounts/{accountId}/vin/decode — decode a VIN number to vehicle attributes. */
    suspend fun decodeVin(accountId: String, vin: String): NetworkResult<VinDecodeResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/vin/decode") {
                setBody(mapOf("vin" to vin))
            }.body()
        }

    /** POST /accounts/{accountId}/geocode — geocode a postal address to lat/lon. */
    suspend fun geocodeAddress(
        accountId: String,
        request: GeocodeRequest,
    ): NetworkResult<GeocodeResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/geocode") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // User Preferences
    // ---------------------------------------------------------------------------

    /** GET /accounts/{accountId}/preferences/me — fetch the current user's preferences. */
    suspend fun getMyPreferences(accountId: String): NetworkResult<UserPreferencesResponse> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/preferences/me").body() }

    /** PUT /accounts/{accountId}/preferences/me — update the current user's preferences. */
    suspend fun updateMyPreferences(
        accountId: String,
        request: UpdatePreferencesRequest,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/accounts/$accountId/preferences/me") {
                    setBody(request)
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    @Deprecated(
        message = "Path was incorrect — use getMyPreferences instead.",
        replaceWith = ReplaceWith("getMyPreferences(accountId)"),
    )
    suspend fun getUserPreferences(accountId: String): NetworkResult<UserPreferencesResponse> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/preferences").body() }

    // ---------------------------------------------------------------------------
    // Tech Location
    // ---------------------------------------------------------------------------

    suspend fun updateTechLocation(
        accountId: String,
        userId: String,
        lat: Double,
        lon: Double,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/users/$userId/location") {
                    setBody(mapOf("lat" to lat, "lon" to lon))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // AI / Scout
    // ---------------------------------------------------------------------------

    /**
     * POST /ai/warmup — pre-warm the AI inference pipeline.
     *
     * Call on app launch (after sign-in) to reduce latency on the first Scout query.
     */
    suspend fun aiWarmup(): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.post("$baseUrl/ai/warmup")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /**
     * GET /accounts/{accountId}/ai/skills — list the Scout skills available for an account.
     */
    suspend fun getAiSkills(accountId: String): NetworkResult<List<AiSkillResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/ai/skills").body()
        }

    /**
     * POST /ai/extract — Scout AI form-fill from voice/text input.
     *
     * Sends a transcript and screen context to the AI pipeline; returns structured
     * entity field values to pre-populate a form.
     *
     * @param transcript      Free-text or transcribed voice input.
     * @param screenContext   Serialized snapshot of the currently visible screen/form.
     * @param skillHint       Optional skill ID to bias extraction toward a specific skill.
     * @param threadId        Optional chat thread ID for conversational context.
     * @param idempotencyKey  Optional client-generated key for deduplication.
     */
    suspend fun aiExtract(
        transcript: String,
        screenContext: String,
        skillHint: String? = null,
        threadId: String? = null,
        idempotencyKey: String? = null,
    ): NetworkResult<String> =
        safeNetworkCall {
            client.post("$baseUrl/ai/extract") {
                setBody(
                    buildMap<String, String?> {
                        put("transcript", transcript)
                        put("screen_context", screenContext)
                        skillHint?.let { put("skill_hint", it) }
                        threadId?.let { put("thread_id", it) }
                        idempotencyKey?.let { put("idempotency_key", it) }
                    }.filterValues { it != null }
                )
            }.body()
        }

    /**
     * POST /accounts/:accountId/ai/extract-doc — extract structured JSON from OCR text.
     *
     * @param accountId Active account.
     * @param text      Raw OCR output.
     * @param docType   Hint for the model (e.g. "receipt", "invoice", "warranty").
     * @return          JSON string of extracted fields on success.
     */
    suspend fun extractDoc(
        accountId: String,
        text: String,
        docType: String,
    ): NetworkResult<String> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/ai/extract-doc") {
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
     * @param accountId      The active account.
     * @param query          Free-text or transcribed voice input.
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
    // Doc OCR
    // ---------------------------------------------------------------------------

    /**
     * POST /accounts/{accountId}/docs/ocr-extract — extract structured fields from OCR text.
     *
     * Distinct from the Scout AI `/ai/extract` endpoint. This pipeline is optimised
     * for document parsing (receipts, warranties, invoices) and returns typed fields
     * such as vendor, total, and line items.
     *
     * @param accountId    The active account.
     * @param ocrRawText   Raw OCR text extracted from the scanned document pages.
     * @param documentType Document category hint (e.g. "receipt", "warranty", "invoice").
     * @param assetId      Optional asset to associate the extracted document with.
     * @param locale       BCP-47 locale of the source document (default "en").
     */
    suspend fun extractDocOcr(
        accountId: String,
        ocrRawText: String,
        documentType: String,
        assetId: String? = null,
        locale: String = "en",
    ): NetworkResult<DocOcrResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/docs/ocr-extract") {
                setBody(
                    buildMap<String, String?> {
                        put("ocr_raw_text", ocrRawText)
                        put("document_type", documentType)
                        assetId?.let { put("asset_id", it) }
                        put("locale", locale)
                    }.filterValues { it != null }
                )
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Client Metrics
    // ---------------------------------------------------------------------------

    /**
     * POST /metrics/client — flush client-side telemetry counters to the server.
     *
     * @param metrics A [JsonObject] whose keys are metric names and whose values
     *                are Long counts (serialized as JSON numbers).
     */
    suspend fun postClientMetrics(metrics: JsonObject): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.post("$baseUrl/metrics/client") {
                setBody(metrics)
            }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Rentals
    // ---------------------------------------------------------------------------

    /**
     * POST /accounts/{accountId}/rentals — create a new rental for an asset.
     */
    suspend fun createRental(
        accountId: String,
        request: CreateRentalRequest,
    ): NetworkResult<RentalResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/rentals") {
                setBody(request)
            }.body()
        }

    /**
     * GET /accounts/{accountId}/assets/{assetId}/rentals — list rentals for an asset.
     */
    suspend fun getRentalsForAsset(
        accountId: String,
        assetId: String,
    ): NetworkResult<List<RentalResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/rentals").body()
        }

    /**
     * POST /accounts/{accountId}/rentals/{rentalId}/end — end an active rental.
     *
     * @param endAt ISO-8601 timestamp of when the rental ended.
     */
    suspend fun endRental(
        accountId: String,
        rentalId: String,
        endAt: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/rentals/$rentalId/end") {
                    setBody(mapOf("end_at" to endAt))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
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
    // Subthread replies
    // ---------------------------------------------------------------------------

    suspend fun getReplies(
        threadId: String,
        messageId: String,
    ): NetworkResult<ChatMessagesResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/$threadId/messages/$messageId/replies").body()
        }

    suspend fun sendReply(
        threadId: String,
        messageId: String,
        body: String,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/threads/$threadId/messages/$messageId/replies") {
                setBody(SendMessageRequest(body = body))
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Pin / unpin
    // ---------------------------------------------------------------------------

    suspend fun pinMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/threads/$threadId/messages/$messageId/pin")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    suspend fun unpinMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/threads/$threadId/messages/$messageId/pin")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Chat Thread Operations
    // ---------------------------------------------------------------------------

    /** GET /chat/threads/{threadId} — get a single thread. */
    suspend fun getThread(threadId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall { client.get("$baseUrl/chat/threads/$threadId").body() }

    /** GET /chat/threads/wo/{woId} — resolve the thread for a work order. */
    suspend fun resolveWoThread(accountId: String, woId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/wo/$woId") {
                parameter("account_id", accountId)
            }.body()
        }

    /** GET /chat/threads/asset/{assetId} — resolve the thread for an asset. */
    suspend fun resolveAssetThread(accountId: String, assetId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/asset/$assetId") {
                parameter("account_id", accountId)
            }.body()
        }

    /** GET /chat/threads/asset/{assetId}/timeline?cursor=... — asset timeline messages. */
    suspend fun getAssetTimeline(
        accountId: String,
        assetId: String,
        cursor: String? = null,
    ): NetworkResult<ChatPageResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/asset/$assetId/timeline") {
                parameter("account_id", accountId)
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /** GET /chat/threads/team — get the team/general thread. */
    suspend fun getTeamThread(accountId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/team") {
                parameter("account_id", accountId)
            }.body()
        }

    /** PUT /chat/threads/{threadId}/read — mark thread read. */
    suspend fun markThreadRead(threadId: String, lastReadMessageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/threads/$threadId/read") {
                    setBody(mapOf("last_read_message_id" to lastReadMessageId))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /chat/threads/{threadId}/notifications — toggle notifications. */
    suspend fun setThreadNotifications(threadId: String, enabled: Boolean): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/threads/$threadId/notifications") {
                    setBody(mapOf("enabled" to enabled))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /chat/threads/{threadId}/mute — mute thread. */
    suspend fun setThreadMute(
        threadId: String,
        muted: Boolean,
        hardMute: Boolean = false,
        until: Long? = null,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/threads/$threadId/mute") {
                    setBody(mapOf("muted" to muted, "hard_mute" to hardMute, "until" to until))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /chat/threads/{threadId}/favorite — favorite thread. */
    suspend fun setThreadFavorite(threadId: String, favorite: Boolean): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/threads/$threadId/favorite") {
                    setBody(mapOf("favorite" to favorite))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /chat/threads/{threadId}/archive — archive thread. */
    suspend fun archiveThread(threadId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/threads/$threadId/archive")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** DELETE /chat/threads/{threadId}/archive — unarchive thread. */
    suspend fun unarchiveThread(threadId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/threads/$threadId/archive")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Chat Direct/Group Thread Creation
    // ---------------------------------------------------------------------------

    /** POST /chat/direct — create or get DM thread with another user. */
    suspend fun createDirectThread(otherUserId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/direct") {
                setBody(mapOf("other_user_id" to otherUserId))
            }.body()
        }

    /** POST /chat/group — create a group thread. */
    suspend fun createGroupThread(
        name: String,
        memberIds: List<String>,
    ): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/group") {
                setBody(mapOf("name" to name, "member_ids" to memberIds))
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Chat Thread Members
    // ---------------------------------------------------------------------------

    /** GET /chat/threads/{threadId}/members */
    suspend fun getThreadMembers(threadId: String): NetworkResult<List<ChatMemberResponse>> =
        safeNetworkCall { client.get("$baseUrl/chat/threads/$threadId/members").body() }

    /** POST /chat/threads/{threadId}/members */
    suspend fun addThreadMembers(threadId: String, userIds: List<String>): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/threads/$threadId/members") {
                    setBody(mapOf("user_ids" to userIds))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** DELETE /chat/threads/{threadId}/members/{userId} */
    suspend fun removeThreadMember(threadId: String, userId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/threads/$threadId/members/$userId")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /chat/threads/{threadId}/leave */
    suspend fun leaveThread(threadId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/threads/$threadId/leave")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /chat/threads/{threadId}/name */
    suspend fun renameGroupThread(threadId: String, name: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/threads/$threadId/name") {
                    setBody(mapOf("name" to name))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** PUT /chat/threads/{threadId}/notification-pref */
    suspend fun setThreadNotificationPref(threadId: String, pref: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/threads/$threadId/notification-pref") {
                    setBody(mapOf("pref" to pref))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Chat Message Operations
    // ---------------------------------------------------------------------------

    /** GET /chat/link-preview?url=... */
    suspend fun fetchLinkPreview(url: String): NetworkResult<LinkPreviewResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/link-preview") {
                parameter("url", url)
            }.body()
        }

    /** PUT /chat/messages/delivered — mark messages delivered. */
    suspend fun markMessagesDelivered(messageIds: List<String>): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/messages/delivered") {
                    setBody(mapOf("message_ids" to messageIds))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /chat/messages/{messageId}/acknowledge */
    suspend fun acknowledgeMessage(messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/messages/$messageId/acknowledge")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** DELETE /chat/messages/{messageId}/reactions/{emoji} */
    suspend fun removeReaction(messageId: String, emoji: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/messages/$messageId/reactions/$emoji")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Chat Media Upload
    // ---------------------------------------------------------------------------

    /**
     * POST /chat/media/presign — get presigned upload URL for chat media.
     */
    suspend fun presignChatMedia(
        contentType: String,
        sizeBytes: Long,
    ): NetworkResult<ChatMediaPresignResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/media/presign") {
                setBody(mapOf("content_type" to contentType, "size_bytes" to sizeBytes))
            }.body()
        }

    /**
     * PUT [uploadUrl] — upload chat media binary to a presigned URL.
     *
     * Uses an unauthenticated client because presigned URLs are self-authenticating —
     * including an Authorization header would cause the request to be rejected.
     */
    suspend fun uploadChatMedia(
        uploadUrl: String,
        bytes: ByteArray,
        contentType: String,
    ): NetworkResult<Unit> {
        val uploadClient = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            val ct = ContentType.parse(contentType)
            val response: HttpResponse = uploadClient.put(uploadUrl) {
                setBody(ByteArrayContent(bytes, ct))
            }
            if (response.status.isSuccess()) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            Timber.e(e, "uploadChatMedia failed")
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        } finally {
            uploadClient.close()
        }
    }

    // ---------------------------------------------------------------------------
    // Chat Sync
    // ---------------------------------------------------------------------------

    /** GET /chat/sync?cursor=... — delta sync for chat. */
    suspend fun chatSync(cursor: String?): NetworkResult<ChatSyncResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/sync") {
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /** POST /chat/sync/ack — acknowledge sync cursor. */
    suspend fun chatSyncAck(cursor: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/sync/ack") {
                    setBody(mapOf("cursor" to cursor))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /chat/threads/{threadId}/typing — typing indicator. */
    suspend fun pingTyping(threadId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/threads/$threadId/typing")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Chat Preferences & Roster
    // ---------------------------------------------------------------------------

    /** GET /chat/me/prefs */
    suspend fun getChatPrefs(): NetworkResult<ChatPrefsResponse> =
        safeNetworkCall { client.get("$baseUrl/chat/me/prefs").body() }

    /** PUT /chat/me/prefs */
    suspend fun updateChatPrefs(prefs: ChatPrefsRequest): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.put("$baseUrl/chat/me/prefs") {
                    setBody(prefs)
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** GET /chat/me/roster */
    suspend fun getChatRoster(): NetworkResult<List<ChatRosterEntry>> =
        safeNetworkCall { client.get("$baseUrl/chat/me/roster").body() }

    /** GET /chat/me/mentions?cursor=... */
    suspend fun getMyMentions(cursor: String? = null): NetworkResult<ChatPageResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/me/mentions") {
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /** GET /chat/threads/{threadId}/media?cursor=... */
    suspend fun getThreadMedia(
        threadId: String,
        cursor: String? = null,
    ): NetworkResult<ChatPageResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/$threadId/media") {
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Photos — presigned upload
    // ---------------------------------------------------------------------------

    /**
     * POST /accounts/:accountId/photos/:photoId/upload-url
     *
     * Requests a presigned S3 upload URL for a locally-captured photo.
     */
    suspend fun getPhotoUploadUrl(
        accountId: String,
        photoId: String,
        entityId: String,
        entityType: String,
    ): NetworkResult<PhotoUploadUrlResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/photos/$photoId/upload-url") {
                setBody(
                    mapOf(
                        "entity_id" to entityId,
                        "entity_type" to entityType,
                    )
                )
            }.body()
        }

    /**
     * GET /accounts/:accountId/entities/:entityId/photos
     *
     * Fetches all photos attached to an entity, each with a download URL.
     */
    suspend fun getPhotosForEntity(
        accountId: String,
        entityId: String,
    ): NetworkResult<List<PhotoResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/entities/$entityId/photos").body()
        }

    /**
     * PUT [uploadUrl]
     *
     * Uploads raw image bytes to a presigned URL obtained from [getPhotoUploadUrl].
     * Uses an unauthenticated client because presigned S3 URLs are self-authenticating —
     * including an Authorization header would cause S3 to reject the request.
     */
    suspend fun uploadPhotoBinary(uploadUrl: String, bytes: ByteArray): NetworkResult<Unit> {
        val uploadClient = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            val response: HttpResponse = uploadClient.put(uploadUrl) {
                setBody(ByteArrayContent(bytes, ContentType.Image.JPEG))
            }
            if (response.status.isSuccess()) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            Timber.e(e, "uploadPhotoBinary failed")
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        } finally {
            uploadClient.close()
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
            NetworkResult.Success(withRetry("AvagoServiceClient") { block() })
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

    private suspend inline fun <reified T> safeCall(crossinline block: suspend () -> T): T {
        return try {
            withRetry("AvagoServiceClient") { block() }
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
