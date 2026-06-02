package com.avago.feature.schedule.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.sync.SyncEngine
import com.avago.feature.schedule.repository.ScheduleRepository
import com.avago.feature.schedule.util.RruleHelper
import com.avago.feature.schedule.util.addScheduleToAndroidCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScheduleRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val dbFactory: DatabaseFactory,
) : ViewModel() {

    private val scheduleId: String = requireNotNull(savedStateHandle["scheduleId"]) {
        "ScheduleDetailViewModel requires scheduleId in SavedStateHandle"
    }

    private val _accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.getActiveAccountId())

    @OptIn(ExperimentalCoroutinesApi::class)
    val schedule: StateFlow<ScheduleEntity?> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(null)
            else repository.observeAll(accountId)
                .map { list -> list.firstOrNull { it.scheduleId == scheduleId } }
                .catch { e -> Timber.e(e, "[ScheduleDetailVM] flow error"); emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val linkedWos: StateFlow<List<WorkOrderEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else dbFactory.get(accountId).workOrderDao().observeBySchedule(scheduleId)
                .catch { e -> Timber.e(e, "[ScheduleDetailVM] linkedWos flow error"); emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _completeServiceAssetId = MutableStateFlow<String?>(null)
    val completeServiceAssetId: StateFlow<String?> = _completeServiceAssetId.asStateFlow()

    fun delete() {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.softDelete(accountId, scheduleId)
                _deleted.value = true
            } catch (e: Exception) {
                Timber.e(e, "[ScheduleDetailVM] delete failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Advances the schedule to its next occurrence and records completion.
     *
     * Mirrors iOS AddEditScheduleViewController.completeServiceTapped():
     * - If the schedule has an RRULE, compute the next due date and update
     *   lastCompletedAt so the scheduler advances the occurrence.
     * - If the schedule has no RRULE (one-off), soft-delete it.
     * - In both cases emit the asset id so the UI can open a new log entry.
     */
    fun completeService() {
        val accountId = _accountId.value ?: return
        val s = schedule.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                val rrule = s.rrule
                if (!rrule.isNullOrBlank() && s.nextDueAt != null) {
                    // Advance nextDueAt by one RRULE interval.
                    val currentDue = RruleHelper.epochMsToLocalDate(s.nextDueAt)
                    val nextDue = currentDue?.let { RruleHelper.nextOccurrence(rrule, it) }
                    val nextDueMs = nextDue
                        ?.atStartOfDay(java.time.ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli()
                    val updated = s.copy(
                        lastCompletedAt = now,
                        nextDueAt = nextDueMs ?: s.nextDueAt,
                        updatedAt = now,
                    )
                    repository.upsert(accountId, updated)
                } else {
                    // One-off schedule finished — soft-delete it.
                    repository.softDelete(accountId, scheduleId)
                }
                _completeServiceAssetId.value = s.assetId
            } catch (e: Exception) {
                Timber.e(e, "[ScheduleDetailVM] completeService failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onCompleteServiceHandled() {
        _completeServiceAssetId.value = null
    }

    fun addToCalendar(context: Context, assetName: String) {
        val s = schedule.value ?: return
        addScheduleToAndroidCalendar(context, s, assetName)
    }

    fun refresh() {
        viewModelScope.launch {
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[ScheduleDetailVM] sync failed")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
