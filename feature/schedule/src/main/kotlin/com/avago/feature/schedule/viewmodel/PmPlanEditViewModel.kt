package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetMeter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.PmRepository
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderTemplate
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.schedule.util.PlanForm
import com.avago.feature.schedule.util.PlanFormError
import com.avago.feature.schedule.util.PlanFormResult
import com.avago.feature.schedule.util.TriggerForm
import com.avago.feature.schedule.util.toDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Outcome of saving or deleting a plan. */
enum class PlanEditResult { SAVED, DELETED, FAILED }

/**
 * Creates (`planId` absent; optional `assetId`) or edits a PM plan with its triggers. Saving sends
 * the typed plan/trigger commands through the repository.
 */
@HiltViewModel
class PmPlanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pm: PmRepository,
    assets: AssetRepository,
    workOrders: WorkOrderRepository,
    permissions: PermissionsManager,
) : ViewModel() {

    val planId: String? = savedStateHandle.get<String>("planId")?.takeIf { it.isNotBlank() }
    private val initialAssetId: String? = savedStateHandle.get<String>("assetId")?.takeIf { it.isNotBlank() }

    /** Null while loading; stays null when the plan is not on this device. */
    private val _form = MutableStateFlow<PlanForm?>(null)
    val form: StateFlow<PlanForm?> = _form.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _error = MutableStateFlow<Pair<PlanFormError, Long?>?>(null)

    /** The validation error and the trigger row it is about, if any. */
    val error: StateFlow<Pair<PlanFormError, Long?>?> = _error.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _result = MutableStateFlow<PlanEditResult?>(null)
    val result: StateFlow<PlanEditResult?> = _result.asStateFlow()

    val canManage: StateFlow<Boolean> = permissions.observeCan(Permissions.WO_MANAGE_RECURRING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissions.can(Permissions.WO_MANAGE_RECURRING))

    val assetChoices: StateFlow<List<Asset>> = assets.observeAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<WorkOrderTemplate>> = workOrders.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Meters of the plan's asset, for meter triggers. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val meters: StateFlow<List<AssetMeter>> = _form.map { it?.assetId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else assets.observeMeters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (planId == null) {
                val name = initialAssetId?.let { assets.observeAsset(it).first()?.name }
                _form.value = PlanForm(assetId = initialAssetId, assetName = name)
            } else {
                val plan = pm.observePlan(planId).first()
                if (plan == null) _notFound.value = true else _form.value = PlanForm.of(plan)
            }
        }
    }

    fun update(transform: (PlanForm) -> PlanForm) {
        _form.value = _form.value?.let(transform)
        _error.value = null
    }

    fun updateTrigger(key: Long, transform: (TriggerForm) -> TriggerForm) = update { f ->
        f.copy(triggers = f.triggers.map { if (it.key == key) transform(it) else it })
    }

    fun addTrigger() = update { f -> f.copy(triggers = f.triggers + TriggerForm(key = f.nextKey)) }

    fun removeTrigger(key: Long) = update { f -> f.copy(triggers = f.triggers.filterNot { it.key == key }) }

    /** Picks the asset of a new plan; meter triggers are reset as their meters belong to the old asset. */
    fun selectAsset(asset: Asset?) {
        if (planId != null) return
        update { f ->
            f.copy(
                assetId = asset?.id,
                assetName = asset?.name,
                triggers = f.triggers.map { it.copy(meterId = null) },
            )
        }
    }

    fun save() {
        val f = _form.value ?: return
        if (_isSaving.value) return
        val draft = when (val r = f.toDraft()) {
            is PlanFormResult.Invalid -> {
                _error.value = r.error to r.triggerKey
                return
            }
            is PlanFormResult.Valid -> r.draft
        }
        perform(PlanEditResult.SAVED) { if (planId == null) pm.createPlan(draft) else pm.updatePlan(planId, draft) }
    }

    fun delete() {
        val id = planId ?: return
        if (_isSaving.value) return
        perform(PlanEditResult.DELETED) { pm.deletePlan(id) }
    }

    fun onResultShown() {
        _result.value = null
    }

    private fun perform(success: PlanEditResult, block: suspend () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                block()
                _result.value = success
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "[PmPlanEditVM] $success failed")
                _result.value = PlanEditResult.FAILED
            } finally {
                _isSaving.value = false
            }
        }
    }
}
