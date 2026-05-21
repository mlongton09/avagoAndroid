package com.avago.feature.docs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.DocDao
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DocListViewModel @Inject constructor(
    private val docDao: DocDao,
    private val identity: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    /** Currently selected doc-type filter key, or null for "All". */
    private val _filter = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allDocs: StateFlow<List<DocEntity>> = identity.activeAccountId
        .filterNotNull()
        .flatMapLatest { accountId -> docDao.observeAll(accountId) }
        .catch { e ->
            Timber.e(e, "[DocListViewModel] Doc flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val docs: StateFlow<List<DocEntity>> = combine(_allDocs, _filter) { all, type ->
        if (type == null) all else all.filter { it.docType == type }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun onFilterChanged(docType: String?) {
        _filter.value = docType
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[DocListViewModel] Sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
