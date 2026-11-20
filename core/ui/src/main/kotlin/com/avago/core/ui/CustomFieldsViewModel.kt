package com.avago.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldEntry
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.StoreNotReadyException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The custom field values of one asset, product or work order (see [CustomFieldsCard]). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomFieldsViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {

    private val record = MutableStateFlow<Pair<CustomFieldEntity, String>?>(null)

    val entries: StateFlow<List<CustomFieldEntry>> = record.filterNotNull()
        .flatMapLatest { (entity, id) -> admin.observeCustomFields(entity, id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The command accepts any of the per-entity edit permissions the catalog lists. */
    val canEdit: StateFlow<Boolean> = record.flatMapLatest { current ->
        current?.let { admin.observeAllowed(editPermission(it.first)) } ?: flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<Int?>(null)

    /** A string resource describing the last failed change, until [errorShown]. */
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun bind(entity: CustomFieldEntity, entityId: String) {
        record.value = entity to entityId
    }

    fun errorShown() {
        _error.value = null
    }

    fun set(def: CustomFieldDef, value: String?) {
        val (_, entityId) = record.value ?: return
        viewModelScope.launch {
            try {
                admin.setCustomFieldValue(def, entityId, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StoreNotReadyException) {
                Timber.w(e, "custom field change before the store opened")
                _error.value = R.string.custom_fields_not_ready
            } catch (e: RecordNotFoundException) {
                Timber.w(e, "custom field change on a missing record")
                _error.value = R.string.custom_fields_gone
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "custom field value rejected locally")
                _error.value = R.string.custom_fields_invalid
            } catch (e: IllegalStateException) {
                Timber.e(e, "custom field change could not be queued")
                _error.value = R.string.custom_fields_save_failed
            }
        }
    }

    private companion object {
        fun editPermission(entity: CustomFieldEntity): String = when (entity) {
            CustomFieldEntity.ASSET -> "assets.edit"
            CustomFieldEntity.PRODUCT -> "inventory.edit_part"
            CustomFieldEntity.WORK_ORDER -> "work_orders.edit_own"
        }
    }
}
