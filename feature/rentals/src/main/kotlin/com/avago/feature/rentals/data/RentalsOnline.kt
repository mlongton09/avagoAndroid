package com.avago.feature.rentals.data

import android.content.Context
import com.avago.core.auth.IdentityManager
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
 * Server-computed, read-only answers that are not account data and so are not part of the v2
 * sync feed: e-signature request status, meter rules, scan resolve, the KPI card, online payment
 * status, and the checkout credit dry run. Every call is a GET; nothing here writes (writes are v2
 * commands). The last good answer of each is held in memory per account, so a screen re-opened
 * offline still shows it with its age, and a failed refresh never clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalsOnline @Inject constructor(
    private val identity: IdentityManager,
    private val api: RentalsApi,
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

    // ── keys ──

    fun signaturesKey(orderId: String) = "esign:$orderId"
    fun meterKey(assetId: String) = "meter:$assetId"
    fun creditKey(orderId: String) = "credit:$orderId"
    fun orderPaymentsKey(orderId: String) = "pay:$orderId"
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
