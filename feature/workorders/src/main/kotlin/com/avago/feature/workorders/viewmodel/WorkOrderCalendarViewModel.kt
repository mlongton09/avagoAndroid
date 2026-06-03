package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.core.sync.SyncEngine
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class WorkOrderCalendarViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    val selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val displayMonth = MutableStateFlow<LocalDate>(LocalDate.now().withDayOfMonth(1))

    val canSeeAllScope: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_ASSIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_ASSIGN))

    private val _showAll = MutableStateFlow(false)
    val showAll: StateFlow<Boolean> = _showAll.asStateFlow()

    fun toggleScope() { _showAll.value = !_showAll.value }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wosByDate: StateFlow<Map<LocalDate, List<WorkOrderEntity>>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId)
                    .catch { e -> Timber.e(e, "[CalendarVM] flow error"); emit(emptyList()) }
            }
            .combine(_showAll) { wos, showAll ->
                val myUserId = identityManager.getActiveUserId()
                val zone = ZoneId.systemDefault()
                wos.filter { wo ->
                    wo.dueDate != null &&
                        wo.status !in listOf("cancelled") &&
                        (showAll || myUserId == null ||
                            wo.assignedTo == myUserId || wo.createdBy == myUserId)
                }.groupBy { wo ->
                    Instant.ofEpochMilli(wo.dueDate ?: error("unreachable")).atZone(zone).toLocalDate()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    val selectedDayWos: StateFlow<List<WorkOrderEntity>> =
        combine(wosByDate, selectedDate) { map, date ->
            map[date] ?: emptyList()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val assetLabels: StateFlow<Map<String, String?>> = wosByDate
        .flatMapLatest { dateMap ->
            val accountId = identityManager.getActiveAccountId()
                ?: return@flatMapLatest flowOf(emptyMap())
            flow {
                val ids = dateMap.values.flatten().mapNotNull { it.assetId }.distinct()
                emit(ids.associateWith { id -> repository.assetLabelFor(id, accountId) })
            }
        }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun navigateMonth(delta: Int) {
        displayMonth.value = displayMonth.value.plusMonths(delta.toLong())
    }

    fun navigateToToday() {
        val today = LocalDate.now()
        displayMonth.value = today.withDayOfMonth(1)
        selectedDate.value = today
    }

    /**
     * Reschedule a WO by dragging it to a new day.
     */
    fun rescheduleWo(wo: WorkOrderEntity, newDate: LocalDate) {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val newDueMs = newDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val updated = wo.copy(
                    dueDate = newDueMs,
                    updatedAt = System.currentTimeMillis(),
                )
                repository.upsert(accountId, updated)
            } catch (e: Exception) {
                Timber.e(e, "[CalendarVM] rescheduleWo failed")
            }
        }
    }
}
