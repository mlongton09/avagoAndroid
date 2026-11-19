package com.avago.feature.inventory.vendors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.ProductSupplier
import com.avago.core.data.domain.Supplier
import com.avago.core.data.domain.SupplierDraft
import com.avago.core.data.domain.SupplierRepository
import com.avago.core.data.domain.toDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// List state
// ---------------------------------------------------------------------------

data class VendorListUiState(
    val vendors: List<Supplier> = emptyList(),
    val filtered: List<Supplier> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class VendorListViewModel @Inject constructor(
    suppliers: SupplierRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<VendorListUiState> =
        combine(suppliers.observeSuppliers(includeArchived = true), searchQuery) { vendors, query ->
            val filtered = if (query.isBlank()) {
                vendors
            } else {
                vendors.filter { it.name.contains(query, ignoreCase = true) || it.supplierCode?.contains(query, true) == true }
            }
            VendorListUiState(vendors = vendors, filtered = filtered, searchQuery = query, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VendorListUiState())

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }
}

// ---------------------------------------------------------------------------
// Detail state
// ---------------------------------------------------------------------------

/** A vendor catalog entry with the name of the product it supplies. */
data class VendorPartWithPart(
    val vendorPart: ProductSupplier,
    val productName: String?,
)

data class VendorDetailUiState(
    val vendor: Supplier? = null,
    val vendorParts: List<VendorPartWithPart> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class VendorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val suppliers: SupplierRepository,
    inventory: InventoryRepository,
) : ViewModel() {

    private val vendorId: String = checkNotNull(savedStateHandle["vendorId"])
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<VendorDetailUiState> = combine(
        suppliers.observeSupplier(vendorId),
        suppliers.observeProductSuppliers(supplierPartyId = vendorId),
        inventory.observeProductStock(includeArchived = true),
        error,
    ) { vendor, catalog, products, error ->
        val names = products.associate { it.product.id to it.product.name }
        VendorDetailUiState(
            vendor = vendor,
            vendorParts = catalog
                .map { VendorPartWithPart(it, names[it.productId]) }
                .sortedBy { it.productName ?: it.vendorPart.supplierSku.orEmpty() },
            isLoading = false,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VendorDetailUiState())

    fun setActive(active: Boolean) {
        viewModelScope.launch {
            runCatching { suppliers.setSupplierActive(vendorId, active) }
                .onFailure { e ->
                    Timber.e(e, "VendorDetailViewModel: setActive failed")
                    error.value = e.message
                }
        }
    }

    fun dismissError() {
        error.value = null
    }
}

// ---------------------------------------------------------------------------
// Add / Edit state
// ---------------------------------------------------------------------------

data class AddEditVendorUiState(
    val name: String = "",
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
    val address: String = "",
    val paymentTerms: String = "",
    val accountNumber: String = "",
    /** Write-only on mobile: the stored value is not synced to devices, so the field starts empty. */
    val taxId: String = "",
    val notes: String = "",
    val isPreferred: Boolean = false,
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class AddEditVendorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val suppliers: SupplierRepository,
) : ViewModel() {

    private val vendorId: String? = savedStateHandle["vendorId"]
    private var stored: Supplier? = null

    private val _state = MutableStateFlow(AddEditVendorUiState())
    val state: StateFlow<AddEditVendorUiState> = _state.asStateFlow()

    init {
        vendorId?.let { loadVendor(it) }
    }

    private fun loadVendor(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val v = suppliers.supplier(id)
            stored = v
            _state.update { s ->
                if (v == null) {
                    s.copy(isLoading = false)
                } else {
                    s.copy(
                        name = v.name,
                        contactName = v.contactName.orEmpty(),
                        email = v.email.orEmpty(),
                        phone = v.phone.orEmpty(),
                        website = v.website.orEmpty(),
                        address = v.address.orEmpty(),
                        paymentTerms = v.paymentTerms.orEmpty(),
                        accountNumber = v.accountNumber.orEmpty(),
                        notes = v.notes.orEmpty(),
                        isPreferred = v.isPreferred,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun setContactName(v: String) = _state.update { it.copy(contactName = v) }
    fun setEmail(v: String) = _state.update { it.copy(email = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v) }
    fun setWebsite(v: String) = _state.update { it.copy(website = v) }
    fun setAddress(v: String) = _state.update { it.copy(address = v) }
    fun setPaymentTerms(v: String) = _state.update { it.copy(paymentTerms = v) }
    fun setAccountNumber(v: String) = _state.update { it.copy(accountNumber = v) }
    fun setTaxId(v: String) = _state.update { it.copy(taxId = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setPreferred(v: Boolean) = _state.update { it.copy(isPreferred = v) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(nameError = "Vendor name is required")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                // Start from the stored vendor so fields this form does not show are kept.
                val base = stored?.toDraft() ?: SupplierDraft(name = s.name)
                val draft = base.copy(
                    name = s.name,
                    contactName = s.contactName,
                    email = s.email,
                    phone = s.phone,
                    website = s.website,
                    address = s.address,
                    paymentTerms = s.paymentTerms,
                    accountNumber = s.accountNumber,
                    taxId = s.taxId,
                    notes = s.notes,
                    isPreferred = s.isPreferred,
                )
                if (vendorId == null) suppliers.createSupplier(draft) else suppliers.updateSupplier(vendorId, draft)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                Timber.e(e, "AddEditVendorViewModel: save failed")
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
