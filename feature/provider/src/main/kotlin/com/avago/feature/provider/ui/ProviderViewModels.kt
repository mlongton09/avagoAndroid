package com.avago.feature.provider.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiResult
import com.avago.feature.provider.R
import com.avago.feature.provider.data.AssetForm
import com.avago.feature.provider.data.Customer
import com.avago.feature.provider.data.CustomerDetail
import com.avago.feature.provider.data.CustomerForm
import com.avago.feature.provider.data.Dashboard
import com.avago.feature.provider.data.Invoice
import com.avago.feature.provider.data.InvoiceDetail
import com.avago.feature.provider.data.Job
import com.avago.feature.provider.data.JobDetail
import com.avago.feature.provider.data.Load
import com.avago.feature.provider.data.Notice
import com.avago.feature.provider.data.ProviderService
import com.avago.feature.provider.data.Quote
import com.avago.feature.provider.data.QuoteDetail
import com.avago.feature.provider.data.SendResult
import com.avago.feature.provider.data.StatusForm
import com.avago.feature.provider.data.toLoad
import com.avago.feature.provider.data.valueOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job as CoJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot navigation or share requests from a view-model. */
sealed interface ProviderEvent {
    /** Share the customer link; [emailed] says the server also e-mailed it. */
    data class Share(val url: String, val emailed: Boolean) : ProviderEvent
    data class OpenCustomer(val id: String) : ProviderEvent
    data class OpenInvoice(val id: String) : ProviderEvent
}

/** [NoticeViewModel] with an event stream and a detail/list load. */
abstract class LoadViewModel<T> : NoticeViewModel() {
    protected val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()
    protected val _events = MutableSharedFlow<ProviderEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ProviderEvent> = _events
    private var loading: CoJob? = null

    protected abstract suspend fun load(): ApiResult<T>

    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch {
            if (_state.value !is Load.Ready) _state.value = Load.Loading
            _state.value = load().toLoad()
        }
    }

    protected fun shareResult(doneRes: Int, call: suspend () -> ApiResult<SendResult>) = act(doneRes, call) { r ->
        r.linkUrl?.let { _events.emit(ProviderEvent.Share(it, r.emailed)) }
        refresh()
    }
}

/** List with an optional server-side status filter (null = all). */
abstract class FilteredListViewModel<T> : LoadViewModel<List<T>>() {
    private val _filter = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    protected abstract suspend fun list(status: String?): ApiResult<List<T>>
    override suspend fun load() = list(_filter.value)

    fun setFilter(status: String?) {
        if (_filter.value == status) return
        _filter.value = status
        _state.value = Load.Loading
        refresh()
    }
}

@HiltViewModel
class ProviderDashboardViewModel @Inject constructor(private val service: ProviderService) : LoadViewModel<Dashboard>() {
    private val _businessName = MutableStateFlow<String?>(null)
    val businessName: StateFlow<String?> = _businessName.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { _businessName.value = service.profile().valueOrNull()?.businessName?.ifBlank { null } }
    }

    override suspend fun load() = service.dashboard()
}

@HiltViewModel
class CustomersViewModel @Inject constructor(private val service: ProviderService) : LoadViewModel<List<Customer>>() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init { refresh() }

    override suspend fun load() = service.customers(_query.value)

    fun setQuery(q: String) { _query.value = q }

    fun search() {
        _state.value = Load.Loading
        refresh()
    }

    /** False (and nothing sent) when the form is invalid. */
    fun add(form: CustomerForm): Boolean {
        if (form.errors().isNotEmpty()) return false
        act(R.string.provider_customer_added, { service.addCustomer(form) }) { c ->
            refresh()
            _events.emit(ProviderEvent.OpenCustomer(c.id))
        }
        return true
    }
}

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val service: ProviderService,
    savedState: SavedStateHandle,
) : LoadViewModel<CustomerDetail>() {
    val customerId: String = checkNotNull(savedState["customerId"])

    init { refresh() }

    override suspend fun load() = service.customer(customerId)

    fun addAsset(form: AssetForm): Boolean {
        if (form.errors().isNotEmpty()) return false
        act(R.string.provider_asset_added, { service.addAsset(customerId, form) }) { refresh() }
        return true
    }
}

@HiltViewModel
class JobsViewModel @Inject constructor(private val service: ProviderService) : FilteredListViewModel<Job>() {
    init { refresh() }
    override suspend fun list(status: String?) = service.jobs(status)
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val service: ProviderService,
    savedState: SavedStateHandle,
) : LoadViewModel<JobDetail>() {
    val jobId: String = checkNotNull(savedState["jobId"])
    private val _uploading = MutableStateFlow(0)

    /** Photos currently uploading. */
    val uploading: StateFlow<Int> = _uploading.asStateFlow()

    init { refresh() }

    override suspend fun load() = service.job(jobId)

    fun changeStatus(form: StatusForm): Boolean {
        val job = _state.value.valueOrNull()?.job ?: return false
        if (form.status !in job.nextStatuses || form.errors().isNotEmpty()) return false
        act(R.string.provider_status_saved, { service.setJobStatus(jobId, form) }) { refresh() }
        return true
    }

    fun saveNotes(notes: String) = act(R.string.provider_notes_saved, { service.saveJobNotes(jobId, notes) }) { refresh() }

    fun addPhoto(jpeg: ByteArray) {
        _uploading.value += 1
        viewModelScope.launch {
            try {
                when (val r = service.uploadJobPhoto(jobId, jpeg)) {
                    is ApiResult.Ok -> {
                        _notices.emit(Notice.Done(R.string.provider_photo_added))
                        refresh()
                    }
                    is ApiResult.Err -> _notices.emit(Notice.Failed(r.error))
                }
            } finally {
                _uploading.value -= 1
            }
        }
    }

    fun send() = shareResult(R.string.provider_job_sent) { service.sendJob(jobId) }

    fun createInvoice() {
        if (_state.value.valueOrNull()?.job?.canInvoice != true) return
        act(R.string.provider_invoice_created, { service.invoiceJob(jobId) }) { inv ->
            refresh()
            _events.emit(ProviderEvent.OpenInvoice(inv.id))
        }
    }
}

@HiltViewModel
class QuotesViewModel @Inject constructor(private val service: ProviderService) : FilteredListViewModel<Quote>() {
    init { refresh() }
    override suspend fun list(status: String?) = service.quotes(status)
}

@HiltViewModel
class QuoteDetailViewModel @Inject constructor(
    private val service: ProviderService,
    savedState: SavedStateHandle,
) : LoadViewModel<QuoteDetail>() {
    val quoteId: String = checkNotNull(savedState["quoteId"])

    init { refresh() }

    override suspend fun load() = service.quote(quoteId)

    fun send() {
        if (_state.value.valueOrNull()?.quote?.canSend != true) return
        shareResult(R.string.provider_quote_sent) { service.sendQuote(quoteId) }
    }
}

@HiltViewModel
class InvoicesViewModel @Inject constructor(private val service: ProviderService) : FilteredListViewModel<Invoice>() {
    init { refresh() }
    override suspend fun list(status: String?) = service.invoices(status)
}

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    private val service: ProviderService,
    savedState: SavedStateHandle,
) : LoadViewModel<InvoiceDetail>() {
    val invoiceId: String = checkNotNull(savedState["invoiceId"])

    init { refresh() }

    override suspend fun load() = service.invoice(invoiceId)

    fun send() {
        if (_state.value.valueOrNull()?.invoice?.canSend != true) return
        shareResult(R.string.provider_invoice_sent) { service.sendInvoice(invoiceId) }
    }

    fun markPaid(reference: String?) {
        if (_state.value.valueOrNull()?.invoice?.canMarkPaid != true) return
        act(R.string.provider_invoice_paid, { service.markPaid(invoiceId, reference) }) { refresh() }
    }
}
