package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.PermissionStore
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ConfigDocuments
import com.avago.core.data.domain.ConfigRepository
import com.avago.core.data.domain.DefectState
import com.avago.core.data.domain.InspectionDefect
import com.avago.core.ui.AvagoToast
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.humanizeIfKey
import com.avago.feature.log.model.parseInspectionLocaleStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

data class OpenDefectsUiState(
    val defects: List<InspectionV2.OpenDefect> = emptyList(),
    /** defect id -> activity id the row opens (latest inspection that saw it, else the reporting one). */
    val openTargets: Map<String, String> = emptyMap(),
    /** `insp.*` -> localized text for labels / corner labels / section titles. */
    val strings: Map<String, String> = emptyMap(),
    val canEdit: Boolean = false,
    val canCreateWo: Boolean = false,
) {
    fun label(key: String?): String? = key?.let { strings[it] ?: humanizeIfKey(it) }
}

/**
 * Backs the asset Log "Open defects" panel (web `OpenDefectsPanel`). The server keeps the register
 * (`inspection_defects`) from inspection activities; the panel reads it from the v2 store and status
 * changes go through `maint.inspection_defects.update` (applied optimistically).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OpenDefectsViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val config: ConfigRepository,
    private val permissionStore: PermissionStore,
    val toast: AvagoToast,
) : ViewModel() {

    private val assetId = MutableStateFlow<String?>(null)

    private val _state = MutableStateFlow(OpenDefectsUiState())
    val state: StateFlow<OpenDefectsUiState> = _state.asStateFlow()

    /** One-shot outcome of a status change (true = applied and queued). */
    private val _updateResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val updateResults: SharedFlow<Boolean> = _updateResults.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                permissionStore.observePermission(PERM_DEFECT_SERVICE),
                permissionStore.observePermission(PERM_WO_EDIT),
                permissionStore.observePermission(PERM_WO_CREATE),
            ) { service, woEdit, woCreate -> (service || woEdit) to woCreate }
                .collect { (edit, wo) -> _state.update { it.copy(canEdit = edit, canCreateWo = wo) } }
        }
        viewModelScope.launch {
            config.observeDocuments(ConfigRepository.LOCALE)
                .map { docs ->
                    parseInspectionLocaleStrings(
                        ConfigDocuments.pick(docs, ConfigRepository.LOCALE_INSPECTION, null, Locale.getDefault())?.payload,
                    )
                }
                .catch { e -> Timber.w(e, "[OpenDefectsViewModel] inspection locale strings failed") }
                .collect { strings -> _state.update { it.copy(strings = strings) } }
        }
        viewModelScope.launch {
            assetId.filterNotNull()
                .flatMapLatest { id -> activities.observeDefects(id, openOnly = false) }
                .catch { e -> Timber.e(e, "[OpenDefectsViewModel] defects flow failed") }
                .collect { rows ->
                    val defects = InspectionV2.sortOpen(rows.filter { it.status != DefectState.REPAIRED }.map(::toOpenDefect))
                    _state.update { s ->
                        s.copy(
                            defects = defects,
                            openTargets = defects.mapNotNull { d -> (d.lastSeenLogId ?: d.sourceLogId)?.let { d.id to it } }.toMap(),
                        )
                    }
                }
        }
    }

    fun setAssetId(id: String) {
        if (assetId.value != id) {
            assetId.value = id
            _state.update { it.copy(defects = emptyList(), openTargets = emptyMap()) }
        }
    }

    /** Web panel lifecycle: repaired (+ optional note), deferred or reopened. */
    fun updateStatus(defect: InspectionV2.OpenDefect, status: String, note: String? = null) {
        if (!_state.value.canEdit) return
        val target = stateFor(status, defect.bucket)
        viewModelScope.launch {
            val ok = try {
                activities.updateDefect(defect.id, target, note?.trim()?.takeIf { it.isNotEmpty() })
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[OpenDefectsViewModel] defect update failed")
                false
            }
            _updateResults.tryEmit(ok)
        }
    }

    companion object {
        const val PERM_DEFECT_SERVICE = "service_records.create"
        const val PERM_WO_EDIT = "work_orders.edit"
        const val PERM_WO_CREATE = "work_orders.create"

        /** Panel status -> register state; reopening a Monitor-bucket row reads `monitoring`, as on the server. */
        fun stateFor(status: String, bucket: InspectionV2.Bucket): DefectState = when (status) {
            InspectionV2.RESOLUTION_REPAIRED -> DefectState.REPAIRED
            "deferred" -> DefectState.DEFERRED
            "monitoring" -> DefectState.MONITORING
            else -> if (bucket == InspectionV2.Bucket.M) DefectState.MONITORING else DefectState.OPEN
        }

        fun toOpenDefect(d: InspectionDefect): InspectionV2.OpenDefect {
            val bucket = if (d.bucket.equals("m", ignoreCase = true)) InspectionV2.Bucket.M else InspectionV2.Bucket.NR
            val severity = d.severity?.takeIf { it == InspectionV2.SEV_MINOR || it == InspectionV2.SEV_OUT_OF_SERVICE }
                ?: InspectionV2.SEV_MAJOR
            return InspectionV2.OpenDefect(
                id = d.id,
                assetId = d.assetId,
                itemId = d.itemId ?: d.itemKey,
                key = d.itemKey,
                label = d.label.orEmpty(),
                cornerLabel = d.cornerLabel,
                sectionTitle = d.sectionTitle,
                bucket = bucket,
                severity = severity,
                note = d.note,
                location = d.locationLabel,
                sourceLogId = d.sourceActivityId,
                reportedAt = Instant.ofEpochMilli(d.reportedAt).toString(),
                lastSeenLogId = d.lastSeenActivityId,
                lastSeenAt = Instant.ofEpochMilli(d.lastSeenAt ?: d.reportedAt).toString(),
                status = d.status.wire,
            )
        }
    }
}
