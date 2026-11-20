package com.avago.feature.rentals.data

import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.SyncRepository
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rentals REST surface outside the v2 sync feed (decision L38):
 * - server-computed, read-only answers (e-signature status, meter rules, scan resolve, the KPI
 *   card, payment status / pay links, the checkout credit dry run, buy-out quotes, the owner
 *   meet-up inbox, never-synced customer fields). The last good answer of
 *   each is held in memory per account, so a screen re-opened offline still shows it with its
 *   age, and a failed refresh never clears it;
 * - online actions that move money or talk to a third party (gateway charges / holds / refunds,
 *   pay links, post-charge "charge now", buy-out). They need a
 *   connection, are never queued, and are bound to the account they started in. The rows they
 *   change (ledger, post charges, contracts, orders) are synced data: after a success a sync
 *   brings them in, so nothing here writes to the local store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalsOnline @Inject constructor(
    private val identity: IdentityManager,
    private val api: RentalsApi,
    private val sync: SyncRepository,
    @ApplicationContext private val context: Context,
) {
    /** A cached answer and the device time (epoch ms) it was fetched. */
    data class Cached(val at: Long, val data: JsonObject)

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** The server answered with an error (403 = not permitted, 404 = not on this server…). */
        data class Refused(val status: Int, val code: String?, val message: String?) : Result<Nothing>
        /** No answer (offline / transport). */
        data class Offline(val message: String?) : Result<Nothing>
    }

    private val cache = MutableStateFlow<Map<Pair<String, String>, Cached>>(emptyMap())

    private fun acc(): String? = identity.getActiveAccountId()

    /** The last good answer under [key] for the active account (never another account's). */
    fun observe(key: String): Flow<Cached?> = identity.activeAccountId.flatMapLatest { acc ->
        if (acc == null) flowOf(null) else cache.map { it[acc to key] }.distinctUntilChanged()
    }

    /** Runs [fetch]; on success caches the answer under [key] (when given). */
    private suspend fun run(key: String?, fetch: suspend (acc: String) -> JsonObject): Result<JsonObject> {
        val acc = acc() ?: return Result.Offline("no account")
        return try {
            val data = fetch(acc)
            key?.let { k -> cache.update { it + ((acc to k) to Cached(System.currentTimeMillis(), data)) } }
            Result.Ok(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RentalsApiException) {
            Result.Refused(e.status, e.code, e.message)
        } catch (e: Exception) {
            Timber.d(e, "[RentalsOnline] $key unavailable")
            Result.Offline(e.message)
        }
    }

    /**
     * Runs an online action in [accountId] only: if the active account changed in between the
     * action is not sent ([ACCOUNT_CHANGED], status 0, a definitive "not sent"). A success is
     * followed by a sync so the rows the server changed arrive locally.
     */
    private suspend fun action(accountId: String, fetch: suspend (acc: String) -> JsonObject): Result<JsonObject> {
        if (acc() != accountId) return Result.Refused(0, ACCOUNT_CHANGED, null)
        val r = run(null, fetch)
        if (r is Result.Ok) sync.syncNow()
        return r
    }

    /** Like [action] in the active account. */
    private suspend fun action(fetch: suspend (acc: String) -> JsonObject): Result<JsonObject> =
        acc()?.let { action(it, fetch) } ?: Result.Offline("no account")

    fun activeAccountId(): String? = acc()

    private fun drop(key: String) {
        val acc = acc() ?: return
        cache.update { it - (acc to key) }
    }

    // ── keys ──

    fun signaturesKey(orderId: String) = "esign:$orderId"
    fun meterKey(assetId: String) = "meter:$assetId"
    fun creditKey(orderId: String) = "credit:$orderId"
    fun orderPaymentsKey(orderId: String) = "pay:$orderId"
    fun buyoutKey(contractId: String) = "buyout:$contractId"
    fun meetupInboxKey(status: String) = "mkt:meetups:$status"
    fun customerKey(customerId: String) = "customer:$customerId"
    val kpiKey = "kpi"
    val paymentsStatusKey = "payments_status"

    // ── refreshes ──

    suspend fun refreshSignatures(orderId: String) = run(signaturesKey(orderId)) { api.signatureRequests(it, orderId) }

    suspend fun refreshMeter(assetId: String) = run(meterKey(assetId)) { api.meterReadings(it, assetId) }

    suspend fun scanResolve(code: String, orderId: String?) = run(null) { api.scanResolve(it, code, orderId) }

    suspend fun refreshKpi(days: Int = 30) = run(kpiKey) { api.kpiSummary(it, days) }

    suspend fun refreshPaymentsStatus() = run(paymentsStatusKey) { api.paymentsStatus(it) }

    suspend fun refreshOrderPayments(orderId: String) = run(orderPaymentsKey(orderId)) { api.orderPayments(it, orderId) }

    suspend fun refreshCreditCheck(orderId: String, lineIds: List<String>) =
        run(creditKey(orderId)) { api.creditCheck(it, orderId, "checkout", lineIds) }

    suspend fun refreshBuyout(contractId: String) = run(buyoutKey(contractId)) { api.buyoutQuote(it, contractId) }

    /** The full customer (date of birth is encrypted at rest and never synced). */
    suspend fun refreshCustomer(customerId: String) = run(customerKey(customerId)) { api.getCustomer(it, customerId) }

    suspend fun refreshMeetupInbox(status: String) = run(meetupInboxKey(status)) { api.meetups(it, status, null) }

    /** Forgets the cached inbox (e.g. after access to it was withdrawn). */
    fun clearMeetupInbox(status: String) = drop(meetupInboxKey(status))

    // ── online actions (L38) ──

    suspend fun createPaymentRequest(body: JsonObject, clientUuid: String) =
        action { api.createPaymentRequest(it, body, clientUuid) }

    suspend fun sendPaymentRequest(requestId: String, to: String?) = action { api.sendPaymentRequest(it, requestId, to) }

    suspend fun linkPaymentRequest(requestId: String) = action { api.linkPaymentRequest(it, requestId) }

    /** Refreshes the order's payments in [accountId] (after a gateway action in that account). */
    suspend fun refreshOrderPayments(accountId: String, orderId: String): Result<JsonObject> =
        if (acc() != accountId) Result.Refused(0, ACCOUNT_CHANGED, null) else refreshOrderPayments(orderId)

    // Gateway actions are only offered while the server reports online_payments_enabled;
    // [key] stays stable across uncertain retries so money never moves twice.

    suspend fun chargeSavedCard(accountId: String, orderId: String, body: JsonObject, key: String) =
        action(accountId) { api.chargeSavedCard(it, orderId, body, key) }

    suspend fun captureAuthorization(accountId: String, entryId: String, body: JsonObject, key: String) =
        action(accountId) { api.captureAuthorization(it, entryId, body, key) }

    suspend fun releaseAuthorization(accountId: String, entryId: String, key: String) =
        action(accountId) { api.releaseAuthorization(it, entryId, key) }

    suspend fun refundGatewayPayment(accountId: String, entryId: String, body: JsonObject, key: String) =
        action(accountId) { api.refundGatewayPayment(it, entryId, body, key) }

    /** Creates a post charge and charges the saved card at once (replays by `client_uuid`). */
    suspend fun createPostChargeNow(accountId: String, orderId: String, body: JsonObject) =
        action(accountId) { api.createPostCharge(it, orderId, body) }

    /** Charges an existing (billed / failed) post charge to the saved card. */
    suspend fun chargePostCharge(accountId: String, orderId: String, chargeId: String) =
        action(accountId) { api.chargePostCharge(it, orderId, chargeId) }

    suspend fun buyout(accountId: String, contractId: String, body: JsonObject, key: String) =
        action(accountId) { api.buyout(it, contractId, body, key) }

    /** Authoritative price of a prospective booking (`POST /rentals/pricing/preview`, read-only). */
    suspend fun pricingPreview(q: BookingQuery) = run(null) { api.pricingPreview(it, q.pricingBody()) }

    /** Whether a renter booking [q] online would be confirmed instantly (read-only evaluation). */
    suspend fun instantBookEvaluate(q: BookingQuery) = run(null) { api.instantBookEvaluate(it, q.instantBody()) }

    /** Downloads a PDF into the app cache (shared through the FileProvider). */
    private suspend fun pdf(name: String, fetch: suspend (acc: String) -> ByteArray): Result<File> {
        val acc = acc() ?: return Result.Offline("no account")
        return try {
            val bytes = fetch(acc)
            val dir = File(context.cacheDir, "rental_pdfs").apply { mkdirs() }
            val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".pdf")
            f.writeBytes(bytes)
            Result.Ok(f)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RentalsApiException) {
            Result.Refused(e.status, e.code, e.message)
        } catch (e: Exception) {
            Result.Offline(e.message)
        }
    }

    suspend fun signaturePdf(requestId: String) = pdf("signature_$requestId") { api.signatureRequestPdf(it, requestId) }

    suspend fun equityStatementPdf(contractId: String) = pdf("equity_$contractId") { api.equityStatementPdf(it, contractId) }

    companion object {
        /** The active account changed before an online action was sent; nothing was sent. */
        const val ACCOUNT_CHANGED = "account_changed"
    }
}

/** A prospective booking, for the read-only pricing preview and instant-book evaluation. */
data class BookingQuery(
    val classId: String?,
    val assetId: String?,
    val customerId: String?,
    val locationId: String?,
    val currency: String,
    val start: Instant,
    val end: Instant,
) {
    /** `PreviewBody` (pricing.rs). */
    fun pricingBody(): JsonObject = buildJsonObject {
        put("currency", currency)
        customerId?.let { put("customer_id", it) }
        locationId?.let { put("location_id", it) }
        put("start_at", start.toString())
        put("end_at", end.toString())
        put(
            "lines",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("kind", "rental")
                        classId?.let { put("class_id", it) }
                        assetId?.let { put("asset_id", it) }
                        put("qty", 1)
                    },
                ),
            ),
        )
    }

    fun instantBody(): JsonObject = buildJsonObject {
        classId?.let { put("class_id", it) }
        assetId?.let { put("asset_id", it) }
        customerId?.let { put("customer_id", it) }
        put("start_at", start.toString())
        put("end_at", end.toString())
    }
}
