package com.avago.feature.workorders.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.ai.ScreenContextStore
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityFilter
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.ReferenceRepository
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderComment
import com.avago.core.data.domain.WorkOrderDetail
import com.avago.core.data.domain.WorkOrderFilter
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.domain.TimerAction
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.ChecklistRowState
import com.avago.feature.workorders.model.WoCloseout
import com.avago.feature.workorders.model.WoCloseoutOptions
import com.avago.feature.workorders.model.WoCode
import com.avago.feature.workorders.model.WoCodeKind
import com.avago.feature.workorders.model.WoTimerState
import com.avago.feature.workorders.model.isChecklistRowRequired
import com.avago.feature.workorders.model.validateCloseout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

/**
 * The merged work order + log entry screen: the technician logs the work as an activity record
 * linked to the work order (`activity_records.work_order_id`), runs the labour timer and closes
 * the work order out.
 */
@HiltViewModel
class WorkOrderLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val activities: ActivityRepository,
    private val assets: AssetRepository,
    workers: WorkerRepository,
    references: ReferenceRepository,
    private val closeoutSupport: WoCloseoutSupport,
    private val screenContextStore: ScreenContextStore,
    private val formFillRouter: FormFillRouter,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val woId: String = requireNotNull(savedStateHandle["woId"])

    private fun str(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    private val detail: StateFlow<WorkOrderDetail?> = repository.observeDetail(woId)
        .catch { e -> Timber.e(e, "[WoLogVM] detail flow error"); emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val wo: StateFlow<WorkOrder?> = detail.map { it?.workOrder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val comments: StateFlow<List<WorkOrderComment>> = detail.map { it?.comments.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------------- close-out and timer

    val codes: StateFlow<Map<WoCodeKind, List<WoCode>>> = closeoutSupport.codes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WoCloseoutSupport.DEFAULT_CODES)

    val closeoutOptions: StateFlow<WoCloseoutOptions> = closeoutSupport.options
        .stateIn(viewModelScope, SharingStarted.Eagerly, WoCloseoutOptions())

    val checklist: StateFlow<List<ChecklistRowState>> = detail.map { d ->
        d?.checklist.orEmpty().map { ChecklistRowState(isChecklistRowRequired(it.rowType, it.required), it.completed) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _showCloseout = MutableStateFlow(false)
    val showCloseout: StateFlow<Boolean> = _showCloseout.asStateFlow()

    /** The signed-in worker's labour clock on this work order. */
    val timer: StateFlow<WoTimerState> = combine(detail, workers.observeMe()) { d, me ->
        if (d == null || me == null) WoTimerState.IDLE
        else WoTimerState.of(d.timeEntries.filter { it.workerId == me.id })
    }.catch { emit(WoTimerState.IDLE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WoTimerState.IDLE)

    // ---------------------------------------------------------------- header

    val assetName = MutableStateFlow<String?>(null)
    val assetSubtitle = MutableStateFlow<String?>(null)
    val assetType = MutableStateFlow<String?>(null)

    // ---------------------------------------------------------------- log entry form

    val logTitle = MutableStateFlow("")
    val logNotes = MutableStateFlow("")
    val logCategory = MutableStateFlow<String?>(null)
    val logCost = MutableStateFlow("")
    val logMeterReading = MutableStateFlow("")
    val logPerformedBy = MutableStateFlow("")

    /** False when the linked activity has several cost lines (edit them in the cost editor). */
    private val _costEditable = MutableStateFlow(true)
    val costEditable: StateFlow<Boolean> = _costEditable.asStateFlow()

    val availableCategories: StateFlow<List<String>> = references.observeCategories("work_order")
        .map { list -> list.map { it.name }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recentCategoryKeys = MutableStateFlow<List<String>>(emptyList())
    val recentCategoryKeys: StateFlow<List<String>> = _recentCategoryKeys.asStateFlow()

    /** The activity this screen saved or found for the work order. */
    private var activity: Activity? = null
    private var activityLines: List<CostLine> = emptyList()
    /** Performer user id while [logPerformedBy] still shows that worker's name. */
    private var performer: Pair<String, String>? = null
    /** Meter value shown on open; only a changed value is saved as a new reading. */
    private var initialMeter: String = ""

    // ---------------------------------------------------------------- UI state

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val savedSuccessfully = MutableStateFlow(false)
    val completedSuccessfully = MutableStateFlow(false)

    init {
        formFillRouter.register(SCREEN_ID) { fields ->
            val changed = mutableListOf<String>()
            fields["title"]?.let { logTitle.value = it; changed += "title" }
            fields["category"]?.let { logCategory.value = it; changed += "category" }
            fields["notes"]?.let { logNotes.value = it; changed += "notes" }
            fields["cost"]?.let { if (_costEditable.value) { logCost.value = it; changed += "cost" } }
            (fields["meter_reading"] ?: fields["odometer"])?.let { logMeterReading.value = it; changed += "odometer" }
            fields["performed_by"]?.let { logPerformedBy.value = it; changed += "performed_by" }
            changed
        }
        viewModelScope.launch { load() }
    }

    override fun onCleared() {
        formFillRouter.unregister(SCREEN_ID)
        super.onCleared()
    }

    private suspend fun load() {
        try {
            val d = detail.filterNotNull().first()
            val wo = d.workOrder
            logTitle.value = wo.title
            logNotes.value = listOfNotNull(wo.description, wo.dispatcherNotes).filter { it.isNotBlank() }.joinToString("\n\n")
            logCategory.value = wo.categoryName
            d.assignments.firstOrNull { it.workerId == wo.assignedWorkerId }?.let { lead ->
                val name = lead.workerName ?: wo.assignedWorkerName
                if (name != null) {
                    logPerformedBy.value = name
                    performer = lead.workerUserId?.let { it to name }
                }
            }

            wo.primaryAssetId?.let { assetId ->
                val asset = assets.asset(assetId)
                assetName.value = asset?.name
                assetType.value = asset?.assetTypeCode
                assetSubtitle.value = asset?.makeModel ?: asset?.locationName
                screenContextStore.setLogEntryScope(assetId, asset?.name)
                assets.observeMeters(assetId).first().firstOrNull { it.isDefault }?.lastValue
                    ?.let { initialMeter = it.stripTrailingZeros().toPlainString(); logMeterReading.value = initialMeter }
                _recentCategoryKeys.value = recentCategories(
                    repository.observeWorkOrders(WorkOrderFilter(assetId = assetId)).first().map { it.createdAt to it.categoryName },
                )
            }

            // Continue the work order's existing log entry, if there is one.
            val existing = wo.completionActivityId?.let { activities.activity(it) }
                ?: activities.observeActivities(ActivityFilter(workOrderId = woId)).first().maxByOrNull { it.occurredAt }
            existing?.let { a ->
                activity = a
                activityLines = activities.observeCostLines(a.id).first()
                a.title?.takeIf { it.isNotBlank() }?.let { logTitle.value = it }
                a.notes?.takeIf { it.isNotBlank() }?.let { logNotes.value = it }
                a.categoryLabel?.let { logCategory.value = it }
                a.meterValue?.let { initialMeter = it.stripTrailingZeros().toPlainString(); logMeterReading.value = initialMeter }
                a.performedByName?.takeIf { it.isNotBlank() }?.let { name ->
                    logPerformedBy.value = name
                    performer = a.performedByUserId?.let { it to name }
                }
                _costEditable.value = activityLines.size <= 1
                activityLines.singleOrNull()?.let { logCost.value = it.total.stripTrailingZeros().toPlainString() }
            }
        } catch (e: Exception) {
            Timber.e(e, "[WoLogVM] load failed woId=$woId")
        }
    }

    // ---------------------------------------------------------------- actions

    /** Saves the log entry (activity linked to the work order) without completing. */
    fun saveDraft(onSuccess: () -> Unit = {}) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            try {
                persistActivity()
                savedSuccessfully.value = true
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "[WoLogVM] saveDraft failed")
                _errorMessage.value = e.message ?: str(R.string.wo_error_save_failed)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** Completing always goes through the close-out sheet first. */
    fun requestComplete() {
        _errorMessage.value = null
        _showCloseout.value = true
    }

    fun dismissCloseout() { _showCloseout.value = false }

    /** Saves the log entry, stops my timer and completes the work order with [closeout]. */
    fun completeAndClose(closeout: WoCloseout, onSuccess: () -> Unit) {
        val current = wo.value ?: return
        val check = validateCloseout(closeout, current.primaryAssetId != null, checklist.value, closeoutOptions.value)
        if (!check.ok) {
            _errorMessage.value = str(R.string.wo_closeout_missing, check.missing.joinToString())
            return
        }
        viewModelScope.launch {
            _isCompleting.value = true
            _errorMessage.value = null
            try {
                // The close-out records its own meter reading; the log keeps the same value.
                persistActivity(recordMeter = closeout.meterReading == null)
                if (timer.value.running) repository.timer(woId, TimerAction.STOP)
                closeoutSupport.complete(current, closeout)
                _showCloseout.value = false
                completedSuccessfully.value = true
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "[WoLogVM] completeAndClose failed")
                _errorMessage.value = e.message ?: str(R.string.wo_error_save_failed)
            } finally {
                _isCompleting.value = false
            }
        }
    }

    fun onCategorySelected(key: String) { logCategory.value = key }
    fun clearError() { _errorMessage.value = null }

    fun startTimer() = timerAction(TimerAction.START)
    fun pauseTimer() = timerAction(TimerAction.PAUSE)
    fun resumeTimer() = timerAction(TimerAction.RESUME)

    private fun timerAction(action: TimerAction) {
        viewModelScope.launch {
            runCatching { repository.timer(woId, action) }
                .onFailure { e ->
                    Timber.e(e, "[WoLogVM] timer $action failed")
                    _errorMessage.value = e.message ?: str(R.string.wo_error_save_failed)
                }
        }
    }

    fun sendComment(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addComment(woId, trimmed) }
                .onFailure { e ->
                    Timber.e(e, "[WoLogVM] sendComment failed")
                    _errorMessage.value = e.message ?: str(R.string.wo_error_save_failed)
                }
        }
    }

    // ---------------------------------------------------------------- persistence

    private suspend fun persistActivity(recordMeter: Boolean = true) {
        val current = wo.value ?: error("work order not loaded")
        val name = logPerformedBy.value.trim().ifBlank { null }
        val meterText = logMeterReading.value.trim()
        val meter = meterText.replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { recordMeter && meterText != initialMeter }
        val cost = logCost.value.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
        val existing = activity
        val draft = ActivityDraft(
            assetId = current.primaryAssetId,
            workOrderId = woId,
            type = existing?.type ?: ActivityType.REPAIR,
            categoryLabel = logCategory.value,
            title = logTitle.value.trim().ifBlank { current.title },
            occurredAt = existing?.occurredAt ?: System.currentTimeMillis(),
            meterValue = meter ?: existing?.meterValue,
            performedByUserId = performer?.takeIf { it.second == name }?.first,
            performedByName = name,
            providerName = existing?.providerName,
            currency = existing?.currency ?: current.currency,
            overtimeMultiplier = existing?.overtimeMultiplier,
            notes = logNotes.value.trim().ifBlank { null },
            payload = existing?.payload,
            attributes = existing?.attributes,
        )
        if (existing == null) {
            val id = activities.create(draft, lines = listOfNotNull(cost?.let(::costLine)))
            activity = activities.activity(id)
            activityLines = activities.observeCostLines(id).first()
        } else {
            activities.update(existing.id, draft)
            if (_costEditable.value) saveSingleCost(existing.id, cost)
        }
        if (meter != null) initialMeter = meterText
    }

    private suspend fun saveSingleCost(activityId: String, cost: BigDecimal?) {
        val line = activityLines.singleOrNull()
        when {
            line == null && cost != null -> activities.addCostLine(activityId, costLine(cost))
            line != null && cost == null -> activities.deleteCostLine(line.id)
            line != null && cost != null && line.total.compareTo(cost) != 0 ->
                activities.updateCostLine(line.id, costLine(cost).copy(kind = line.kind, description = line.description))
        }
        activityLines = activities.observeCostLines(activityId).first()
    }

    private fun costLine(amount: BigDecimal) =
        CostLineDraft(kind = CostLineType.MISC, description = str(R.string.wo_log_cost_line), quantity = BigDecimal.ONE, unitCost = amount)

    private companion object {
        const val SCREEN_ID = "log_entry"
    }
}
