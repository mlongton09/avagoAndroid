package com.avago.core.network

import android.content.Context
import com.avago.core.network.model.AccountFeaturesResponse
import com.avago.core.network.model.AccountResponse
import com.avago.core.network.model.AccountsEnvelope
import com.avago.core.network.model.PutAccountFeaturesRequest
import com.avago.core.network.model.MembersEnvelope
import com.avago.core.network.model.AiSkillResponse
import com.avago.core.network.model.AiSkillsEnvelope
import com.avago.core.network.model.AuditEventResponse
import com.avago.core.network.model.AuditListResponse
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
import com.avago.core.network.model.RolePermissionsSyncData
import com.avago.core.network.model.RolePermissionsSyncEnvelope
import com.avago.core.network.model.UpdatePreferencesRequest
import com.avago.core.network.model.UserPreferencesResponse
import com.avago.core.network.model.VinDecodeResponse
import com.avago.core.network.model.AssetCriticalityResponse
import com.avago.core.network.model.AssetModelResponse
import com.avago.core.network.model.RecommendedPartResponse
import com.avago.core.network.model.RcaReportResponse
import com.avago.core.network.model.CreateAssetCriticalityRequest
import com.avago.core.network.model.CreateAssetModelRequest
import com.avago.core.network.model.CreateRcaReportRequest
import com.avago.core.network.model.AddRecommendedPartRequest
import com.avago.core.network.model.FileUploadResponse
import com.avago.core.network.model.PresignedUploadRequest
import com.avago.core.network.model.PresignedUploadResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.avago.core.network.model.ChatMediaPresignResponse
import com.avago.core.network.model.ChatMemberResponse
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatMessagesResponse
import com.avago.core.network.model.SendMessageEnvelope
import com.avago.core.network.model.ChatPageResponse
import com.avago.core.network.model.ChatPrefsRequest
import com.avago.core.network.model.ChatPrefsResponse
import com.avago.core.network.model.ChatRosterEntry
import com.avago.core.network.model.ChatRosterEnvelope
import com.avago.core.network.model.ChatSyncResponse
import com.avago.core.network.model.ChatThreadResponse
import com.avago.core.network.model.ChatThreadsEnvelope
import com.avago.core.network.model.CreateThreadEnvelope
import com.avago.core.network.model.TeamThreadEnvelope
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
import com.avago.core.network.model.PartBinLocation
import com.avago.core.network.model.PartBinLocationsResponse
import com.avago.core.network.model.PartIssueResponse
import com.avago.core.network.model.CreateVendorPartRequest
import com.avago.core.network.model.UpdateVendorPartRequest
import com.avago.core.network.model.VendorPartResponse
import com.avago.core.network.model.ReorderSuggestionResponse
import com.avago.core.network.model.InvitationStatusResponse
import com.avago.core.network.model.WorkOrderResponse
import com.avago.core.network.model.WorkOrderPatch
import com.avago.core.network.model.RecurrenceResponse
import com.avago.core.network.model.RescheduleResponse
import com.avago.core.network.model.ProvisionRequest
import com.avago.core.network.model.PurchaseOrderResponse
import com.avago.core.network.model.ReactMessageRequest
import com.avago.core.network.model.RefreshRequest
import com.avago.core.network.model.ScoutEntityDto
import com.avago.core.network.model.ScoutExtractRequest
import com.avago.core.network.model.ScoutExtractResponse
import com.avago.core.network.model.ScoutScreenContext
import com.avago.core.network.model.SendMessageRequest
import com.avago.core.network.model.SignInRequest
import com.avago.core.network.model.UpgradeDeviceRequest
import com.avago.core.network.model.UpgradeRequest
import com.avago.core.network.model.SyncPullResponse
import com.avago.core.network.model.SyncPushRequest
import com.avago.core.network.model.SyncPushResponse
import com.avago.core.network.model.UserResponse
import com.avago.core.network.model.MessageReceiptResponse
import com.avago.core.network.model.BroadcastReceiptResponse
import com.avago.core.network.model.VendorResponse
import com.avago.core.network.model.PatchVendorRequest
import com.avago.core.network.model.StockingLevelResponse
import com.avago.core.network.model.UpsertStockingLevelRequest
import com.avago.core.network.model.SavedFilterViewResponse
import com.avago.core.network.model.CreateFilterViewRequest
import com.avago.core.network.model.WoCostSummaryResponse
import com.avago.core.network.model.ChatMessageSave
import com.avago.core.network.model.ChatUserStatus
import com.avago.core.network.model.ChatScheduledMessage
import com.avago.core.network.model.SetStatusRequest
import com.avago.core.network.model.UpdateScheduledMessageRequest
import com.avago.core.network.model.SaveMessageRequest
import com.avago.core.network.model.SetTopicRequest
import com.avago.core.network.model.SetDescriptionRequest
import com.avago.core.network.model.SkipOccurrenceRequest
import com.avago.core.network.model.CreateInventoryTransactionRequest
import com.avago.core.network.model.ReverseTransactionRequest
import com.avago.core.network.model.InventoryTransactionResponse
import com.avago.core.network.model.ConvertReorderToPORequest
import com.avago.core.network.model.ConvertReorderToPOResponse
import com.avago.core.network.model.PatchPurchaseOrderRequest
import com.avago.core.network.model.PurchaseOrdersResponse
import com.avago.core.network.model.VendorContactResponse
import com.avago.core.network.model.VendorContactsResponse
import com.avago.core.network.model.CreateVendorContactRequest
import com.avago.core.network.model.UpdateVendorContactRequest
import com.avago.core.network.model.KpiSummaryResponse
import com.avago.core.network.model.BulkUpdateWorkOrderRequest
import com.avago.core.network.model.BulkUpdateResponse
import com.avago.core.network.model.ChatMessageTemplate
import com.avago.core.network.model.CreateTemplateRequest
import com.avago.core.network.model.ChatWebhook
import com.avago.core.network.model.CreateWebhookRequest
import com.avago.core.network.model.CreateWebhookResponse
import com.avago.core.network.model.SyncConflict
import com.avago.core.network.model.SlashCommandRequest
import com.avago.core.network.model.GenerateCycleCountsRequest
import com.avago.core.network.model.CopyTemplateRequest
import com.avago.core.network.model.CycleCountLineResponse
import com.avago.core.network.model.CategoryResponse
import com.avago.core.network.model.DelegateApprovalRequest
import com.avago.core.network.model.ApprovalResponse
import com.avago.core.network.model.ApproveCostsRequest
import com.avago.core.network.model.WoCostApproval
import com.avago.core.network.model.PoCommentsResponse
import com.avago.core.network.model.CreatePoCommentRequest
import com.avago.core.network.model.PoCommentResponse
import com.avago.core.network.model.OrganizationResponse
import com.avago.core.network.model.OrgSummaryResponse
import com.avago.core.network.model.RequestPortalResponse
import com.avago.core.network.model.CreatePortalRequest
import com.avago.core.network.model.PortalSubmissionRequest
import com.avago.core.network.model.WorkRequestPortalResponse
import com.avago.core.network.model.CreateWorkRequestPortalRequest
import com.avago.core.network.model.SyncCheckpointResponse
import com.avago.core.network.model.AncestorItem
import com.avago.core.network.model.LocationHistoryEntry
import com.avago.core.network.model.QrBatchJobRequest
import com.avago.core.network.model.QrBatchJobResponse
import com.avago.core.network.model.QrScanEntry
import com.avago.core.network.model.RecordQrScanRequest
import com.avago.core.network.model.AssetHistoryEvent
import com.avago.core.network.model.BulkReassignRequest
import com.avago.core.network.model.BulkReassignResponse
import com.avago.core.network.model.WorkPermit
import com.avago.core.network.model.CreateWorkPermitRequest
import com.avago.core.network.model.BinContent
import com.avago.core.network.model.CustomFieldDef
import com.avago.core.network.model.CustomFieldValue
import com.avago.core.network.model.MeterTrigger
import com.avago.core.network.model.PmPlan
import com.avago.core.network.model.PermissionSet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AvagoServiceClient @Inject constructor(
    private val client: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
    @ApplicationContext private val context: Context,
) {

    private val _permissionsStaleEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val runtimeFlags by lazy {
        context.getSharedPreferences(RUNTIME_FLAGS_PREFS, Context.MODE_PRIVATE)
    }
    val permissionsStaleEvents: SharedFlow<String> = _permissionsStaleEvents.asSharedFlow()

    fun notifyPermissionsStale(accountId: String) {
        _permissionsStaleEvents.tryEmit(accountId)
    }

    /**
     * Clears Ktor's cached bearer token so the next request re-invokes [loadTokens]
     * and picks up the newly stored account credentials. Must be called after
     * switching the active account (e.g. after sign-in or account switch) so
     * subsequent calls don't reuse the previous session's cached token.
     *
     * Ktor 3.0 exposes Auth.providers and BearerAuthProvider.clearToken() as
     * public API — no reflection required. The prior reflection-based
     * implementation silently failed on Ktor 3.x (no exception, but the
     * cached anonymous token kept being sent post sign-in, producing 403s
     * on the newly-active account's sync/pull because the server saw the
     * anonymous user, which has no membership on that account).
     */
    fun clearBearerTokenCache() {
        try {
            client.authProviders
                .filterIsInstance<BearerAuthProvider>()
                .forEach { it.clearToken() }
        } catch (e: Exception) {
            Timber.w(e, "AvagoServiceClient: failed to clear bearer token cache")
        }
    }

    suspend fun provision(deviceId: String): AuthResponse =
        safeCall { client.post("$baseUrl/auth/provision") {
            setBody(ProvisionRequest(device_id = deviceId))
        }.body() }

    suspend fun signIn(firebaseToken: String, deviceId: String, provider: String = "firebase"): AuthResponse =
        safeCall { client.post("$baseUrl/auth/signin") {
            setBody(SignInRequest(provider = provider, oauth_token = firebaseToken, device_id = deviceId))
        }.body() }

    suspend fun upgradeAnonymous(firebaseToken: String, provider: String = "firebase") {
        safeCall<Unit> {
            val response: HttpResponse = client.post("$baseUrl/auth/upgrade") {
                setBody(UpgradeRequest(provider = provider, oauth_token = firebaseToken))
            }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun upgradeAnonymousDevice(
        deviceId: String,
        firebaseToken: String,
        provider: String = "firebase",
    ): AuthResponse =
        safeCall { client.post("$baseUrl/auth/upgrade-device") {
            setBody(UpgradeDeviceRequest(device_id = deviceId, provider = provider, oauth_token = firebaseToken))
        }.body() }

    suspend fun refreshTokens(refreshToken: String, deviceId: String): AuthResponse =
        safeCall { client.post("$baseUrl/auth/refresh") {
            setBody(RefreshRequest(refresh_token = refreshToken, device_id = deviceId))
        }.body() }

    /** GET /auth/refresh — check token validity / retrieve a fresh token without a body. */
    suspend fun checkAuthRefresh(): AuthResponse =
        safeCall { client.get("$baseUrl/auth/refresh").body() }

    suspend fun switchAccount(targetAccountId: String): NetworkResult<AuthResponse> =
        safeNetworkCall {
            client.post("$baseUrl/auth/switch-account") {
                setBody(mapOf("account_id" to targetAccountId))
            }.body()
        }

    suspend fun getMe(): UserResponse =
        safeCall { client.get("$baseUrl/users/me").body() }

    /** GET /accounts — list all accounts accessible to the current user. Mirrors iOS GET /accounts. */
    suspend fun getAllAccounts(): NetworkResult<List<AccountResponse>> =
        safeNetworkCall { client.get("$baseUrl/accounts").body<AccountsEnvelope>().accounts }

    suspend fun getAccountMembers(accountId: String): NetworkResult<List<UserResponse>> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/members").body<MembersEnvelope>().members }

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

    /** DELETE /accounts/{accountId}?hard=true|false — soft/hard account deletion. */
    suspend fun deleteAccount(accountId: String, hard: Boolean = false): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.delete("$baseUrl/accounts/$accountId") {
                if (hard) parameter("hard", "true")
            }
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

    /** GET /accounts/{accountId}/invitations/batch — check batch invitation statuses. */
    suspend fun getBatchInvitationStatuses(accountId: String): NetworkResult<List<InvitationStatusResponse>> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/invitations/batch").body() }

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

    /** Mirrors iOS: GET /accounts/:id/sync/pull?entity_type=role_permissions. */
    suspend fun pullRolePermissions(accountId: String): NetworkResult<RolePermissionsSyncData> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/sync/pull") {
                parameter("entity_type", "role_permissions")
            }.body<RolePermissionsSyncEnvelope>().data
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
    ): SyncPullResponse = safeCall {
        val response: HttpResponse = client.get("$baseUrl/accounts/$accountId/sync/pull") {
            parameter("entity_type", entityType)
            parameter("last_seq", lastSeq)
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) {
            val stale = if (response.status.value == 403) {
                runCatching {
                    val body = response.bodyAsText()
                    body.contains("\"stale_permissions\":true")
                }.getOrDefault(false)
            } else false
            throw NetworkException(
                response.status.value,
                "syncPull $entityType HTTP ${response.status.value}",
                stalePermissions = stale,
            )
        }
        response.body()
    }

    suspend fun getMembers(accountId: String): List<UserResponse> =
        safeCall { client.get("$baseUrl/accounts/$accountId/members").body<MembersEnvelope>().members }

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

    suspend fun receivePurchaseOrder(
        accountId: String,
        poId: String,
        lines: List<Map<String, Any>>,
    ) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/receive") {
                    setBody(mapOf("lines" to lines))
                }
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

    suspend fun startCycleCount(accountId: String, countId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/cycle-counts/$countId/start")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun completeCycleCount(accountId: String, countId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/cycle-counts/$countId/complete")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun postCycleCount(accountId: String, countId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/cycle-counts/$countId/post")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun cancelCycleCount(accountId: String, countId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/cycle-counts/$countId/cancel")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Purchase Order additional workflow actions
    // ---------------------------------------------------------------------------

    suspend fun submitPurchaseOrder(accountId: String, poId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/submit")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun rejectPurchaseOrder(accountId: String, poId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/reject")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun closePurchaseOrder(accountId: String, poId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/close")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun cancelPurchaseOrder(accountId: String, poId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/cancel")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun duplicatePurchaseOrder(accountId: String, poId: String): PurchaseOrderResponse =
        safeCall {
            client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/duplicate").body()
        }

    // ---------------------------------------------------------------------------
    // Vendor Parts
    // ---------------------------------------------------------------------------

    suspend fun createVendorPart(
        accountId: String,
        request: CreateVendorPartRequest,
    ): VendorPartResponse =
        safeCall {
            client.post("$baseUrl/accounts/$accountId/vendor-parts") {
                setBody(request)
            }.body()
        }

    suspend fun updateVendorPart(
        accountId: String,
        vendorPartId: String,
        request: UpdateVendorPartRequest,
    ): VendorPartResponse =
        safeCall {
            client.put("$baseUrl/accounts/$accountId/vendor-parts/$vendorPartId") {
                setBody(request)
            }.body()
        }

    suspend fun deleteVendorPart(accountId: String, vendorPartId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.delete("$baseUrl/accounts/$accountId/vendor-parts/$vendorPartId")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Reorder Suggestions
    // ---------------------------------------------------------------------------

    suspend fun getReorderSuggestions(accountId: String): List<ReorderSuggestionResponse> =
        safeCall {
            client.get("$baseUrl/accounts/$accountId/reorder-suggestions").body()
        }

    suspend fun regenerateReorderSuggestions(accountId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/reorder-suggestions/regenerate")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun dismissReorderSuggestion(accountId: String, suggestionId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/reorder-suggestions/$suggestionId/dismiss")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    suspend fun acceptReorderSuggestion(accountId: String, suggestionId: String) {
        safeCall<Unit> {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/reorder-suggestions/$suggestionId/accept")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Work Orders — direct REST calls
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/work-orders/:woId — fetch a single work order by ID. */
    suspend fun getWorkOrder(accountId: String, woId: String): NetworkResult<WorkOrderResponse> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/work-orders/$woId").body() }

    /** GET /accounts/:accountId/work-orders/:woId/recurrence — read recurrence rule. */
    suspend fun getWorkOrderRecurrence(accountId: String, woId: String): NetworkResult<RecurrenceResponse> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/work-orders/$woId/recurrence").body() }

    /** GET /accounts/:accountId/work-orders/:woId/reschedule — read reschedule config. */
    suspend fun getWorkOrderReschedule(accountId: String, woId: String): NetworkResult<RescheduleResponse> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/work-orders/$woId/reschedule").body() }

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
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/transition") {
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

    /**
     * PATCH /accounts/{accountId}/work-orders/{woId} — typed overload for field-level edits.
     * Change 3: only the non-null fields in [patch] are serialised and sent to the server.
     */
    suspend fun patchWorkOrder(
        accountId: String,
        woId: String,
        patch: WorkOrderPatch,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.patch("$baseUrl/accounts/$accountId/work-orders/$woId") {
                    setBody(patch)
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
            val envelope: AuditListResponse = client.get("$baseUrl/accounts/$accountId/work-orders/$woId/audit").body()
            envelope.entries
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

    /** GET /accounts/{accountId}/geocode — reverse-geocode lat/lon to an address. */
    suspend fun reverseGeocode(
        accountId: String,
        lat: Double,
        lon: Double,
    ): NetworkResult<GeocodeResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/geocode") {
                parameter("lat", lat)
                parameter("lon", lon)
            }.body()
        }

    /** GET /accounts/{accountId}/photos — list all photos for an account. */
    suspend fun getAccountPhotos(accountId: String): NetworkResult<List<PhotoResponse>> =
        safeNetworkCall { client.get("$baseUrl/accounts/$accountId/photos").body() }

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
                client.put("$baseUrl/accounts/$accountId/tech-profiles/$userId/location") {
                    setBody(mapOf("lat" to lat, "lon" to lon))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /accounts/:accountId/tech-profiles/:userId/location — push tech location (iOS style). */
    suspend fun postTechLocation(
        accountId: String,
        userId: String,
        lat: Double,
        lon: Double,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/tech-profiles/$userId/location") {
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
            client.get("$baseUrl/accounts/$accountId/ai/skills")
                .body<AiSkillsEnvelope>().skills
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
     * @param accountId      The active account — injected into screen_context.account_id.
     * @param query          Free-text or transcribed voice input (maps to transcript).
     * @param recentEntities MRU list of entities the user recently viewed.
     * @param currentScreen  Nav route of the currently visible screen.
     */
    suspend fun scoutQuery(
        accountId: String,
        query: String,
        recentEntities: List<ScoutEntityDto> = emptyList(),
        currentScreen: String? = null,
    ): NetworkResult<ScoutExtractResponse> =
        safeNetworkCall {
            client.post("$baseUrl/ai/extract") {
                setBody(
                    ScoutExtractRequest(
                        transcript = query,
                        screen_context = ScoutScreenContext(
                            account_id = accountId,
                            recent_entities = recentEntities,
                            current_screen = currentScreen,
                        ),
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
     * GET /accounts/{accountId}/rentals — list all rentals for an account.
     */
    suspend fun getRentals(accountId: String): NetworkResult<List<RentalResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/rentals").body()
        }

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

    suspend fun getThreads(): NetworkResult<List<ChatThreadResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/chat/me/threads")
                .body<ChatThreadsEnvelope>().threads
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
        imageUrls: List<String> = emptyList(),
        needsReply: Boolean? = null,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/threads/$threadId/messages") {
                setBody(
                    SendMessageRequest(
                        body_md = body,
                        photo_url = photoUrl,
                        image_urls = imageUrls,
                        needs_reply = needsReply,
                    )
                )
            }.body<SendMessageEnvelope>().message
        }

    suspend fun editMessage(
        threadId: String,
        messageId: String,
        body: String,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.put("$baseUrl/chat/messages/$messageId") {
                setBody(EditMessageRequest(body_md = body))
            }.body<SendMessageEnvelope>().message
        }

    /** PATCH /chat/messages/:messageId — partial-update a chat message (iOS style). */
    suspend fun patchMessage(
        messageId: String,
        body: String,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.patch("$baseUrl/chat/messages/$messageId") {
                setBody(EditMessageRequest(body_md = body))
            }.body()
        }

    suspend fun deleteMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/messages/$messageId")
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
                client.post("$baseUrl/chat/messages/$messageId/reactions") {
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
                setBody(SendMessageRequest(body_md = body))
            }.body<SendMessageEnvelope>().message
        }

    // ---------------------------------------------------------------------------
    // Pin / unpin
    // ---------------------------------------------------------------------------

    suspend fun pinMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/messages/$messageId/pin")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    suspend fun unpinMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/chat/messages/$messageId/pin")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /chat/messages/{messageId}/report — report message to admins. */
    suspend fun reportMessage(threadId: String, messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/chat/messages/$messageId/report")
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
            // Server wraps the thread as { "thread": {...}, "messages": {...} }
            // (chat_threads.rs resolve_wo_thread) — unwrap to .thread; the
            // CreateThreadEnvelope drops the `messages` sibling via ignoreUnknownKeys.
            client.get("$baseUrl/chat/threads/wo/$woId") {
                parameter("account_id", accountId)
            }.body<CreateThreadEnvelope>().thread
        }

    /** GET /chat/threads/asset/{assetId} — resolve the thread for an asset. */
    suspend fun resolveAssetThread(accountId: String, assetId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            // Same { "thread": {...}, "messages": {...} } envelope as resolve_wo_thread.
            client.get("$baseUrl/chat/threads/asset/$assetId") {
                parameter("account_id", accountId)
            }.body<CreateThreadEnvelope>().thread
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

    /** GET /chat/threads/team — get the team/general thread. Server returns
     *  `{thread: {...}, messages: {...}}`; we only need the thread metadata. */
    suspend fun getTeamThread(accountId: String): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/team") {
                parameter("account_id", accountId)
            }.body<TeamThreadEnvelope>().thread
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
            }.body<CreateThreadEnvelope>().thread
        }

    /** POST /chat/group — create a group thread. */
    suspend fun createGroupThread(
        name: String,
        memberIds: List<String>,
    ): NetworkResult<ChatThreadResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/group") {
                // Server's CreateGroupBody requires `member_user_ids` (no serde
                // alias / default — a `member_ids` payload 422s). See avagosvc
                // routes/chat_group.rs.
                setBody(mapOf("name" to name, "member_user_ids" to memberIds))
            }.body<CreateThreadEnvelope>().thread
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
        if (isForceOffline()) return NetworkResult.Error(OFFLINE_MODE_CODE, OFFLINE_MODE_MESSAGE)
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
        safeNetworkCall { client.get("$baseUrl/chat/me/roster").body<ChatRosterEnvelope>().members }

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
     * POST /accounts/:accountId/photos
     *
     * Reserves a server-side row and returns a presigned S3 upload URL.
     */
    suspend fun getPhotoUploadUrl(
        accountId: String,
        photoId: String,
        entityId: String,
        entityType: String,
    ): NetworkResult<PhotoUploadUrlResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/photos") {
                setBody(
                    mapOf(
                        "photo_id" to photoId,
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
        if (isForceOffline()) return NetworkResult.Error(OFFLINE_MODE_CODE, OFFLINE_MODE_MESSAGE)
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
    // Chat Read Receipts (Change 45)
    // ---------------------------------------------------------------------------

    suspend fun getMessageReadReceipts(messageId: String): NetworkResult<List<MessageReceiptResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/chat/messages/$messageId/receipts")
                .body<Map<String, List<MessageReceiptResponse>>>()["receipts"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Broadcast Compliance (Change 46)
    // ---------------------------------------------------------------------------

    suspend fun getBroadcastReceipt(messageId: String): NetworkResult<BroadcastReceiptResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/messages/$messageId/broadcast-receipt")
                .body<BroadcastReceiptResponse>()
        }

    // ---------------------------------------------------------------------------
    // Jump-to-Message (Change 47)
    // ---------------------------------------------------------------------------

    suspend fun listMessagesAround(threadId: String, aroundMessageId: String, limit: Int = 50): NetworkResult<ChatPageResponse> =
        safeNetworkCall {
            client.get("$baseUrl/chat/threads/$threadId/messages") {
                parameter("around", aroundMessageId)
                parameter("limit", limit)
            }.body<ChatPageResponse>()
        }

    // ---------------------------------------------------------------------------
    // PATCH Vendor with ETag (Change 49)
    // ---------------------------------------------------------------------------

    suspend fun patchVendor(accountId: String, vendorId: String, request: PatchVendorRequest, serverVersion: Int? = null): NetworkResult<VendorResponse> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/vendors/$vendorId") {
                if (serverVersion != null) {
                    headers.append("If-Match", "\"$serverVersion\"")
                }
                setBody(request)
            }.body<VendorResponse>()
        }

    // ---------------------------------------------------------------------------
    // Sub-Work-Orders (Change 15)
    // ---------------------------------------------------------------------------

    suspend fun listSubWorkOrders(accountId: String, woId: String): NetworkResult<List<WorkOrderResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/sub-work-orders")
                .body<Map<String, List<WorkOrderResponse>>>()["work_orders"] ?: emptyList()
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
            ensureOnlineMode()
            NetworkResult.Success(withRetry("AvagoServiceClient") { block() })
        } catch (e: UnauthorizedException) {
            NetworkResult.Unauthorized
        } catch (e: NetworkException) {
            NetworkResult.Error(e.code, e.message)
        } catch (e: io.ktor.client.plugins.ResponseException) {
            val code = e.response.status.value
            if (code == HttpStatusCode.Unauthorized.value) {
                NetworkResult.Unauthorized
            } else {
                Timber.e(e, "HTTP $code from service")
                val bodyText = runCatching { e.response.bodyAsText() }.getOrNull() ?: ""
                NetworkResult.Error(
                    code,
                    bodyText.ifBlank { e.response.status.description },
                )
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
            ensureOnlineMode()
            withRetry("AvagoServiceClient") { block() }
        } catch (e: NetworkException) {
            throw e
        } catch (e: io.ktor.client.plugins.ResponseException) {
            val code = e.response.status.value
            if (code == HttpStatusCode.Unauthorized.value) {
                throw UnauthorizedException()
            }
            Timber.e(e, "HTTP $code from service")
            val bodyText = runCatching { e.response.bodyAsText() }.getOrNull() ?: ""
            val retryAfter = parseRetryAfterSeconds(bodyText)
            val message = bodyText.ifBlank { e.response.status.description }.ifBlank { "HTTP $code" }
            throw NetworkException(code, message, retryAfter)
        } catch (e: Exception) {
            Timber.e(e, "Network call failed")
            throw NetworkException(-1, e.message ?: "Unknown error")
        }
    }

    private fun ensureOnlineMode() {
        if (isForceOffline()) throw NetworkException(OFFLINE_MODE_CODE, OFFLINE_MODE_MESSAGE)
    }

    private fun isForceOffline(): Boolean = runtimeFlags.getBoolean(FORCE_OFFLINE_PREF_KEY, false)

    // Parses "Wait for Xs" out of server 429 bodies. Returns null if not present.
    private fun parseRetryAfterSeconds(body: String): Long? {
        val match = Regex("""Wait for (\d+)s""").find(body) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    // ---------------------------------------------------------------------------
    // Asset Criticalities (Change 31)
    // ---------------------------------------------------------------------------

    suspend fun listAssetCriticalities(accountId: String): NetworkResult<List<AssetCriticalityResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/asset-criticalities")
                .body<Map<String, List<AssetCriticalityResponse>>>()["asset_criticalities"] ?: emptyList()
        }

    suspend fun createAssetCriticality(accountId: String, request: CreateAssetCriticalityRequest): NetworkResult<AssetCriticalityResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/asset-criticalities") {
                setBody(request)
            }.body<AssetCriticalityResponse>()
        }

    suspend fun updateAssetCriticality(accountId: String, critId: String, request: CreateAssetCriticalityRequest): NetworkResult<AssetCriticalityResponse> =
        safeNetworkCall {
            client.put("$baseUrl/accounts/$accountId/asset-criticalities/$critId") {
                setBody(request)
            }.body<AssetCriticalityResponse>()
        }

    suspend fun deleteAssetCriticality(accountId: String, critId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/asset-criticalities/$critId")
            Unit
        }

    // ---------------------------------------------------------------------------
    // Asset Models (Change 40) + Recommended Parts (Change 41)
    // ---------------------------------------------------------------------------

    suspend fun listAssetModels(accountId: String): NetworkResult<List<AssetModelResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/asset-models")
                .body<Map<String, List<AssetModelResponse>>>()["asset_models"] ?: emptyList()
        }

    suspend fun createAssetModel(accountId: String, request: CreateAssetModelRequest): NetworkResult<AssetModelResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/asset-models") {
                setBody(request)
            }.body<AssetModelResponse>()
        }

    suspend fun updateAssetModel(accountId: String, modelId: String, request: CreateAssetModelRequest): NetworkResult<AssetModelResponse> =
        safeNetworkCall {
            client.put("$baseUrl/accounts/$accountId/asset-models/$modelId") {
                setBody(request)
            }.body<AssetModelResponse>()
        }

    suspend fun deleteAssetModel(accountId: String, modelId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/asset-models/$modelId")
            Unit
        }

    suspend fun listRecommendedParts(accountId: String, modelId: String): NetworkResult<List<RecommendedPartResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/asset-models/$modelId/recommended-parts")
                .body<Map<String, List<RecommendedPartResponse>>>()["recommended_parts"] ?: emptyList()
        }

    suspend fun addRecommendedPart(accountId: String, modelId: String, request: AddRecommendedPartRequest): NetworkResult<RecommendedPartResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/asset-models/$modelId/recommended-parts") {
                setBody(request)
            }.body<RecommendedPartResponse>()
        }

    suspend fun removeRecommendedPart(accountId: String, modelId: String, partId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/asset-models/$modelId/recommended-parts/$partId")
            Unit
        }

    // ---------------------------------------------------------------------------
    // RCA Reports (Change 34)
    // ---------------------------------------------------------------------------

    suspend fun createRcaReport(accountId: String, request: CreateRcaReportRequest): NetworkResult<RcaReportResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/rca-reports") {
                setBody(request)
            }.body<RcaReportResponse>()
        }

    suspend fun getRcaReport(accountId: String, rcaId: String): NetworkResult<RcaReportResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/rca-reports/$rcaId").body<RcaReportResponse>()
        }

    suspend fun listRcaReportsForWorkOrder(accountId: String, woId: String): NetworkResult<List<RcaReportResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/rca-reports")
                .body<Map<String, List<RcaReportResponse>>>()["rca_reports"] ?: emptyList()
        }

    suspend fun listRcaReportsForAsset(accountId: String, assetId: String): NetworkResult<List<RcaReportResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/rca-reports")
                .body<Map<String, List<RcaReportResponse>>>()["rca_reports"] ?: emptyList()
        }

    suspend fun deleteRcaReport(accountId: String, rcaId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/rca-reports/$rcaId")
            Unit
        }

    // ---------------------------------------------------------------------------
    // Attachments (Changes 38-39)
    // ---------------------------------------------------------------------------

    suspend fun listAssetAttachments(accountId: String, assetId: String): NetworkResult<List<Map<String, Any>>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/attachments")
                .body<Map<String, Any>>()["photos"] as? List<Map<String, Any>> ?: emptyList()
        }

    suspend fun listWorkOrderAttachments(accountId: String, woId: String): NetworkResult<List<Map<String, Any>>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/attachments")
                .body<Map<String, Any>>()["photos"] as? List<Map<String, Any>> ?: emptyList()
        }

    suspend fun getPresignedUploadUrl(accountId: String, request: PresignedUploadRequest): NetworkResult<PresignedUploadResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/attachments/presigned-url") {
                setBody(request)
            }.body<PresignedUploadResponse>()
        }

    suspend fun finalizeFileUpload(accountId: String, fileUploadId: String, fileSizeBytes: Long? = null): NetworkResult<FileUploadResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/attachments/$fileUploadId/finalize") {
                setBody(mapOf("file_size_bytes" to fileSizeBytes))
            }.body<Map<String, FileUploadResponse>>()["file_upload"]!!
        }

    // ---------------------------------------------------------------------------
    // Recurrence Preview (Change 43)
    // ---------------------------------------------------------------------------

    suspend fun previewRecurrence(accountId: String, woId: String): NetworkResult<List<String>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/recurrence/preview")
                .body<Map<String, List<String>>>()["occurrences"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Stocking Levels (Changes 51-52)
    // ---------------------------------------------------------------------------

    suspend fun listStockingLevels(
        accountId: String,
        partId: String? = null,
        locationId: String? = null,
    ): NetworkResult<List<StockingLevelResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/stocking-levels") {
                partId?.let { parameter("part_id", it) }
                locationId?.let { parameter("location_id", it) }
            }.body<Map<String, List<StockingLevelResponse>>>()["stocking_levels"] ?: emptyList()
        }

    suspend fun upsertStockingLevel(
        accountId: String,
        request: UpsertStockingLevelRequest,
    ): NetworkResult<StockingLevelResponse> =
        safeNetworkCall {
            client.put("$baseUrl/accounts/$accountId/stocking-levels/upsert") {
                setBody(request)
            }.body<StockingLevelResponse>()
        }

    // ---------------------------------------------------------------------------
    // Filter Views (Change 54)
    // ---------------------------------------------------------------------------

    suspend fun listFilterViews(
        accountId: String,
        entityType: String? = null,
    ): NetworkResult<List<SavedFilterViewResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/filter-views") {
                entityType?.let { parameter("entity_type", it) }
            }.body<Map<String, List<SavedFilterViewResponse>>>()["filter_views"] ?: emptyList()
        }

    suspend fun createFilterView(
        accountId: String,
        request: CreateFilterViewRequest,
    ): NetworkResult<SavedFilterViewResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/filter-views") {
                setBody(request)
            }.body<SavedFilterViewResponse>()
        }

    suspend fun deleteFilterView(accountId: String, viewId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/filter-views/$viewId")
            Unit
        }

    // ---------------------------------------------------------------------------
    // Cost Line Approval + WO Cost Summary (Changes 55-56)
    // ---------------------------------------------------------------------------

    suspend fun approveCostLine(
        accountId: String,
        logId: String,
        costLineId: String,
    ): NetworkResult<JsonObject> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/log/$logId/cost-lines/$costLineId/approve")
                .body<JsonObject>()
        }

    suspend fun getWoCostSummary(accountId: String, woId: String): NetworkResult<WoCostSummaryResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/costs/summary")
                .body<WoCostSummaryResponse>()
        }

    // ---------------------------------------------------------------------------
    // Assets with Children (Change 60)
    // ---------------------------------------------------------------------------

    // Change 106: tags filter — pass a list of tags to filter assets (all must match)
    suspend fun listAssetsWithChildren(
        accountId: String,
        maxDepth: Int = 3,
        tags: List<String>? = null,
    ): NetworkResult<List<JsonObject>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/assets") {
                parameter("include_children", "true")
                parameter("max_depth", maxDepth)
                tags?.forEach { parameter("tags[]", it) }
            }.body<Map<String, List<JsonObject>>>()["assets"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Saved Messages (Change 67)
    // ---------------------------------------------------------------------------

    suspend fun saveMessage(messageId: String, note: String? = null): NetworkResult<ChatMessageSave> =
        safeNetworkCall {
            client.post("$baseUrl/chat/messages/$messageId/save") {
                setBody(SaveMessageRequest(note = note))
            }.body<Map<String, ChatMessageSave>>()["save"]!!
        }

    suspend fun unsaveMessage(messageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/chat/messages/$messageId/save")
            Unit
        }

    suspend fun listSavedMessages(limit: Int = 25, cursor: String? = null): NetworkResult<List<ChatMessageSave>> =
        safeNetworkCall {
            client.get("$baseUrl/chat/me/saved") {
                parameter("limit", limit)
                if (cursor != null) parameter("cursor", cursor)
            }.body<Map<String, List<ChatMessageSave>>>()["saves"] ?: emptyList()
        }

    suspend fun updateSaveNote(messageId: String, note: String?): NetworkResult<ChatMessageSave> =
        safeNetworkCall {
            client.patch("$baseUrl/chat/messages/$messageId/save") {
                setBody(SaveMessageRequest(note = note))
            }.body<Map<String, ChatMessageSave>>()["save"]!!
        }

    // ---------------------------------------------------------------------------
    // Custom User Status (Change 68)
    // ---------------------------------------------------------------------------

    suspend fun setUserStatus(request: SetStatusRequest): NetworkResult<Unit> =
        safeNetworkCall {
            client.put("$baseUrl/chat/me/status") {
                setBody(request)
            }
            Unit
        }

    suspend fun clearUserStatus(): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/chat/me/status")
            Unit
        }

    suspend fun getUserStatus(userId: String): NetworkResult<ChatUserStatus> =
        safeNetworkCall {
            client.get("$baseUrl/chat/users/$userId/status").body<ChatUserStatus>()
        }

    // ---------------------------------------------------------------------------
    // Scheduled Messages (Change 69)
    // ---------------------------------------------------------------------------

    suspend fun listScheduledMessages(accountId: String, threadId: String): NetworkResult<List<ChatScheduledMessage>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/chat/threads/$threadId/scheduled")
                .body<Map<String, List<ChatScheduledMessage>>>()["scheduled"] ?: emptyList()
        }

    suspend fun cancelScheduledMessage(accountId: String, scheduledMessageId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/chat/scheduled-messages/$scheduledMessageId")
            Unit
        }

    suspend fun updateScheduledMessage(accountId: String, scheduledMessageId: String, request: UpdateScheduledMessageRequest): NetworkResult<ChatScheduledMessage> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/chat/scheduled-messages/$scheduledMessageId") {
                setBody(request)
            }.body<Map<String, ChatScheduledMessage>>()["scheduled_message"]!!
        }

    // ---------------------------------------------------------------------------
    // Thread Topic and Description (Change 70)
    // ---------------------------------------------------------------------------

    suspend fun setThreadTopic(threadId: String, topic: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.put("$baseUrl/chat/threads/$threadId/topic") {
                setBody(SetTopicRequest(topic = topic))
            }
            Unit
        }

    suspend fun setThreadDescription(threadId: String, description: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.put("$baseUrl/chat/threads/$threadId/description") {
                setBody(SetDescriptionRequest(description = description))
            }
            Unit
        }

    // ---------------------------------------------------------------------------
    // Occurrence Skip with reason (Change 72)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/occurrences/:occurrenceId/skip */
    suspend fun skipOccurrence(
        accountId: String,
        occurrenceId: String,
        skipReasonCode: String? = null,
        skipReason: String? = null,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/occurrences/$occurrenceId/skip") {
                    setBody(SkipOccurrenceRequest(skip_reason_code = skipReasonCode, skip_reason = skipReason))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // Change 72: POST /accounts/:accountId/work-orders/:woId/skip
    suspend fun skipWorkOrder(
        accountId: String,
        woId: String,
        reasonCode: String? = null,
        notes: String? = null,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/skip") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody(SkipOccurrenceRequest(skip_reason_code = reasonCode, skip_reason = notes))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // Inventory Transactions (Changes 73-74)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/inventory-transactions */
    suspend fun createInventoryTransaction(
        accountId: String,
        request: CreateInventoryTransactionRequest,
    ): NetworkResult<InventoryTransactionResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/inventory-transactions") {
                setBody(request)
            }.body()
        }

    /** POST /accounts/:accountId/inventory-transactions/:transactionId/reverse */
    suspend fun reverseInventoryTransaction(
        accountId: String,
        transactionId: String,
        request: ReverseTransactionRequest,
    ): NetworkResult<InventoryTransactionResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/inventory-transactions/$transactionId/reverse") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Reorder Suggestions → PO conversion (Change 75)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/reorder-suggestions/convert-to-po */
    suspend fun convertReorderToPO(
        accountId: String,
        request: ConvertReorderToPORequest,
    ): NetworkResult<ConvertReorderToPOResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/reorder-suggestions/convert-to-po") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // PATCH Purchase Order (Change 76)
    // ---------------------------------------------------------------------------

    /** PATCH /accounts/:accountId/purchase-orders/:poId */
    suspend fun patchPurchaseOrder(
        accountId: String,
        poId: String,
        request: PatchPurchaseOrderRequest,
    ): NetworkResult<PurchaseOrderResponse> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/purchase-orders/$poId") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Work Order Purchase Orders (Change 77)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/work-orders/:woId/purchase-orders */
    suspend fun getPurchaseOrdersForWorkOrder(
        accountId: String,
        woId: String,
    ): NetworkResult<PurchaseOrdersResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/purchase-orders").body()
        }

    // ---------------------------------------------------------------------------
    // Vendor Contacts (Change 79)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/vendors/:vendorId/contacts */
    suspend fun listVendorContacts(
        accountId: String,
        vendorId: String,
    ): NetworkResult<List<VendorContactResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/vendors/$vendorId/contacts")
                .body<VendorContactsResponse>().contacts
        }

    /** POST /accounts/:accountId/vendors/:vendorId/contacts */
    suspend fun createVendorContact(
        accountId: String,
        vendorId: String,
        request: CreateVendorContactRequest,
    ): NetworkResult<VendorContactResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/vendors/$vendorId/contacts") {
                setBody(request)
            }.body()
        }

    /** PATCH /accounts/:accountId/vendors/:vendorId/contacts/:contactId */
    suspend fun updateVendorContact(
        accountId: String,
        vendorId: String,
        contactId: String,
        request: UpdateVendorContactRequest,
    ): NetworkResult<VendorContactResponse> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/vendors/$vendorId/contacts/$contactId") {
                setBody(request)
            }.body()
        }

    /** DELETE /accounts/:accountId/vendors/:vendorId/contacts/:contactId */
    suspend fun deleteVendorContact(
        accountId: String,
        vendorId: String,
        contactId: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.delete("$baseUrl/accounts/$accountId/vendors/$vendorId/contacts/$contactId")
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // ---------------------------------------------------------------------------
    // KPI Summary (Change 80)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/kpi/summary */
    suspend fun getKpiSummary(
        accountId: String,
        from: String? = null,
        to: String? = null,
        locationId: String? = null,
    ): NetworkResult<KpiSummaryResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/kpi/summary") {
                from?.let { parameter("from", it) }
                to?.let { parameter("to", it) }
                locationId?.let { parameter("location_id", it) }
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Bulk Work Order Update (Change 88)
    // ---------------------------------------------------------------------------

    suspend fun bulkUpdateWorkOrders(accountId: String, request: BulkUpdateWorkOrderRequest): NetworkResult<BulkUpdateResponse> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/work-orders/bulk") {
                setBody(request)
            }.body<BulkUpdateResponse>()
        }

    // ---------------------------------------------------------------------------
    // Team Work Order Queue (Change 84)
    // ---------------------------------------------------------------------------

    suspend fun listGroupWorkOrders(
        accountId: String,
        groupId: String,
        status: String? = null,
        priority: String? = null,
    ): NetworkResult<List<WorkOrderResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/groups/$groupId/work-orders") {
                if (status != null) parameter("status", status)
                if (priority != null) parameter("priority", priority)
            }.body<Map<String, List<WorkOrderResponse>>>()["work_orders"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Report CSV Export (Change 81)
    // ---------------------------------------------------------------------------

    fun getReportCsvUrl(accountId: String, reportKey: String, params: Map<String, String> = emptyMap()): String {
        val qs = (params + mapOf("format" to "csv")).entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$baseUrl/accounts/$accountId/reports/$reportKey?$qs"
    }

    // ---------------------------------------------------------------------------
    // Cycle Count Date Filters (Change 90)
    // ---------------------------------------------------------------------------

    suspend fun listCycleCountsFiltered(
        accountId: String,
        status: String? = null,
        dueBefore: String? = null,
        dueAfter: String? = null,
        limit: Int? = null,
    ): NetworkResult<List<CycleCountResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/cycle-counts") {
                if (status != null) parameter("status", status)
                if (dueBefore != null) parameter("due_before", dueBefore)
                if (dueAfter != null) parameter("due_after", dueAfter)
                if (limit != null) parameter("limit", limit)
            }.body<Map<String, List<CycleCountResponse>>>()["cycle_counts"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Chat Message Templates (Change 102)
    // ---------------------------------------------------------------------------

    suspend fun listPersonalTemplates(): NetworkResult<List<ChatMessageTemplate>> =
        safeNetworkCall {
            client.get("$baseUrl/chat/me/templates")
                .body<Map<String, List<ChatMessageTemplate>>>()["templates"] ?: emptyList()
        }

    suspend fun createPersonalTemplate(request: CreateTemplateRequest): NetworkResult<ChatMessageTemplate> =
        safeNetworkCall {
            client.post("$baseUrl/chat/me/templates") {
                setBody(request)
            }.body<Map<String, ChatMessageTemplate>>()["template"]!!
        }

    suspend fun updatePersonalTemplate(templateId: String, request: CreateTemplateRequest): NetworkResult<ChatMessageTemplate> =
        safeNetworkCall {
            client.put("$baseUrl/chat/me/templates/$templateId") {
                setBody(request)
            }.body<Map<String, ChatMessageTemplate>>()["template"]!!
        }

    suspend fun deletePersonalTemplate(templateId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/chat/me/templates/$templateId")
            Unit
        }

    suspend fun listAccountTemplates(accountId: String): NetworkResult<List<ChatMessageTemplate>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/chat/templates")
                .body<Map<String, List<ChatMessageTemplate>>>()["templates"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Chat Slash Commands (Change 103)
    // ---------------------------------------------------------------------------

    suspend fun executeSlashCommand(request: SlashCommandRequest): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/chat/commands") {
                setBody(request)
            }.body<Map<String, Any>>()["result"] as? Map<String, Any> ?: emptyMap()
        }

    // ---------------------------------------------------------------------------
    // Chat Webhooks (Change 104)
    // ---------------------------------------------------------------------------

    suspend fun listChatWebhooks(accountId: String): NetworkResult<List<ChatWebhook>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/chat/webhooks")
                .body<Map<String, List<ChatWebhook>>>()["webhooks"] ?: emptyList()
        }

    suspend fun createChatWebhook(accountId: String, request: CreateWebhookRequest): NetworkResult<CreateWebhookResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/chat/webhooks") {
                setBody(request)
            }.body<CreateWebhookResponse>()
        }

    suspend fun deleteChatWebhook(accountId: String, webhookId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/chat/webhooks/$webhookId")
            Unit
        }

    // ---------------------------------------------------------------------------
    // WO Template Copy (Change 109)
    // ---------------------------------------------------------------------------

    suspend fun copyWorkOrderTemplate(accountId: String, templateId: String, title: String? = null): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/wo-templates/$templateId/copy") {
                setBody(CopyTemplateRequest(title = title))
            }.body<Map<String, Any>>()["template"] as? Map<String, Any> ?: emptyMap()
        }

    // ---------------------------------------------------------------------------
    // Cycle Count Generation (Change 107)
    // ---------------------------------------------------------------------------

    suspend fun generateCycleCounts(accountId: String, request: GenerateCycleCountsRequest): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/cycle-counts/generate") {
                setBody(request)
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Sync Conflict Review (Change 110)
    // ---------------------------------------------------------------------------

    suspend fun listSyncConflicts(accountId: String, page: Int = 0): NetworkResult<List<SyncConflict>> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/sync/conflicts") {
                setBody(mapOf("mode" to "list", "page" to page))
            }.body<Map<String, List<SyncConflict>>>()["conflicts"] ?: emptyList()
        }

    suspend fun resolveSyncConflicts(accountId: String, conflictIds: List<String>, resolution: String): NetworkResult<Int> =
        safeNetworkCall {
            val res = client.post("$baseUrl/accounts/$accountId/sync/conflicts") {
                setBody(mapOf("mode" to "resolve", "conflict_ids" to conflictIds, "resolution" to resolution))
            }.body<Map<String, Any>>()
            (res["resolved_count"] as? Number)?.toInt() ?: 0
        }

    // ---------------------------------------------------------------------------
    // Cycle Count Lines (Change 91)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/cycle-counts/:countId/lines */
    suspend fun listCycleCountLines(
        accountId: String,
        countId: String,
        varianceOnly: Boolean? = null,
        minValue: Double? = null,
    ): NetworkResult<List<CycleCountLineResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/cycle-counts/$countId/lines") {
                if (varianceOnly != null) parameter("variance_only", varianceOnly)
                if (minValue != null) parameter("min_value", minValue)
            }.body<Map<String, List<CycleCountLineResponse>>>()["lines"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Categories (Changes 93+94)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/categories */
    suspend fun listCategories(
        accountId: String,
        includeTree: Boolean? = null,
    ): NetworkResult<List<CategoryResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/categories") {
                if (includeTree != null) parameter("include_tree", includeTree)
            }.body<Map<String, List<CategoryResponse>>>()["categories"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Approval Delegation (Change 95)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/approval-requests/:requestId/delegate */
    suspend fun delegateApproval(
        accountId: String,
        requestId: String,
        request: DelegateApprovalRequest,
    ): NetworkResult<ApprovalResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/approval-requests/$requestId/delegate") {
                setBody(request)
            }.body<ApprovalResponse>()
        }

    // ---------------------------------------------------------------------------
    // Work Order Cost Approvals (Change 98)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/work-orders/:woId/costs/approve */
    suspend fun approveWoCosts(
        accountId: String,
        woId: String,
        request: ApproveCostsRequest,
    ): NetworkResult<WoCostApproval> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/work-orders/$woId/costs/approve") {
                setBody(request)
            }.body<WoCostApproval>()
        }

    // ---------------------------------------------------------------------------
    // PO Comments (Change 100)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/purchase-orders/:poId/comments */
    suspend fun listPoComments(
        accountId: String,
        poId: String,
        cursor: String? = null,
        limit: Int? = null,
    ): NetworkResult<PoCommentsResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/purchase-orders/$poId/comments") {
                if (cursor != null) parameter("cursor", cursor)
                if (limit != null) parameter("limit", limit)
            }.body<PoCommentsResponse>()
        }

    /** POST /accounts/:accountId/purchase-orders/:poId/comments */
    suspend fun createPoComment(
        accountId: String,
        poId: String,
        content: String,
    ): NetworkResult<PoCommentResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/purchase-orders/$poId/comments") {
                setBody(CreatePoCommentRequest(content = content))
            }.body<PoCommentResponse>()
        }

    // ---------------------------------------------------------------------------
    // Organizations (Change 112)
    // ---------------------------------------------------------------------------

    suspend fun listOrganizations(): NetworkResult<List<OrganizationResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/organizations")
                .body<Map<String, List<OrganizationResponse>>>()["organizations"] ?: emptyList()
        }

    suspend fun getOrgSummary(orgId: String, from: String? = null, to: String? = null): NetworkResult<OrgSummaryResponse> =
        safeNetworkCall {
            client.get("$baseUrl/organizations/$orgId/summary") {
                from?.let { parameter("from", it) }
                to?.let { parameter("to", it) }
            }.body<OrgSummaryResponse>()
        }

    // ---------------------------------------------------------------------------
    // Request Portals (Change 113)
    // ---------------------------------------------------------------------------

    suspend fun createRequestPortal(accountId: String, request: CreatePortalRequest): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/request-portals") {
                setBody(request)
            }.body<Map<String, Any>>()
        }

    suspend fun submitPortal(portalToken: String, request: PortalSubmissionRequest): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/request-portals/$portalToken/submissions") {
                setBody(request)
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Sync Checkpoint (Change 114)
    // ---------------------------------------------------------------------------

    suspend fun getSyncCheckpoint(accountId: String, deviceId: String): NetworkResult<SyncCheckpointResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/sync/checkpoint") {
                headers { append("X-Device-Id", deviceId) }
            }.body<SyncCheckpointResponse>()
        }

    // ---------------------------------------------------------------------------
    // Work Request Portals (Change 116)
    // ---------------------------------------------------------------------------

    suspend fun listWorkRequestPortals(accountId: String): NetworkResult<List<WorkRequestPortalResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-request-portals")
                .body<Map<String, List<WorkRequestPortalResponse>>>()["portals"] ?: emptyList()
        }

    suspend fun createWorkRequestPortal(accountId: String, request: CreateWorkRequestPortalRequest): NetworkResult<WorkRequestPortalResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/work-request-portals") {
                setBody(request)
            }.body<Map<String, WorkRequestPortalResponse>>()["portal"]!!
        }

    suspend fun updateWorkRequestPortal(accountId: String, portalId: String, updates: Map<String, Any>): NetworkResult<WorkRequestPortalResponse> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/work-request-portals/$portalId") {
                setBody(updates)
            }.body<Map<String, WorkRequestPortalResponse>>()["portal"]!!
        }

    suspend fun deleteWorkRequestPortal(accountId: String, portalId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/work-request-portals/$portalId")
            Unit
        }

    // ---------------------------------------------------------------------------
    // Chat Link Preview (Change 119)
    // ---------------------------------------------------------------------------

    suspend fun getChatLinkPreview(url: String): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/chat/link-preview") {
                parameter("url", url)
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Asset Location History with GPS + Pagination (Changes 131+132+134)
    // ---------------------------------------------------------------------------

    suspend fun getAssetLocationHistory(
        accountId: String,
        assetId:   String,
        from:      String? = null,
        to:        String? = null,
        page:      Int? = null,
        pageSize:  Int? = null,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/location-history") {
                from?.let     { parameter("from",      it) }
                to?.let       { parameter("to",        it) }
                page?.let     { parameter("page",      it) }
                pageSize?.let { parameter("page_size", it) }
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Asset Transfer (Fix 2)
    // ---------------------------------------------------------------------------

    suspend fun transferAsset(
        accountId:    String,
        assetId:      String,
        toLocationId: String,
        moveReason:   String? = null,
        lat:          Double? = null,
        lng:          Double? = null,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/assets/$assetId/transfer") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(buildMap<String, Any?> {
                    put("to_location_id", toLocationId)
                    moveReason?.let { put("move_reason", it) }
                    lat?.let        { put("lat", it) }
                    lng?.let        { put("lng", it) }
                })
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // QR Batch Generation (Change 133)
    // ---------------------------------------------------------------------------

    suspend fun generateQrBatch(
        accountId:        String,
        assetIds:         List<String>,
        format:           String? = null,
        pageSize:         String? = null,
        includeAssetName: Boolean? = null,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/assets/qr-tokens/batch") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(buildMap<String, Any?> {
                    put("asset_ids", assetIds)
                    format?.let           { put("format", it) }
                    pageSize?.let         { put("page_size", it) }
                    includeAssetName?.let { put("include_asset_name", it) }
                })
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // QR Scan History (Change 135)
    // ---------------------------------------------------------------------------

    suspend fun getAssetQrScans(
        accountId: String,
        assetId:   String,
        from:      String? = null,
        to:        String? = null,
        page:      Int? = null,
        pageSize:  Int? = null,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/qr-scans") {
                from?.let     { parameter("from",      it) }
                to?.let       { parameter("to",        it) }
                page?.let     { parameter("page",      it) }
                pageSize?.let { parameter("page_size", it) }
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Record QR Scan with GPS (Change 132/135)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/assets/:assetId/qr-scans — record a scan with optional GPS. */
    suspend fun recordQrScan(
        accountId: String,
        request: RecordQrScanRequest,
    ): NetworkResult<QrScanEntry> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/assets/${request.asset_id}/qr-scans") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // AI Thread Summary Stub (Change 136)
    // ---------------------------------------------------------------------------

    suspend fun getThreadSummary(threadId: String): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/chat/threads/$threadId/summary")
                .body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // QBO Targeted Re-sync (Change 138)
    // ---------------------------------------------------------------------------

    suspend fun resyncQboEntity(
        accountId:  String,
        entityType: String,
        entityId:   String,
        force:      Boolean? = null,
        reason:     String? = null,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/integrations/qbo/sync/$entityType/$entityId") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(buildMap<String, Any?> {
                    force?.let  { put("force",  it) }
                    reason?.let { put("reason", it) }
                })
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // QBO Sync Directions (Change 139)
    // ---------------------------------------------------------------------------

    suspend fun getQboSyncDirections(accountId: String): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/accounts/$accountId/integrations/qbo/sync-directions")
                .body<Map<String, Any>>()
        }

    suspend fun putQboSyncDirections(
        accountId:        String,
        entityDirections: List<Map<String, String>>,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.put("$baseUrl/accounts/$accountId/integrations/qbo/sync-directions") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(mapOf("entity_directions" to entityDirections))
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // GL Mapping Rules (Change 140)
    // ---------------------------------------------------------------------------

    suspend fun listGlMappingRules(accountId: String): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.get("$baseUrl/accounts/$accountId/gl-accounts/mapping-rules")
                .body<Map<String, Any>>()
        }

    suspend fun createGlMappingRule(
        accountId:   String,
        name:        String,
        conditions:  List<Map<String, Any>>,
        glAccountId: String,
        priority:    Int? = null,
        enabled:     Boolean? = null,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/gl-accounts/mapping-rules") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(buildMap<String, Any?> {
                    put("name", name)
                    put("conditions", conditions)
                    put("gl_account_id", glAccountId)
                    priority?.let { put("priority", it) }
                    enabled?.let  { put("enabled",  it) }
                })
            }.body<Map<String, Any>>()
        }

    suspend fun deleteGlMappingRule(
        accountId: String,
        ruleId:    String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/gl-accounts/mapping-rules/$ruleId")
            Unit
        }

    // ── Account Feature Flags ─────────────────────────────────────────────────

    suspend fun getAccountFeatures(
        accountId: String,
    ): NetworkResult<AccountFeaturesResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/features").body()
        }

    suspend fun putAccountFeatures(
        accountId: String,
        patch:     PutAccountFeaturesRequest,
    ): NetworkResult<AccountFeaturesResponse> =
        safeNetworkCall {
            client.put("$baseUrl/accounts/$accountId/features") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(patch)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Asset Ancestors & History (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/assets/:assetId/ancestors */
    suspend fun getAssetAncestors(
        accountId: String,
        assetId: String,
    ): NetworkResult<List<AncestorItem>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/ancestors")
                .body<Map<String, List<AncestorItem>>>()["ancestors"] ?: emptyList()
        }

    /** GET /accounts/:accountId/assets/:assetId/history */
    suspend fun getAssetHistory(
        accountId: String,
        assetId: String,
        limit: Int = 50,
        cursor: String? = null,
    ): NetworkResult<List<AssetHistoryEvent>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/assets/$assetId/history") {
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
            }.body<Map<String, List<AssetHistoryEvent>>>()["events"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Work Order Batch Log Entries & Quick Work (new)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/work-orders/:woId/log-entries/batch */
    suspend fun batchCreateLogEntries(
        accountId: String,
        woId: String,
        entries: List<Map<String, Any>>,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/log-entries/batch") {
                    setBody(entries)
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** POST /accounts/:accountId/quick-work */
    suspend fun createQuickWork(
        accountId: String,
        body: Map<String, Any>,
    ): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/accounts/$accountId/quick-work") {
                setBody(body)
            }.body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Bulk Reassign Work Orders (new)
    // ---------------------------------------------------------------------------

    /** POST /accounts/:accountId/work-orders/bulk-reassign */
    suspend fun bulkReassignWorkOrders(
        accountId: String,
        request: BulkReassignRequest,
    ): NetworkResult<BulkReassignResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/work-orders/bulk-reassign") {
                setBody(request)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Work Permits (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/work-orders/:woId/permits */
    suspend fun listWorkPermits(
        accountId: String,
        woId: String,
    ): NetworkResult<List<WorkPermit>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/permits")
                .body<Map<String, List<WorkPermit>>>()["permits"] ?: emptyList()
        }

    /** POST /accounts/:accountId/work-orders/:woId/permits */
    suspend fun createWorkPermit(
        accountId: String,
        woId: String,
        request: CreateWorkPermitRequest,
    ): NetworkResult<WorkPermit> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/work-orders/$woId/permits") {
                setBody(request)
            }.body()
        }

    /** POST /accounts/:accountId/work-permits/:permitId/sign */
    suspend fun signWorkPermit(
        accountId: String,
        permitId: String,
        signatureData: String,
    ): NetworkResult<WorkPermit> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/work-permits/$permitId/sign") {
                setBody(mapOf("signature" to signatureData))
            }.body()
        }

    // ---------------------------------------------------------------------------
    // Bin Contents (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/bins/:binId/contents */
    suspend fun listBinContents(
        accountId: String,
        binId: String,
    ): NetworkResult<List<BinContent>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/bins/$binId/contents")
                .body<Map<String, List<BinContent>>>()["contents"] ?: emptyList()
        }

    /** POST /accounts/:accountId/bins/:binId/contents */
    suspend fun addBinContent(
        accountId: String,
        binId: String,
        partId: String,
        quantity: Int,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/bins/$binId/contents") {
                    setBody(mapOf("part_id" to partId, "quantity" to quantity))
                }
            if (!response.status.isSuccess()) {
                throw NetworkException(response.status.value, response.status.description)
            }
        }

    // Change 144: GET /accounts/:accountId/inventory?part_id=:partId&include_bins=true
    suspend fun getPartBinLocations(
        accountId: String,
        partId: String,
    ): NetworkResult<List<PartBinLocation>> =
        safeNetworkCall {
            val resp = client.get("$baseUrl/accounts/$accountId/inventory") {
                parameter("part_id", partId)
                parameter("include_bins", "true")
            }.body<PartBinLocationsResponse>()
            resp.bins
        }

    // ---------------------------------------------------------------------------
    // Custom Fields (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/custom-fields?entity_type=... */
    suspend fun listCustomFieldDefs(
        accountId: String,
        entityType: String,
    ): NetworkResult<List<CustomFieldDef>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/custom-fields") {
                parameter("entity_type", entityType)
            }.body<Map<String, List<CustomFieldDef>>>()["field_defs"] ?: emptyList()
        }

    /** GET /accounts/:accountId/custom-fields/values?entity_type=...&entity_id=... */
    suspend fun getEntityCustomValues(
        accountId: String,
        entityType: String,
        entityId: String,
    ): NetworkResult<List<CustomFieldValue>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/custom-fields/values") {
                parameter("entity_type", entityType)
                parameter("entity_id", entityId)
            }.body<Map<String, List<CustomFieldValue>>>()["values"] ?: emptyList()
        }

    // ---------------------------------------------------------------------------
    // Meter Triggers (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/meter-triggers */
    suspend fun listMeterTriggers(accountId: String): NetworkResult<List<MeterTrigger>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/meter-triggers")
                .body<Map<String, List<MeterTrigger>>>()["triggers"] ?: emptyList()
        }

    /** POST /accounts/:accountId/meter-triggers */
    suspend fun createMeterTrigger(
        accountId: String,
        trigger: MeterTrigger,
    ): NetworkResult<MeterTrigger> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/meter-triggers") {
                setBody(trigger)
            }.body()
        }

    // ---------------------------------------------------------------------------
    // PM Plans (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/pm-plans */
    suspend fun listPmPlans(accountId: String): NetworkResult<List<PmPlan>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/pm-plans")
                .body<Map<String, List<PmPlan>>>()["plans"] ?: emptyList()
        }

    /** POST /accounts/:accountId/pm-plans */
    suspend fun createPmPlan(
        accountId: String,
        plan: PmPlan,
    ): NetworkResult<PmPlan> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/pm-plans") {
                setBody(plan)
            }.body()
        }

    suspend fun updatePmPlan(
        accountId: String,
        planId: String,
        plan: PmPlan,
    ): NetworkResult<PmPlan> =
        safeNetworkCall {
            client.patch("$baseUrl/accounts/$accountId/pm-plans/$planId") {
                setBody(plan)
            }.body()
        }

    suspend fun deletePmPlan(
        accountId: String,
        planId: String,
    ): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/pm-plans/$planId").body()
        }

    // ---------------------------------------------------------------------------
    // Permission Sets (new)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/permission-sets */
    suspend fun listPermissionSets(accountId: String): NetworkResult<List<PermissionSet>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/permission-sets")
                .body<Map<String, List<PermissionSet>>>()["permission_sets"] ?: emptyList()
        }

    /** POST /accounts/:accountId/permission-sets */
    suspend fun createPermissionSet(accountId: String, body: Map<String, Any>): NetworkResult<PermissionSet> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/permission-sets") { setBody(body) }.body()
        }

    /** DELETE /accounts/:accountId/permission-sets/:setId */
    suspend fun deletePermissionSet(accountId: String, setId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/permission-sets/$setId").body()
        }

    // ---------------------------------------------------------------------------
    // Custom Field Defs
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/custom-field-defs */
    suspend fun listCustomFieldDefs(accountId: String): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/custom-field-defs").body()
        }

    /** POST /accounts/:accountId/custom-field-defs */
    suspend fun createCustomFieldDef(accountId: String, body: Map<String, Any>): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/custom-field-defs") { setBody(body) }.body()
        }

    /** DELETE /accounts/:accountId/custom-field-defs/:fieldId */
    suspend fun deleteCustomFieldDef(accountId: String, fieldId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.delete("$baseUrl/accounts/$accountId/custom-field-defs/$fieldId").body()
        }

    // ---------------------------------------------------------------------------
    // Report KPIs (new — typed alias over getKpiSummary)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/reports/kpis */
    suspend fun getReportKPIs(accountId: String): NetworkResult<KpiSummaryResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/reports/kpis").body()
        }

    // ---------------------------------------------------------------------------
    // Part Transfer Requests
    // ---------------------------------------------------------------------------

    suspend fun createPartTransferRequest(
        accountId: String,
        request: CreatePartTransferRequestBody,
    ): NetworkResult<PartTransferRequestResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/part-transfer-requests") {
                setBody(request)
            }.body()
        }

    suspend fun approvePartTransferRequest(accountId: String, requestId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/part-transfer-requests/$requestId/approve").body()
        }

    suspend fun rejectPartTransferRequest(accountId: String, requestId: String): NetworkResult<Unit> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/part-transfer-requests/$requestId/reject").body()
        }
}

class NetworkException(
    val code: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
    val stalePermissions: Boolean = false,
) : Exception(message)
class UnauthorizedException : Exception("Unauthorized")

private const val RUNTIME_FLAGS_PREFS = "avago_runtime_flags"
private const val FORCE_OFFLINE_PREF_KEY = "force_offline"
private const val OFFLINE_MODE_CODE = 0
private const val OFFLINE_MODE_MESSAGE = "Offline mode is enabled"
