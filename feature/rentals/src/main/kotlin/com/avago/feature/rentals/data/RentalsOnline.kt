package com.avago.feature.rentals.data

import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.feature.rentals.domain.Esign
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.parseObj
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online-only reads of wave 2 (signature status, meter, scan resolve, KPI, online payments,
 * buy-out quote, credit check). These are not sync feeds, so the last good answer of each is
 * kept as a `rental_meta` record `w2:<key>` = `{at, data}`: screens render it offline (with its
 * age) and a refresh replaces it. A failed refresh never clears the last good copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalsOnline @Inject constructor(
    private val dbFactory: DatabaseFactory,
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

    private fun acc(): String? = identity.getActiveAccountId()

    fun observe(key: String): Flow<Cached?> = identity.activeAccountId.flatMapLatest { acc ->
        if (acc == null) flowOf(null)
        else kotlinx.coroutines.flow.flow<RentalRecordEntity?> {
            emit(null) // Payment gates and cached read models must not cross an account switch.
            emitAll(dbFactory.get(acc).rentalDao().observe(RentalEntity.META, PREFIX + key))
        }.map { r: RentalRecordEntity? -> r?.let { parseObj(it.json) }?.let { o -> o.obj("data")?.let { Cached(o.longAt(), it) } } }
    }

    private fun JsonObject.longAt(): Long = (this["at"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    private suspend fun store(acc: String, key: String, data: JsonObject) {
        val now = System.currentTimeMillis()
        dbFactory.get(acc).rentalDao().upsert(
            RentalRecordEntity(
                entity = RentalEntity.META,
                id = PREFIX + key,
                json = RentalJson.encodeToString(JsonObject.serializer(), buildJsonObject { put("at", now); put("data", data) }),
                updatedAt = now,
            ),
        )
    }

    /** Runs [fetch]; on success caches the answer under [key] (when given). */
    private suspend fun run(key: String?, fetch: suspend (acc: String) -> JsonObject): Result<JsonObject> {
        val acc = acc() ?: return Result.Offline("no account")
        return runAs(acc, key, fetch)
    }

    /** Like [run] for an explicit account; the cache write goes to that account's store only. */
    private suspend fun runAs(acc: String, key: String?, fetch: suspend (acc: String) -> JsonObject): Result<JsonObject> {
        return try {
            val data = fetch(acc)
            key?.let { store(acc, it, data) }
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
     * Money-moving call bound to the account the user acted in: nothing is sent when the active
     * account changed in between ([ACCOUNT_CHANGED], status 0, a definitive "not sent").
     */
    private suspend fun runFor(accountId: String, fetch: suspend (acc: String) -> JsonObject): Result<JsonObject> {
        if (acc() != accountId) return Result.Refused(0, ACCOUNT_CHANGED, null)
        return runAs(accountId, null, fetch)
    }

    fun activeAccountId(): String? = acc()

    // ── keys ─────────────────────────────────────────────────────────────────────────

    fun signaturesKey(orderId: String) = "esign:$orderId"
    fun meterKey(assetId: String) = "meter:$assetId"
    fun creditKey(orderId: String) = "credit:$orderId"
    fun orderPaymentsKey(orderId: String) = "pay:$orderId"
    fun buyoutKey(contractId: String) = "buyout:$contractId"
    val kpiKey = "kpi"
    val paymentsStatusKey = "payments_status"

    // ── refreshes ────────────────────────────────────────────────────────────────────

    suspend fun refreshSignatures(orderId: String) = run(signaturesKey(orderId)) { api.signatureRequests(it, orderId) }

    /** From the outbox (account known). Failures are ignored: the screen refreshes on open. */
    suspend fun refreshSignatures(accountId: String, orderId: String) {
        try {
            store(accountId, signaturesKey(orderId), api.signatureRequests(accountId, orderId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "[RentalsOnline] signature refresh after capture failed")
        }
    }

    suspend fun createInPersonRequest(orderId: String, customerName: String, locale: String?): Result<JsonObject> =
        run(null) { api.createSignatureRequest(it, orderId, Esign.createInPersonBody(customerName, locale), UUID.randomUUID().toString()) }
            .also { if (it is Result.Ok) refreshSignatures(orderId) }

    suspend fun refreshMeter(assetId: String) = run(meterKey(assetId)) { api.meterReadings(it, assetId) }

    suspend fun scanResolve(code: String, orderId: String?) = run(null) { api.scanResolve(it, code, orderId) }

    suspend fun refreshKpi(days: Int = 30) = run(kpiKey) { api.kpiSummary(it, days) }

    suspend fun refreshPaymentsStatus() = run(paymentsStatusKey) { api.paymentsStatus(it) }

    suspend fun refreshOrderPayments(orderId: String) = run(orderPaymentsKey(orderId)) { api.orderPayments(it, orderId) }

    suspend fun createPaymentRequest(body: JsonObject, clientUuid: String) = run(null) { api.createPaymentRequest(it, body, clientUuid) }

    suspend fun sendPaymentRequest(requestId: String, to: String?) = run(null) { api.sendPaymentRequest(it, requestId, to) }

    /** Refreshes the order's payments in [accountId]'s store (after a gateway action in that account). */
    suspend fun refreshOrderPayments(accountId: String, orderId: String) =
        runAs(accountId, orderPaymentsKey(orderId)) { api.orderPayments(it, orderId) }

    // G6 gateway actions: the gateway is off by default; these are only offered while the server
    // reports online_payments_enabled. [key] stays stable across uncertain retries.

    suspend fun chargeSavedCard(accountId: String, orderId: String, body: JsonObject, key: String) =
        runFor(accountId) { api.chargeSavedCard(it, orderId, body, key) }

    suspend fun captureAuthorization(accountId: String, entryId: String, body: JsonObject, key: String) =
        runFor(accountId) { api.captureAuthorization(it, entryId, body, key) }

    suspend fun releaseAuthorization(accountId: String, entryId: String, key: String) =
        runFor(accountId) { api.releaseAuthorization(it, entryId, key) }

    suspend fun refundGatewayPayment(accountId: String, entryId: String, body: JsonObject, key: String) =
        runFor(accountId) { api.refundGatewayPayment(it, entryId, body, key) }

    suspend fun refreshBuyout(contractId: String) = run(buyoutKey(contractId)) { api.buyoutQuote(it, contractId) }

    suspend fun refreshCreditCheck(orderId: String, lineIds: List<String>) =
        run(creditKey(orderId)) { api.creditCheck(it, orderId, "checkout", lineIds) }

    /** Downloads a PDF into the app cache (shared through the FileProvider). */
    suspend fun pdf(name: String, fetch: suspend (acc: String) -> ByteArray): Result<File> {
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

    // ── Avago Marketplace meet-up inbox (owner; display cache only, actions are online) ──

    fun meetupInboxKey(status: String) = "mkt:meetups:$status"

    /** First page of the owner meet-up inbox; the last good page stays readable offline. */
    suspend fun refreshMeetupInbox(status: String) = run(meetupInboxKey(status)) { api.meetups(it, status, null) }

    /** Drops cached inbox pages after a 403 (permission revoked) so stale requests are not shown. */
    suspend fun clearMeetupInbox(status: String) {
        val acc = acc() ?: return
        runCatching { dbFactory.get(acc).rentalDao().delete(RentalEntity.META, PREFIX + meetupInboxKey(status)) }
            .onFailure { Timber.d(it, "[RentalsOnline] inbox cache clear failed") }
    }

    companion object {
        const val PREFIX = "w2:"

        /** [Result.Refused.code] when a money action was not sent because the account changed. */
        const val ACCOUNT_CHANGED = "account_changed"
    }
}
