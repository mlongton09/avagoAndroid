package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogListViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    val assetId = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val logs: StateFlow<List<LogEntity>> = combine(
        identity.activeAccountId.filterNotNull(),
        assetId,
        _categoryFilter,
    ) { accountId, assetIdVal, filter ->
        Triple(accountId, assetIdVal, filter)
    }.flatMapLatest { (accountId, assetIdVal, filter) ->
        try {
            val db = dbFactory.get(accountId)
            db.logDao().observeAll(accountId).map { list ->
                list.filter { log ->
                    (assetIdVal == null || log.assetId == assetIdVal) &&
                        (filter == null || log.category == filter)
                }.sortedByDescending { it.entryDate }
            }
        } catch (e: Exception) {
            Timber.e(e, "[LogListViewModel] Failed to observe logs")
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * All distinct categories observed in the current log set, for building filter pills.
     */
    val availableCategories: StateFlow<List<String>> = logs.map { list ->
        list.mapNotNull { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryFilter: StateFlow<String?> = _categoryFilter

    fun setAssetId(id: String?) {
        assetId.value = id
    }

    fun setFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] Refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
