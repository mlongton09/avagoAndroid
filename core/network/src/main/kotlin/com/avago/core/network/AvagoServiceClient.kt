package com.avago.core.network

import android.content.Context
import com.avago.core.network.model.AccountResponse
import com.avago.core.network.model.AccountsEnvelope
import com.avago.core.network.model.MembersEnvelope
import com.avago.core.network.model.AiSkillResponse
import com.avago.core.network.model.AiSkillsEnvelope
import com.avago.core.network.model.AuthResponse
import com.avago.core.network.model.BulkInvitation
import com.avago.core.network.model.DocOcrReextractResponse
import com.avago.core.network.model.DocOcrResponse
import com.avago.core.network.model.GeocodeRequest
import com.avago.core.network.model.GeocodeResponse
import com.avago.core.network.model.RolePermissionsSyncData
import com.avago.core.network.model.RolePermissionsSyncEnvelope
import com.avago.core.network.model.VinDecodeResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.avago.core.network.model.ChatMediaPresignResponse
import com.avago.core.network.model.ChatMemberResponse
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatMessagesResponse
import com.avago.core.network.model.SendMessageEnvelope
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
import com.avago.core.network.model.CreatePurchaseOrderRequest
import com.avago.core.network.model.DeviceUpdateRequest
import com.avago.core.network.model.EditMessageRequest
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
import com.avago.core.network.model.UserResponse
import com.avago.core.network.model.SkipOccurrenceRequest
import com.avago.core.network.model.KpiSummaryResponse
import com.avago.core.network.model.WorkOrderBudgetsResponse
import com.avago.core.network.model.EffortStats
import com.avago.core.network.model.WorkOrderAuditResponse
import com.avago.core.network.model.InspectionDraftRow
import com.avago.core.network.model.PutInspectionDraftRequest
import com.avago.core.network.model.SampleWorkspaceJson
import com.avago.core.network.model.SampleWorkspacesResponse
import com.avago.core.network.model.SwitchSampleWorkspaceRequest
import com.avago.core.network.model.SwitchSampleWorkspaceResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
import kotlinx.serialization.json.put
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

    // ---------------------------------------------------------------------------
    // Sample workspaces (SystemAdmin only — non-admins get 401/403)
    // ---------------------------------------------------------------------------

    /** GET /admin/sample-data/workspaces — sample profiles plus the caller's saved demo account per profile. */
    suspend fun getSampleWorkspaces(): NetworkResult<SampleWorkspacesResponse> =
        safeNetworkCall {
            SampleWorkspaceJson.decodeProfiles(
                client.get("$baseUrl/admin/sample-data/workspaces").body<JsonObject>(),
            )
        }

    /**
     * POST /admin/sample-data/workspaces/switch — creates (or reopens) the caller's demo account
     * for [profileKey]. Does NOT change the current account; the caller switches locally.
     * Creating a large (e.g. rental) workspace can take tens of seconds, so this call gets a
     * long per-request timeout, and a timeout is reported instead of being retried (a retry would
     * restart the same long server-side job).
     */
    suspend fun switchSampleWorkspace(profileKey: String): NetworkResult<SwitchSampleWorkspaceResponse> =
        safeNetworkCall {
            try {
                SampleWorkspaceJson.decodeSwitch(
                    client.post("$baseUrl/admin/sample-data/workspaces/switch") {
                        timeout {
                            requestTimeoutMillis = SAMPLE_WORKSPACE_SWITCH_TIMEOUT_MS
                            socketTimeoutMillis = SAMPLE_WORKSPACE_SWITCH_TIMEOUT_MS
                        }
                        setBody(SwitchSampleWorkspaceRequest(profileKey = profileKey))
                    }.body<JsonObject>(),
                )
            } catch (e: HttpRequestTimeoutException) {
                throw NetworkException(-1, e.message ?: "Request timed out")
            } catch (e: java.net.SocketTimeoutException) {
                throw NetworkException(-1, e.message ?: "Request timed out")
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

    /** Mirrors iOS: GET /accounts/:id/sync/pull?entity_type=role_permissions. */
    suspend fun pullRolePermissions(accountId: String): NetworkResult<RolePermissionsSyncData> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/sync/pull") {
                parameter("entity_type", "role_permissions")
            }.body<RolePermissionsSyncEnvelope>().data
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

    suspend fun getMembers(accountId: String): List<UserResponse> =
        safeCall { client.get("$baseUrl/accounts/$accountId/members").body<MembersEnvelope>().members }

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

    /** POST /accounts/{accountId}/work-orders/{woId}/assignments/claim — self-claim a WO. */
    suspend fun claimWorkOrder(accountId: String, woId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse =
                client.post("$baseUrl/accounts/$accountId/work-orders/$woId/assignments/claim")
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

    /**
     * GET /accounts/{accountId}/ai/skills — list the Scout skills available for an account.
     */
    suspend fun getAiSkills(accountId: String): NetworkResult<List<AiSkillResponse>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/ai/skills")
                .body<AiSkillsEnvelope>().skills
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

    /**
     * POST /accounts/{accountId}/docs/{docId}/ocr-extract: re-runs field extraction for a stored
     * document (online action). The text and fields are stored on its current revision; the
     * revision's OCR status syncs.
     *
     * @param ocrRawText   Fresh text (a re-scan); null re-uses the text of the last run.
     * @param documentType Extraction type; null derives it from the document's kind.
     */
    suspend fun reextractDocOcr(
        accountId: String,
        docId: String,
        ocrRawText: String?,
        documentType: String?,
        locale: String,
    ): NetworkResult<DocOcrReextractResponse> =
        safeNetworkCall {
            client.post("$baseUrl/accounts/$accountId/docs/$docId/ocr-extract") {
                setBody(
                    buildMap {
                        ocrRawText?.let { put("ocr_raw_text", it) }
                        documentType?.let { put("document_type", it) }
                        put("locale", locale)
                    },
                )
            }.body()
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
        clientRef: String? = null,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/threads/$threadId/messages") {
                setBody(
                    SendMessageRequest(
                        body_md = body,
                        photo_url = photoUrl,
                        image_urls = imageUrls,
                        needs_reply = needsReply,
                        client_ref = clientRef,
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
        clientRef: String? = null,
    ): NetworkResult<ChatMessageResponse> =
        safeNetworkCall {
            client.post("$baseUrl/chat/threads/$threadId/messages/$messageId/replies") {
                setBody(SendMessageRequest(body_md = body, client_ref = clientRef))
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
    // AI Thread Summary Stub (Change 136)
    // ---------------------------------------------------------------------------

    suspend fun getThreadSummary(threadId: String): NetworkResult<Map<String, Any>> =
        safeNetworkCall {
            @Suppress("UNCHECKED_CAST")
            client.post("$baseUrl/chat/threads/$threadId/summary")
                .body<Map<String, Any>>()
        }

    // ---------------------------------------------------------------------------
    // Report KPIs (new — typed alias over getKpiSummary)
    // ---------------------------------------------------------------------------

    /** GET /accounts/:accountId/reports/kpis */
    suspend fun getReportKPIs(accountId: String): NetworkResult<KpiSummaryResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/reports/kpis").body()
        }

    /** GET /accounts/{accountId}/work-orders/{woId}/audit: the work order's history, newest first. */
    suspend fun getWorkOrderAudit(accountId: String, woId: String): NetworkResult<WorkOrderAuditResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/audit").body()
        }

    /** GET /accounts/{accountId}/inspection-drafts?asset_id=&log_id=: the caller's server drafts. */
    suspend fun getInspectionDrafts(accountId: String, assetId: String, logId: String?): NetworkResult<List<InspectionDraftRow>> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/inspection-drafts") {
                parameter("asset_id", assetId)
                if (logId != null) parameter("log_id", logId)
            }.body()
        }

    /** PUT /accounts/{accountId}/inspection-drafts: upserts the caller's draft for (asset, log). */
    suspend fun putInspectionDraft(accountId: String, body: PutInspectionDraftRequest): NetworkResult<InspectionDraftRow> =
        safeNetworkCall {
            client.put("$baseUrl/accounts/$accountId/inspection-drafts") { setBody(body) }.body()
        }

    /** DELETE /accounts/{accountId}/inspection-drafts/{draftId} (own drafts only). */
    suspend fun deleteInspectionDraft(accountId: String, draftId: String): NetworkResult<Unit> =
        safeNetworkCall {
            val response: HttpResponse = client.delete("$baseUrl/accounts/$accountId/inspection-drafts/$draftId")
            if (!response.status.isSuccess()) throw NetworkException(response.status.value, response.status.description)
        }

    /**
     * GET /accounts/{accountId}/work-orders/{woId}/effort-stats: the effort benchmark for the work
     * order's asset type and category; `null` when the server has none (204).
     */
    suspend fun getEffortStats(
        accountId: String,
        woId: String,
        category: String,
        assetType: String,
        complexity: String,
    ): NetworkResult<EffortStats?> =
        safeNetworkCall {
            val response: HttpResponse = client.get("$baseUrl/accounts/$accountId/work-orders/$woId/effort-stats") {
                parameter("category", category)
                parameter("asset_type", assetType)
                parameter("complexity", complexity)
            }
            when {
                response.status == HttpStatusCode.NoContent -> null
                response.status.isSuccess() -> response.body<EffortStats>()
                else -> throw NetworkException(response.status.value, response.status.description)
            }
        }

    /** GET /accounts/{accountId}/work-orders/{woId}/budget-pill: active budgets whose scope covers the work order. */
    suspend fun getWorkOrderBudgets(accountId: String, woId: String): NetworkResult<WorkOrderBudgetsResponse> =
        safeNetworkCall {
            client.get("$baseUrl/accounts/$accountId/work-orders/$woId/budget-pill").body()
        }

}

class NetworkException(
    val code: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
    val stalePermissions: Boolean = false,
) : Exception(message)
class UnauthorizedException : Exception("Unauthorized")

private const val SAMPLE_WORKSPACE_SWITCH_TIMEOUT_MS = 180_000L
private const val RUNTIME_FLAGS_PREFS = "avago_runtime_flags"
private const val FORCE_OFFLINE_PREF_KEY = "force_offline"
private const val OFFLINE_MODE_CODE = 0
private const val OFFLINE_MODE_MESSAGE = "Offline mode is enabled"
