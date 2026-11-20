package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.AssetToolDetails
import com.avago.core.data.domain.AssetVehicleDetails
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.model.RecallList
import com.avago.feature.assets.model.ToolForm
import com.avago.feature.assets.model.VehicleForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Screen state for one server load. */
sealed interface ExtrasLoad<out T> {
    data object Loading : ExtrasLoad<Nothing>
    data class Ready<T>(val value: T) : ExtrasLoad<T>
    data class Failed(val error: ApiError) : ExtrasLoad<Nothing>
}

/** One-shot snackbar event. */
sealed interface ExtrasNotice {
    data object Saved : ExtrasNotice
    data class Failed(val error: ApiError) : ExtrasNotice

    /** A local edit could not be queued (store closed, invalid value, record gone). */
    data object NotSaved : ExtrasNotice
}

/**
 * Edits one store-backed asset subtype row: the form is filled once from the stored row, then saved
 * as a catalog command whose optimistic overlay updates [state] at once (a server rejection rolls
 * it back and is listed with the sync conflicts).
 */
abstract class SubtypeEditViewModel<D, F> : ViewModel() {
    abstract val state: StateFlow<D?>

    private val _form = MutableStateFlow<F?>(null)

    /** Null until the stored row has been read. */
    val form: StateFlow<F?> = _form.asStateFlow()

    /** Field key -> error code (`date`, `range`, `number`, `currency`, `length`). */
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _notices = MutableSharedFlow<ExtrasNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<ExtrasNotice> = _notices

    protected fun fill(read: suspend () -> F) {
        viewModelScope.launch { _form.value = read() }
    }

    protected abstract fun errorsOf(form: F): Map<String, String>

    protected abstract suspend fun persist(form: F)

    fun update(f: F) {
        _form.value = f
        if (_errors.value.isNotEmpty()) _errors.value = errorsOf(f)
    }

    fun submit() {
        val f = _form.value ?: return
        val errs = errorsOf(f)
        _errors.value = errs
        if (errs.isNotEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                persist(f)
                _notices.emit(ExtrasNotice.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "asset subtype edit rejected locally")
                _notices.emit(ExtrasNotice.NotSaved)
            } catch (e: IllegalStateException) {
                Timber.e(e, "asset subtype edit could not be queued")
                _notices.emit(ExtrasNotice.NotSaved)
            } finally {
                _busy.value = false
            }
        }
    }
}

/** Tool calibration / usage / replacement value / tracker (`maint.asset_tool_details.update`). */
@HiltViewModel
class ToolDetailsViewModel @Inject constructor(
    private val assets: AssetRepository,
    prefs: UserPreferencesRepository,
    savedState: SavedStateHandle,
) : SubtypeEditViewModel<AssetToolDetails, ToolForm>() {
    val assetId: String = checkNotNull(savedState["assetId"])

    override val state: StateFlow<AssetToolDetails?> = assets.observeToolDetails(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        fill { ToolForm.of(assets.observeToolDetails(assetId).first(), prefs.currencyFlow.first()) }
    }

    override fun errorsOf(form: ToolForm) = form.errors()

    override suspend fun persist(form: ToolForm) = assets.updateToolDetails(assetId, form.toDraft())
}

/** Vehicle specs / registration / insurance (`maint.asset_vehicle_details.update`). */
@HiltViewModel
class VehicleDetailsViewModel @Inject constructor(
    private val assets: AssetRepository,
    savedState: SavedStateHandle,
) : SubtypeEditViewModel<AssetVehicleDetails, VehicleForm>() {
    val assetId: String = checkNotNull(savedState["assetId"])

    override val state: StateFlow<AssetVehicleDetails?> = assets.observeVehicleDetails(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        fill { VehicleForm.of(assets.observeVehicleDetails(assetId).first()) }
    }

    override fun errorsOf(form: VehicleForm) = form.errors()

    override suspend fun persist(form: VehicleForm) = assets.updateVehicleDetails(assetId, form.toDraft())
}

/** NHTSA recalls: a server query with a daily cache. */
@HiltViewModel
class RecallsViewModel @Inject constructor(
    private val service: AssetExtrasService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val assetId: String = checkNotNull(savedState["assetId"])
    private val _state = MutableStateFlow<ExtrasLoad<RecallList>>(ExtrasLoad.Loading)
    val state: StateFlow<ExtrasLoad<RecallList>> = _state.asStateFlow()
    private val _notices = MutableSharedFlow<ExtrasNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<ExtrasNotice> = _notices

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { refresh(force = false) }

    /** Marks [campaign] remedied (with [note]) or reopens it; one action at a time. */
    fun setRemedied(campaign: String, remedied: Boolean, note: String? = null) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val r = service.setRemedied(assetId, campaign, remedied, note)) {
                    is ApiResult.Ok -> {
                        val previous = (_state.value as? ExtrasLoad.Ready)?.value
                        _state.value = ExtrasLoad.Ready(r.value.copy(fetchedAt = r.value.fetchedAt ?: previous?.fetchedAt))
                        _notices.emit(ExtrasNotice.Saved)
                    }
                    is ApiResult.Err -> _notices.emit(ExtrasNotice.Failed(r.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** [force] bypasses the server's daily NHTSA cache; a failed refresh keeps the shown list. */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            if (_state.value !is ExtrasLoad.Ready) _state.value = ExtrasLoad.Loading
            when (val r = service.recalls(assetId, force)) {
                is ApiResult.Ok -> _state.value = ExtrasLoad.Ready(r.value)
                is ApiResult.Err -> if (_state.value is ExtrasLoad.Ready) {
                    _notices.emit(ExtrasNotice.Failed(r.error))
                } else {
                    _state.value = ExtrasLoad.Failed(r.error)
                }
            }
        }
    }
}
