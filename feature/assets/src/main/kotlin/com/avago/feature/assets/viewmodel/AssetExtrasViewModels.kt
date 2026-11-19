package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.AssetToolDetails
import com.avago.core.data.domain.AssetVehicleDetails
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.model.RecallList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    data class Failed(val error: ApiError) : ExtrasNotice
}

/** Tool calibration / usage / tracker, read from the v2 store. */
@HiltViewModel
class ToolDetailsViewModel @Inject constructor(
    assets: AssetRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val assetId: String = checkNotNull(savedState["assetId"])

    /** Null until the first read; [ToolDetailsState.details] is null when the asset has none. */
    val state: StateFlow<ToolDetailsState?> = assets.observeToolDetails(assetId)
        .map { ToolDetailsState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class ToolDetailsState(val details: AssetToolDetails?)

/** Vehicle registration / insurance, read from the v2 store. */
@HiltViewModel
class VehicleDetailsViewModel @Inject constructor(
    assets: AssetRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val assetId: String = checkNotNull(savedState["assetId"])

    /** Null until the first read; [VehicleDetailsState.details] is null when the asset has none. */
    val state: StateFlow<VehicleDetailsState?> = assets.observeVehicleDetails(assetId)
        .map { VehicleDetailsState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class VehicleDetailsState(val details: AssetVehicleDetails?)

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

    init { refresh(force = false) }

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
