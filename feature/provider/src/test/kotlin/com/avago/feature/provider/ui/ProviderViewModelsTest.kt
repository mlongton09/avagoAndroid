package com.avago.feature.provider.ui

import androidx.lifecycle.SavedStateHandle
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.provider.R
import com.avago.feature.provider.data.AssetForm
import com.avago.feature.provider.data.Customer
import com.avago.feature.provider.data.CustomerAsset
import com.avago.feature.provider.data.CustomerDetail
import com.avago.feature.provider.data.CustomerForm
import com.avago.feature.provider.data.Dashboard
import com.avago.feature.provider.data.Invoice
import com.avago.feature.provider.data.InvoiceDetail
import com.avago.feature.provider.data.Job
import com.avago.feature.provider.data.JobDetail
import com.avago.feature.provider.data.Load
import com.avago.feature.provider.data.Notice
import com.avago.feature.provider.data.Profile
import com.avago.feature.provider.data.ProviderService
import com.avago.feature.provider.data.Quote
import com.avago.feature.provider.data.QuoteDetail
import com.avago.feature.provider.data.SendResult
import com.avago.feature.provider.data.StatusForm
import com.avago.feature.provider.data.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Fake that fails every call unless a test overrides it. */
private open class FakeProvider : ProviderService {
    fun <T> nope(status: Int = 500): ApiResult<T> = ApiResult.Err(ApiError(status, "not_faked", "not faked"))
    override suspend fun profile(): ApiResult<Profile> = nope()
    override suspend fun dashboard(): ApiResult<Dashboard> = nope()
    override suspend fun customers(query: String?): ApiResult<List<Customer>> = nope()
    override suspend fun customer(id: String): ApiResult<CustomerDetail> = nope()
    override suspend fun addCustomer(form: CustomerForm): ApiResult<Customer> = nope()
    override suspend fun addAsset(customerId: String, form: AssetForm): ApiResult<CustomerAsset> = nope()
    override suspend fun jobs(status: String?): ApiResult<List<Job>> = nope()
    override suspend fun job(id: String): ApiResult<JobDetail> = nope()
    override suspend fun saveJobNotes(id: String, notes: String): ApiResult<Job> = nope()
    override suspend fun setJobStatus(id: String, form: StatusForm): ApiResult<Job> = nope()
    override suspend fun uploadJobPhoto(id: String, jpeg: ByteArray): ApiResult<String> = nope()
    override suspend fun sendJob(id: String): ApiResult<SendResult> = nope()
    override suspend fun invoiceJob(id: String): ApiResult<Invoice> = nope()
    override suspend fun quotes(status: String?): ApiResult<List<Quote>> = nope()
    override suspend fun quote(id: String): ApiResult<QuoteDetail> = nope()
    override suspend fun sendQuote(id: String): ApiResult<SendResult> = nope()
    override suspend fun invoices(status: String?): ApiResult<List<Invoice>> = nope()
    override suspend fun invoice(id: String): ApiResult<InvoiceDetail> = nope()
    override suspend fun sendInvoice(id: String): ApiResult<SendResult> = nope()
    override suspend fun markPaid(id: String, reference: String?): ApiResult<Invoice> = nope()
}

private fun mkJob(status: String, invoiceId: String? = null) =
    Job("j1", "J-1", status, null, null, null, null, null, null, null, null, null, emptyList(), invoiceId, "c1", null, "Ann")

private fun jobDetail(status: String, invoiceId: String? = null) = JobDetail(mkJob(status, invoiceId), null, null, emptyList(), null, "USD")

private fun mkInvoice(status: String, total: Long = 1000) =
    Invoice("i1", "INV-1", status, null, emptyList(), total, 0, total, "USD", null, null, null, null, null, null, "c1", "Ann")

private fun mkQuote(status: String) =
    Quote("q1", "Q-1", status, null, null, emptyList(), 0, 0, 0, "USD", null, null, null, null, "c1", "Ann")

private fun mkCustomer(id: String) = Customer(id, "Ann", null, null, null, false, 0, null)

private val totals = Totals(0, 0, 0, 0, 0)

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderViewModelsTest {
    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `dashboard surfaces not_a_provider as a failed load`() {
        val vm = ProviderDashboardViewModel(object : FakeProvider() {
            override suspend fun dashboard(): ApiResult<Dashboard> = ApiResult.Err(ApiError(404, "not_a_provider", "not_a_provider"))
            override suspend fun profile(): ApiResult<Profile> = ApiResult.Ok(Profile("Ace Repairs", "USD"))
        })
        val s = vm.state.value as Load.Failed
        assertEquals(R.string.provider_err_not_provider, errorRes(s.error))
        assertEquals("Ace Repairs", vm.businessName.value)
        assertEquals(R.string.provider_err_pro, errorRes(ApiError(402, "provider_pro_required", "")))
    }

    @Test
    fun `invalid customer form is not sent and a valid one opens the new customer`() = runTest {
        var adds = 0
        val vm = CustomersViewModel(object : FakeProvider() {
            override suspend fun customers(query: String?): ApiResult<List<Customer>> = ApiResult.Ok(emptyList())
            override suspend fun addCustomer(form: CustomerForm): ApiResult<Customer> {
                adds++
                return ApiResult.Ok(mkCustomer("c9"))
            }
        })
        val events = mutableListOf<ProviderEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        assertFalse(vm.add(CustomerForm(name = "")))
        assertEquals(0, adds)
        assertTrue(vm.add(CustomerForm(name = "Ann")))
        assertEquals(1, adds)
        assertEquals(listOf<ProviderEvent>(ProviderEvent.OpenCustomer("c9")), events)
        job.cancel()
    }

    @Test
    fun `customer search passes the query`() {
        val queries = mutableListOf<String?>()
        val vm = CustomersViewModel(object : FakeProvider() {
            override suspend fun customers(query: String?): ApiResult<List<Customer>> {
                queries += query
                return ApiResult.Ok(listOf(mkCustomer("c1")))
            }
        })
        vm.setQuery("ann")
        vm.search()
        assertEquals(listOf<String?>("", "ann"), queries)
        assertEquals(1, (vm.state.value as Load.Ready).value.size)
    }

    @Test
    fun `job status change is limited to allowed transitions`() {
        val sent = mutableListOf<StatusForm>()
        var status = "scheduled"
        val vm = JobDetailViewModel(
            object : FakeProvider() {
                override suspend fun job(id: String): ApiResult<JobDetail> = ApiResult.Ok(jobDetail(status))
                override suspend fun setJobStatus(id: String, form: StatusForm): ApiResult<Job> {
                    sent += form
                    status = form.status
                    return ApiResult.Ok(mkJob(status))
                }
            },
            SavedStateHandle(mapOf("jobId" to "j1")),
        )
        assertFalse(vm.changeStatus(StatusForm("on_hold")))
        assertFalse(vm.changeStatus(StatusForm("in_progress", labourHours = "x")))
        assertTrue(vm.changeStatus(StatusForm("in_progress")))
        assertEquals(listOf("in_progress"), sent.map { it.status })
        assertEquals("in_progress", vm.state.value.valueOrNull()!!.job.status)
    }

    @Test
    fun `invoice from job only for completed uninvoiced jobs and opens it`() = runTest {
        var calls = 0
        var status = "in_progress"
        val vm = JobDetailViewModel(
            object : FakeProvider() {
                override suspend fun job(id: String): ApiResult<JobDetail> = ApiResult.Ok(jobDetail(status))
                override suspend fun invoiceJob(id: String): ApiResult<Invoice> {
                    calls++
                    return ApiResult.Ok(mkInvoice("draft"))
                }
            },
            SavedStateHandle(mapOf("jobId" to "j1")),
        )
        val events = mutableListOf<ProviderEvent>()
        val c = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        vm.createInvoice()
        assertEquals(0, calls)
        status = "completed"
        vm.refresh()
        vm.createInvoice()
        assertEquals(1, calls)
        assertEquals(listOf<ProviderEvent>(ProviderEvent.OpenInvoice("i1")), events)
        c.cancel()
    }

    @Test
    fun `failed photo upload reports the error and clears the uploading count`() = runTest {
        val vm = JobDetailViewModel(
            object : FakeProvider() {
                override suspend fun job(id: String): ApiResult<JobDetail> = ApiResult.Ok(jobDetail("in_progress"))
                override suspend fun uploadJobPhoto(id: String, jpeg: ByteArray): ApiResult<String> = ApiResult.Err(ApiError(0, "offline", ""))
            },
            SavedStateHandle(mapOf("jobId" to "j1")),
        )
        val notices = mutableListOf<Notice>()
        val c = launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        vm.addPhoto(byteArrayOf(1, 2, 3))
        assertEquals(0, vm.uploading.value)
        assertTrue(notices.single() is Notice.Failed)
        c.cancel()
    }

    @Test
    fun `sending a quote shares the customer link`() = runTest {
        val vm = QuoteDetailViewModel(
            object : FakeProvider() {
                override suspend fun quote(id: String): ApiResult<QuoteDetail> = ApiResult.Ok(QuoteDetail(mkQuote("draft"), null, totals))
                override suspend fun sendQuote(id: String): ApiResult<SendResult> = ApiResult.Ok(SendResult("https://p/q1", true))
            },
            SavedStateHandle(mapOf("quoteId" to "q1")),
        )
        val events = mutableListOf<ProviderEvent>()
        val notices = mutableListOf<Notice>()
        val c1 = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        val c2 = launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        vm.send()
        assertEquals(listOf<ProviderEvent>(ProviderEvent.Share("https://p/q1", true)), events)
        assertEquals(listOf<Notice>(Notice.Done(R.string.provider_quote_sent)), notices)
        c1.cancel()
        c2.cancel()
    }

    @Test
    fun `mark paid sends the reference and is skipped once paid`() {
        val refs = mutableListOf<String?>()
        var status = "sent"
        val vm = InvoiceDetailViewModel(
            object : FakeProvider() {
                override suspend fun invoice(id: String): ApiResult<InvoiceDetail> = ApiResult.Ok(InvoiceDetail(mkInvoice(status), null, totals, false))
                override suspend fun markPaid(id: String, reference: String?): ApiResult<Invoice> {
                    refs += reference
                    status = "paid"
                    return ApiResult.Ok(mkInvoice("paid"))
                }
            },
            SavedStateHandle(mapOf("invoiceId" to "i1")),
        )
        vm.markPaid("CHQ-12")
        vm.markPaid("again")
        assertEquals(listOf<String?>("CHQ-12"), refs)
        assertEquals("paid", vm.state.value.valueOrNull()!!.invoice.status)
    }

    @Test
    fun `list filter reloads with the chosen status`() {
        val seen = mutableListOf<String?>()
        val vm = InvoicesViewModel(object : FakeProvider() {
            override suspend fun invoices(status: String?): ApiResult<List<Invoice>> {
                seen += status
                return ApiResult.Ok(emptyList())
            }
        })
        vm.setFilter("sent")
        vm.setFilter("sent")
        vm.setFilter(null)
        assertEquals(listOf(null, "sent", null), seen)
    }
}
