package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.PmFilter
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmRepository
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.feature.schedule.util.PmSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

data class ScheduleDetailUiState(
    val isLoading: Boolean = true,
    val occurrence: PmOccurrence? = null,
    val plan: PmPlan? = null,
    val workOrder: WorkOrder? = null,
    /** Other cycles of the plan, newest first. */
    val history: List<PmOccurrence> = emptyList(),
)

/** Outcome of a complete / skip / defer action, shown once. */
enum class ScheduleAction { COMPLETED, SKIPPED, DEFERRED, FAILED }

/** One PM occurrence: plan details, linked work order, other cycles; complete / skip / defer. */
@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pm: PmRepository,
    private val workOrders: WorkOrderRepository,
) : ViewModel() {

    private val occurrenceId: String = requireNotNull(savedStateHandle["scheduleId"]) {
        "ScheduleDetailViewModel requires scheduleId in SavedStateHandle"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScheduleDetailUiState> = pm.observeOccurrence(occurrenceId)
        .flatMapLatest { o ->
            if (o == null) {
                flowOf(ScheduleDetailUiState(isLoading = false))
            } else {
                combine(
                    pm.observePlan(o.planId),
                    o.workOrderId?.let(workOrders::observeWorkOrder) ?: flowOf(null),
                    pm.observeOccurrences(PmFilter(states = emptySet())),
                ) { plan, wo, all -> ScheduleDetailUiState(false, o, plan, wo, PmSchedule.history(all, o)) }
            }
        }
        .catch { e ->
            Timber.e(e, "[ScheduleDetailVM] observe failed")
            emit(ScheduleDetailUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleDetailUiState())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _event = MutableStateFlow<ScheduleAction?>(null)
    val event: StateFlow<ScheduleAction?> = _event.asStateFlow()

    /** Records the cycle as done now, with the meter reading at completion when given. */
    fun complete(meterValue: BigDecimal?) = act(ScheduleAction.COMPLETED) {
        pm.complete(occurrenceId, completedAt = System.currentTimeMillis(), meterValue = meterValue)
    }

    fun skip(reason: String) = act(ScheduleAction.SKIPPED) { pm.skip(occurrenceId, reason.trim()) }

    fun defer(dueAt: Long) = act(ScheduleAction.DEFERRED) { pm.defer(occurrenceId, dueAt) }

    fun onEventShown() {
        _event.value = null
    }

    private fun act(success: ScheduleAction, block: suspend () -> Unit) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                block()
                _event.value = success
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "[ScheduleDetailVM] $success failed")
                _event.value = ScheduleAction.FAILED
            } finally {
                _isSaving.value = false
            }
        }
    }
}
