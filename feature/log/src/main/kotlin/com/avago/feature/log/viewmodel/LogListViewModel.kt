package com.avago.feature.log.viewmodel

import com.avago.core.data.domain.SyncRepository
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityFilter
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetMeter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.feature.log.model.LogAnalytics
import com.avago.feature.log.model.LogListFilter
import com.avago.feature.log.model.LogPacks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogListViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val assets: AssetRepository,
    private val sync: SyncRepository,
    userPrefsRepository: UserPreferencesRepository,
) : ViewModel() {

    val assetId = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Failed photo writes, for a snackbar. */
    val errors: SharedFlow<String> = _errors

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val distanceUnit: StateFlow<String> = userPrefsRepository.distanceUnitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "mi")

    /** Logs scoped to the asset (if any), newest first, before search / category filters. */
    private val scopedLogs: StateFlow<List<Activity>> = assetId
        .flatMapLatest { id -> activities.observeActivities(ActivityFilter(assetId = id)) }
        .map { list -> list.sortedByDescending { it.occurredAt } }
        .catch { e ->
            Timber.e(e, "[LogListViewModel] Failed to observe logs")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Money in v2 is stored in each record's currency; the list shows the dominant one. */
    val currencyCode: StateFlow<String> = combine(scopedLogs, userPrefsRepository.currencyFlow) { list, preferred ->
        list.mapNotNull { it.currency }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: preferred
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    val logs: StateFlow<List<Activity>> = combine(scopedLogs, _categoryFilter, _searchQuery) { list, filter, query ->
        LogListFilter.apply(list, filter, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True when the asset has entries but search / category hides all of them. */
    val hasAnyLogs: StateFlow<Boolean> = scopedLogs.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Distinct categories with entry counts over the unfiltered set, most used first. */
    val categoryCounts: StateFlow<List<Pair<String, Int>>> = scopedLogs.map { list ->
        LogListFilter.categoryCounts(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryFilter: StateFlow<String?> = _categoryFilter

    /** The current asset, or null when viewing all logs. */
    val asset: StateFlow<Asset?> = assetId
        .flatMapLatest { id -> if (id == null) flowOf(null) else assets.observeAsset(id) }
        .catch { e ->
            Timber.e(e, "[LogListViewModel] Error observing asset")
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The asset's default meter (else its first), for units on rows and analytics. */
    val meter: StateFlow<AssetMeter?> = assetId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else assets.observeMeters(id) }
        .map { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pictures attached to the current asset. Empty when viewing all logs. */
    val photos: StateFlow<List<Attachment>> = assetId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else assets.observeAttachments(AttachmentTarget.ToAsset(id))
        }
        .map { list -> list.filter { it.file?.isImage == true } }
        .catch { e ->
            Timber.e(e, "[LogListViewModel] Error observing photos")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Most-recent log date for the current asset, or null. */
    val lastServiceDate: StateFlow<Long?> = logs
        .map { it.maxOfOrNull { entry -> entry.occurredAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Log count for the current asset (before search / category filters). */
    val entryCount: StateFlow<Int> = combine(assetId, scopedLogs) { id, list -> if (id == null) 0 else list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setAssetId(id: String?) {
        assetId.value = id
    }

    /**
     * Per-asset analytics. Only for a single asset with at least two entries; mixing assets would
     * make economy and cost-per-meter meaningless.
     */
    val analytics: StateFlow<LogAnalytics.Summary?> = combine(scopedLogs, meter, distanceUnit) { list, m, unit ->
        if (assetId.value == null || list.size < 2) return@combine null
        val entries = list.map { log ->
            LogAnalytics.Entry(
                id = log.id,
                dateMs = log.occurredAt,
                category = log.categoryLabel,
                meter = log.meterValue?.toDouble(),
                cost = log.totalCost.toDouble().takeIf { log.totalCost.signum() != 0 },
                attributes = LogPacks.parse(log.attributes),
            )
        }
        LogAnalytics.summarize(entries, LogAnalytics.distanceUnitFor(m?.unit, unit))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _categoryFilter.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                sync.syncNow()
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] Refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Attaches a gallery or camera picture to the asset; the file uploads through the E12 endpoints. */
    fun addAssetPhoto(uri: Uri) {
        val id = assetId.value ?: return
        viewModelScope.launch {
            try {
                assets.attach(AttachmentTarget.ToAsset(id), LocalFileRef(uri.toString()))
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] addAssetPhoto failed for $uri")
                _errors.tryEmit(e.message ?: "photo")
            }
        }
    }

    fun deleteAssetPhoto(attachmentId: String) {
        viewModelScope.launch {
            try {
                assets.detach(attachmentId)
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] deleteAssetPhoto failed for $attachmentId")
                _errors.tryEmit(e.message ?: "photo")
            }
        }
    }
}
