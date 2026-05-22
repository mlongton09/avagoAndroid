package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.repository.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

/**
 * Log entries grouped by year for the sticky-header list on AssetDetailScreen.
 */
data class LogsByYear(
    val year: Int,
    val entries: List<LogEntity>,
)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AssetRepository,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val assetId: String = checkNotNull(savedStateHandle["assetId"]) {
        "assetId is required in SavedStateHandle for AssetDetailViewModel"
    }

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = identityManager.getActiveAccountId(),
        )

    /**
     * The asset entity for this screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val asset: StateFlow<AssetEntity?> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(null)
            else {
                try {
                    repository.observeAssets(acctId).map { list ->
                        list.firstOrNull { it.assetId == assetId }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Error observing asset $assetId")
                    flowOf(null)
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Asset flow error")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * All log entries for this asset, reactive to the active account.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allLogs: StateFlow<List<LogEntity>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else {
                try {
                    repository.observeLogsForAsset(acctId, assetId)
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Error observing logs for $assetId")
                    flowOf(emptyList())
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Log flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Available category filter options derived from the loaded log entries.
     */
    val availableCategories: StateFlow<List<String>> = _allLogs
        .map { logs -> logs.mapNotNull { it.category }.distinct().sorted() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Filtered and year-grouped log entries for the detail screen.
     */
    val logsByYear: StateFlow<List<LogsByYear>> = combine(
        _allLogs,
        _categoryFilter,
    ) { logs, category ->
        val filtered = if (category == null) logs
        else logs.filter { it.category == category }

        // Sort descending by entryDate so newest entries appear first within each year
        val sorted = filtered.sortedByDescending { it.entryDate }

        // Group by calendar year of entryDate
        sorted
            .groupBy { entry ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = entry.entryDate
                cal.get(Calendar.YEAR)
            }
            .entries
            .sortedByDescending { it.key }
            .map { (year, entries) -> LogsByYear(year = year, entries = entries) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Total cost across all log entries for this asset.
     */
    val totalCost: StateFlow<Double> = _allLogs
        .map { logs -> logs.sumOf { it.cost ?: 0.0 } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0,
        )

    /**
     * Most recent log entryDate, or null if no entries exist.
     */
    val lastServiceDate: StateFlow<Long?> = _allLogs
        .map { logs -> logs.maxOfOrNull { it.entryDate } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Most recent odometer reading from logs, if the asset tracks meters.
     */
    val latestMeterReading: StateFlow<Double?> = _allLogs
        .map { logs ->
            logs
                .filter { it.odometerValue != null }
                .maxByOrNull { it.entryDate }
                ?.odometerValue
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Photos attached to this asset, ordered by sort_order ascending.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else {
                try {
                    dbFactory.get(acctId).photoDao().observeByEntity(assetId, "asset")
                        .catch { e ->
                            Timber.e(e, "[AssetDetailViewModel] Error loading photos for $assetId")
                            emit(emptyList())
                        }
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Could not get photoDao for $acctId")
                    flowOf(emptyList())
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Photos flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onCategoryFilterChanged(category: String?) {
        _categoryFilter.value = category
    }
}
