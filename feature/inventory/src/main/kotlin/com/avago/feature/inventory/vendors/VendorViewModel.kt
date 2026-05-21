package com.avago.feature.inventory.vendors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.dao.VendorDao
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.entity.VendorEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

// ---------------------------------------------------------------------------
// List state
// ---------------------------------------------------------------------------

data class VendorListUiState(
    val vendors: List<VendorEntity> = emptyList(),
    val filtered: List<VendorEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class VendorListViewModel @Inject constructor(
    private val vendorDao: VendorDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VendorListUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(VendorListUiState(isLoading = false))
        else combine(vendorDao.observeAll(accountId), searchQuery) { vendors, query ->
            val filtered = if (query.isBlank()) vendors
            else vendors.filter { it.name.contains(query, ignoreCase = true) }
            VendorListUiState(vendors = vendors, filtered = filtered, searchQuery = query, isLoading = false)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VendorListUiState(),
    )

    fun setSearchQuery(q: String) { searchQuery.value = q }
}

// ---------------------------------------------------------------------------
// Detail state
// ---------------------------------------------------------------------------

data class VendorDetailUiState(
    val vendor: VendorEntity? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class VendorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vendorDao: VendorDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val vendorId: String = checkNotNull(savedStateHandle["vendorId"])

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VendorDetailUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(VendorDetailUiState(isLoading = false))
        else vendorDao.observeAll(accountId).flatMapLatest { vendors ->
            flowOf(VendorDetailUiState(vendor = vendors.find { it.vendorId == vendorId }, isLoading = false))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VendorDetailUiState(),
    )
}

// ---------------------------------------------------------------------------
// Add / Edit state
// ---------------------------------------------------------------------------

data class AddEditVendorUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val paymentTerms: String = "",
    val taxId: String = "",
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class AddEditVendorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vendorDao: VendorDao,
    private val syncQueueDao: SyncQueueDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val vendorId: String? = savedStateHandle["vendorId"]

    private val _state = MutableStateFlow(AddEditVendorUiState())
    val state: StateFlow<AddEditVendorUiState> = _state.asStateFlow()

    init {
        vendorId?.let { loadVendor(it) }
    }

    private fun loadVendor(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val v = vendorDao.getById(id)
            if (v != null) {
                _state.value = _state.value.copy(
                    name = v.name,
                    email = v.email ?: "",
                    phone = v.phone ?: "",
                    address = v.address ?: "",
                    paymentTerms = v.paymentTerms ?: "",
                    taxId = v.taxId ?: "",
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v, nameError = null) }
    fun setEmail(v: String) { _state.value = _state.value.copy(email = v) }
    fun setPhone(v: String) { _state.value = _state.value.copy(phone = v) }
    fun setAddress(v: String) { _state.value = _state.value.copy(address = v) }
    fun setPaymentTerms(v: String) { _state.value = _state.value.copy(paymentTerms = v) }
    fun setTaxId(v: String) { _state.value = _state.value.copy(taxId = v) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(nameError = "Vendor name is required")
            return
        }
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val id = vendorId ?: UUID.randomUUID().toString()
                val entity = VendorEntity(
                    vendorId = id,
                    accountId = accountId,
                    name = s.name.trim(),
                    email = s.email.takeIf { it.isNotBlank() },
                    phone = s.phone.takeIf { it.isNotBlank() },
                    address = s.address.takeIf { it.isNotBlank() },
                    paymentTerms = s.paymentTerms.takeIf { it.isNotBlank() },
                    taxId = s.taxId.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0L,
                    seq = null,
                )
                vendorDao.upsert(entity)
                syncQueueDao.enqueueOrReplace(
                    SyncQueueEntity(
                        queueId = UUID.randomUUID().toString(),
                        entityType = "vendor",
                        entityId = id,
                        operation = if (vendorId == null) "create" else "update",
                        serverVersion = 0L,
                        payload = null,
                        syncStatus = "pending",
                        attempts = 0L,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _state.value = _state.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                Timber.e(e, "AddEditVendorViewModel: save failed")
                _state.value = _state.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
