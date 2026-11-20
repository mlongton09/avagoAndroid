package com.avago.feature.assets.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.assets.data.AssetQrService
import com.avago.feature.assets.model.QrScan
import com.avago.feature.assets.model.QrToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetQrState(
    val loading: Boolean = true,
    val token: QrToken? = null,
    val error: ApiError? = null,
    val busy: Boolean = false,
)

data class QrScansState(
    val scans: List<QrScan> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: ApiError? = null,
)

/** An asset's public QR label (mint / revoke) and its scan history, all online (REST). */
@HiltViewModel
class AssetQrViewModel @Inject constructor(
    private val service: AssetQrService,
    workers: WorkerRepository,
    permissions: PermissionsManager,
) : ViewModel() {
    private var assetId: String? = null

    private val _state = MutableStateFlow(AssetQrState())
    val state: StateFlow<AssetQrState> = _state.asStateFlow()

    private val _scans = MutableStateFlow(QrScansState())
    val scans: StateFlow<QrScansState> = _scans.asStateFlow()

    private val _failures = MutableSharedFlow<ApiError>(extraBufferCapacity = 4)
    val failures: SharedFlow<ApiError> = _failures

    /** Minting replaces the printed label, so it needs `assets.edit` (the server also requires an admin). */
    val canManage: StateFlow<Boolean> = permissions.observeCan(Permissions.ASSETS_EDIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), permissions.can(Permissions.ASSETS_EDIT))

    val canViewScans: StateFlow<Boolean> = permissions.observeCan(Permissions.ASSET_QR_VIEW_SCAN_HISTORY)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            permissions.can(Permissions.ASSET_QR_VIEW_SCAN_HISTORY),
        )

    val namesByUserId: StateFlow<Map<String, String>> = workers.observeWorkers()
        .map { list -> list.mapNotNull { w -> w.userId?.let { it to w.displayName } }.toMap() }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    fun load(id: String) {
        if (assetId == id) return
        assetId = id
        _state.value = AssetQrState()
        _scans.value = QrScansState()
        refresh()
    }

    fun refresh() {
        val id = assetId ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.update { s ->
                when (val r = service.token(id)) {
                    is ApiResult.Ok -> s.copy(loading = false, token = r.value, error = null)
                    is ApiResult.Err -> s.copy(loading = false, error = r.error)
                }
            }
        }
    }

    /** Mints a new label; any printed label for this asset stops resolving. */
    fun mint() = act { id ->
        when (val r = service.mint(id)) {
            is ApiResult.Ok -> _state.update { it.copy(token = r.value, error = null) }
            is ApiResult.Err -> _failures.emit(r.error)
        }
    }

    fun revoke() = act { id ->
        when (val r = service.revoke(id)) {
            is ApiResult.Ok -> _state.update { it.copy(token = null, error = null) }
            is ApiResult.Err -> _failures.emit(r.error)
        }
    }

    /** Loads the first page ([reset]) or the next one. */
    fun loadScans(reset: Boolean = false) {
        val id = assetId ?: return
        val current = if (reset) QrScansState() else _scans.value
        if (current.loading || !current.hasMore) return
        _scans.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            _scans.update { s ->
                when (val r = service.scans(id, current.page + 1)) {
                    is ApiResult.Ok -> s.copy(
                        scans = s.scans + r.value.scans,
                        page = r.value.page,
                        hasMore = r.value.hasMore,
                        loading = false,
                    )
                    is ApiResult.Err -> s.copy(loading = false, error = r.error)
                }
            }
        }
    }

    private fun act(block: suspend (String) -> Unit) {
        val id = assetId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block(id)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
