package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.model.RecallList
import com.avago.feature.assets.model.ToolDetails
import com.avago.feature.assets.model.ToolForm
import com.avago.feature.assets.model.VehicleDetails
import com.avago.feature.assets.model.VehicleForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
}

/** Shared load / save plumbing for the scope detail screens. */
abstract class ExtrasViewModel<T> : ViewModel() {
    protected val _state = MutableStateFlow<ExtrasLoad<T>>(ExtrasLoad.Loading)
    val state: StateFlow<ExtrasLoad<T>> = _state.asStateFlow()
    protected val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    protected val _notices = MutableSharedFlow<ExtrasNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<ExtrasNotice> = _notices

    protected fun load(call: suspend () -> ApiResult<T>, onOk: (T) -> Unit = {}) {
        viewModelScope.launch {
            if (_state.value !is ExtrasLoad.Ready) _state.value = ExtrasLoad.Loading
            _state.value = when (val r = call()) {
                is ApiResult.Ok -> ExtrasLoad.Ready(r.value).also { onOk(r.value) }
                is ApiResult.Err -> if (_state.value is ExtrasLoad.Ready) {
                    _notices.emit(ExtrasNotice.Failed(r.error))
                    _state.value
                } else {
                    ExtrasLoad.Failed(r.error)
                }
            }
        }
    }

    /** Runs one mutation at a time; [onErr] returns true when it handled the error itself. */
    protected fun save(call: suspend () -> ApiResult<T>, onErr: (ApiError) -> Boolean = { false }, onOk: (T) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val r = call()) {
                    is ApiResult.Ok -> {
                        _state.value = ExtrasLoad.Ready(r.value)
                        onOk(r.value)
                        _notices.emit(ExtrasNotice.Saved)
                    }
                    is ApiResult.Err -> if (!onErr(r.error)) _notices.emit(ExtrasNotice.Failed(r.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }
}

@HiltViewModel
class ToolDetailsViewModel @Inject constructor(
    private val service: AssetExtrasService,
    savedState: SavedStateHandle,
) : ExtrasViewModel<ToolDetails>() {
    val assetId: String = checkNotNull(savedState["assetId"])
    private val _form = MutableStateFlow(ToolForm())
    val form: StateFlow<ToolForm> = _form.asStateFlow()

    /** Field key → error code (`date`, `range`, `number`, `currency`, `length`, `in_use`, `required`). */
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    init { refresh() }

    fun refresh() = load({ service.toolDetails(assetId) }) { _form.value = ToolForm.of(it) }

    fun update(f: ToolForm) {
        _form.value = f
        if (_errors.value.isNotEmpty()) _errors.value = f.errors()
    }

    fun submit() {
        val f = _form.value
        val errs = f.errors()
        _errors.value = errs
        if (errs.isNotEmpty()) return
        save(
            { service.saveToolDetails(assetId, f.toBody()) },
            onErr = { e ->
                when (e.code) {
                    "tracker_tag_in_use" -> { _errors.value = mapOf("tracker_tag_id" to "in_use"); true }
                    else -> false
                }
            },
        ) { _form.value = ToolForm.of(it) }
    }
}

@HiltViewModel
class VehicleDetailsViewModel @Inject constructor(
    private val service: AssetExtrasService,
    savedState: SavedStateHandle,
) : ExtrasViewModel<VehicleDetails>() {
    val assetId: String = checkNotNull(savedState["assetId"])
    private val _form = MutableStateFlow(VehicleForm())
    val form: StateFlow<VehicleForm> = _form.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    init { refresh() }

    fun refresh() = load({ service.vehicleDetails(assetId) }) { _form.value = VehicleForm.of(it) }

    fun update(f: VehicleForm) {
        _form.value = f
        if (_errors.value.isNotEmpty()) _errors.value = f.errors()
    }

    fun submit() {
        val f = _form.value
        val errs = f.errors()
        _errors.value = errs
        if (errs.isNotEmpty()) return
        val sendOdometer = (_state.value as? ExtrasLoad.Ready)?.value?.odometerSupported == true
        save({ service.saveVehicleDetails(assetId, f.toBody(sendOdometer)) }) { _form.value = VehicleForm.of(it) }
    }
}

@HiltViewModel
class RecallsViewModel @Inject constructor(
    private val service: AssetExtrasService,
    savedState: SavedStateHandle,
) : ExtrasViewModel<RecallList>() {
    val assetId: String = checkNotNull(savedState["assetId"])

    init { refresh(force = false) }

    /** [force] bypasses the server's daily NHTSA cache. */
    fun refresh(force: Boolean = true) = load({ service.recalls(assetId, force) })

    fun setRemedied(campaign: String, remedied: Boolean, note: String? = null) =
        save({ service.setRemedied(assetId, campaign, remedied, note) })
}
