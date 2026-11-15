package com.avago.feature.provider.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Field-mechanic side of the provider workspace (feature C). */
interface ProviderService {
    suspend fun profile(): ApiResult<Profile>
    suspend fun dashboard(): ApiResult<Dashboard>

    suspend fun customers(query: String?): ApiResult<List<Customer>>
    suspend fun customer(id: String): ApiResult<CustomerDetail>
    suspend fun addCustomer(form: CustomerForm): ApiResult<Customer>
    suspend fun addAsset(customerId: String, form: AssetForm): ApiResult<CustomerAsset>

    suspend fun jobs(status: String?): ApiResult<List<Job>>
    suspend fun job(id: String): ApiResult<JobDetail>
    suspend fun saveJobNotes(id: String, notes: String): ApiResult<Job>
    suspend fun setJobStatus(id: String, form: StatusForm): ApiResult<Job>
    suspend fun uploadJobPhoto(id: String, jpeg: ByteArray): ApiResult<String>
    suspend fun sendJob(id: String): ApiResult<SendResult>
    suspend fun invoiceJob(id: String): ApiResult<Invoice>

    suspend fun quotes(status: String?): ApiResult<List<Quote>>
    suspend fun quote(id: String): ApiResult<QuoteDetail>
    suspend fun sendQuote(id: String): ApiResult<SendResult>

    suspend fun invoices(status: String?): ApiResult<List<Invoice>>
    suspend fun invoice(id: String): ApiResult<InvoiceDetail>
    suspend fun sendInvoice(id: String): ApiResult<SendResult>
    suspend fun markPaid(id: String, reference: String?): ApiResult<Invoice>
}

@Singleton
class KtorProviderService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : ProviderService {
    private fun acc(): String? = identity.getActiveAccountId()
    private fun p(a: String, path: String) = "/accounts/$a/provider$path"

    /** Profile currency per account; job rows carry no currency of their own. */
    private val currencies = java.util.concurrent.ConcurrentHashMap<String, String>()

    private inline fun <T> one(r: ApiResult<JsonObject>, key: String, parse: (JsonObject) -> T?): ApiResult<T> = when (r) {
        is ApiResult.Err -> r
        is ApiResult.Ok -> r.value.obj(key)?.let(parse)?.let { ApiResult.Ok(it) } ?: parseError(key)
    }

    private inline fun <T> whole(r: ApiResult<JsonObject>, what: String, parse: (JsonObject) -> T?): ApiResult<T> = when (r) {
        is ApiResult.Err -> r
        is ApiResult.Ok -> parse(r.value)?.let { ApiResult.Ok(it) } ?: parseError(what)
    }

    override suspend fun profile(): ApiResult<Profile> {
        val a = acc() ?: return noAccount()
        return api.get(p(a, "/profile")).map(Profile::from).also { r ->
            (r as? ApiResult.Ok)?.let { currencies[a] = it.value.currency }
        }
    }

    override suspend fun dashboard(): ApiResult<Dashboard> {
        val a = acc() ?: return noAccount()
        return api.get(p(a, "/dashboard")).map(Dashboard::from).also { r ->
            (r as? ApiResult.Ok)?.let { currencies[a] = it.value.currency }
        }
    }

    override suspend fun customers(query: String?): ApiResult<List<Customer>> {
        val a = acc() ?: return noAccount()
        return api.get(p(a, "/customers"), mapOf("q" to query?.trim()?.ifBlank { null }, "limit" to 200))
            .map { o -> o.objs("customers").mapNotNull(Customer::from) }
    }

    override suspend fun customer(id: String): ApiResult<CustomerDetail> {
        val a = acc() ?: return noAccount()
        return whole(api.get(p(a, "/customers/$id")), "customer", CustomerDetail::from)
    }

    override suspend fun addCustomer(form: CustomerForm): ApiResult<Customer> {
        val a = acc() ?: return noAccount()
        return one(api.post(p(a, "/customers"), form.toBody()), "customer", Customer::from)
    }

    override suspend fun addAsset(customerId: String, form: AssetForm): ApiResult<CustomerAsset> {
        val a = acc() ?: return noAccount()
        return one(api.post(p(a, "/customers/$customerId/assets"), form.toBody()), "asset", CustomerAsset::from)
    }

    override suspend fun jobs(status: String?): ApiResult<List<Job>> {
        val a = acc() ?: return noAccount()
        return api.get(p(a, "/jobs"), mapOf("status" to status, "limit" to 200)).map { o -> o.objs("jobs").mapNotNull(Job::from) }
    }

    override suspend fun job(id: String): ApiResult<JobDetail> {
        val a = acc() ?: return noAccount()
        val currency = currencies[a] ?: profile().valueOrNull()?.currency ?: DEFAULT_CURRENCY
        return whole(api.get(p(a, "/jobs/$id")), "job") { JobDetail.from(it, currency) }
    }

    override suspend fun saveJobNotes(id: String, notes: String): ApiResult<Job> {
        val a = acc() ?: return noAccount()
        return one(api.put(p(a, "/jobs/$id"), jsonBody("notes" to notes.trim())), "job", Job::from)
    }

    override suspend fun setJobStatus(id: String, form: StatusForm): ApiResult<Job> {
        val a = acc() ?: return noAccount()
        return one(api.post(p(a, "/jobs/$id/status"), form.toBody()), "job", Job::from)
    }

    override suspend fun uploadJobPhoto(id: String, jpeg: ByteArray): ApiResult<String> {
        val a = acc() ?: return noAccount()
        val reserve = api.post("/accounts/$a/photos", jsonBody("entity_id" to id, "entity_type" to "provider_job"))
        val slot = when (reserve) {
            is ApiResult.Err -> return reserve
            is ApiResult.Ok -> reserve.value
        }
        val photoId = photoIdFrom(slot) ?: return parseError("photo")
        val url = (slot["upload_url"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return ApiResult.Ok(photoId)
        return when (val put = api.upload(url, jpeg)) {
            is ApiResult.Ok -> ApiResult.Ok(photoId)
            is ApiResult.Err -> put
        }
    }

    override suspend fun sendJob(id: String): ApiResult<SendResult> {
        val a = acc() ?: return noAccount()
        return api.post(p(a, "/jobs/$id/send")).map(SendResult::from)
    }

    override suspend fun invoiceJob(id: String): ApiResult<Invoice> {
        val a = acc() ?: return noAccount()
        return one(api.post(p(a, "/jobs/$id/invoice")), "invoice", Invoice::from)
    }

    override suspend fun quotes(status: String?): ApiResult<List<Quote>> {
        val a = acc() ?: return noAccount()
        return api.get(p(a, "/quotes"), mapOf("status" to status, "limit" to 200)).map { o -> o.objs("quotes").mapNotNull(Quote::from) }
    }

    override suspend fun quote(id: String): ApiResult<QuoteDetail> {
        val a = acc() ?: return noAccount()
        return whole(api.get(p(a, "/quotes/$id")), "quote", QuoteDetail::from)
    }

    override suspend fun sendQuote(id: String): ApiResult<SendResult> {
        val a = acc() ?: return noAccount()
        return api.post(p(a, "/quotes/$id/send")).map(SendResult::from)
    }

    override suspend fun invoices(status: String?): ApiResult<List<Invoice>> {
        val a = acc() ?: return noAccount()
        return api.get(p(a, "/invoices"), mapOf("status" to status, "limit" to 200)).map { o -> o.objs("invoices").mapNotNull(Invoice::from) }
    }

    override suspend fun invoice(id: String): ApiResult<InvoiceDetail> {
        val a = acc() ?: return noAccount()
        return whole(api.get(p(a, "/invoices/$id")), "invoice", InvoiceDetail::from)
    }

    override suspend fun sendInvoice(id: String): ApiResult<SendResult> {
        val a = acc() ?: return noAccount()
        return api.post(p(a, "/invoices/$id/send")).map(SendResult::from)
    }

    override suspend fun markPaid(id: String, reference: String?): ApiResult<Invoice> {
        val a = acc() ?: return noAccount()
        return one(api.post(p(a, "/invoices/$id/mark-paid"), jsonBody("reference" to reference?.trim()?.ifBlank { null })), "invoice", Invoice::from)
    }
}
