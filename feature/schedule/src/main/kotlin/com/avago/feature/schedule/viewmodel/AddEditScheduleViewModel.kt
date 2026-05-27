package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.sync.SyncEngine
import com.avago.feature.schedule.repository.ScheduleRepository
import com.avago.feature.schedule.util.RruleHelper
import com.avago.feature.schedule.util.ScheduleFrequencyPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Which half of the schedule-type toggle is active. */
enum class ScheduleTypeSelection { BY_DATE, BY_METER }

@HiltViewModel
class AddEditScheduleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScheduleRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    /** Non-null when editing an existing schedule. */
    private val scheduleId: String? = savedStateHandle.get<String>("scheduleId")
        ?.takeIf { it.isNotBlank() }

    /** Pre-selected asset when navigating from an asset screen. */
    private val initialAssetId: String? = savedStateHandle.get<String>("assetId")
        ?.takeIf { it.isNotBlank() }

    // -------------------------------------------------------------------------
    // Form state
    // -------------------------------------------------------------------------

    val title = MutableStateFlow("")
    val assetId = MutableStateFlow(initialAssetId ?: "")
    val assetName = MutableStateFlow("")
    val category = MutableStateFlow("")
    val scheduleType = MutableStateFlow(ScheduleTypeSelection.BY_DATE)
    val frequencyPreset = MutableStateFlow(ScheduleFrequencyPreset.MONTHLY)
    val startDate = MutableStateFlow(LocalDate.now())
    val meterType = MutableStateFlow("odometer")
    val meterInterval = MutableStateFlow("")
    val meterCurrent = MutableStateFlow("")

    // End-repeat options (mirrors iOS AddEditScheduleViewController)
    // "never" | "date" | "count"
    val endType = MutableStateFlow("never")
    val endDate = MutableStateFlow<LocalDate?>(null)
    val endCount = MutableStateFlow(1)

    // Additional detail fields (mirrors iOS: location, timezone, notes)
    val location = MutableStateFlow("")
    val timezone = MutableStateFlow(java.util.TimeZone.getDefault().id)
    val notes = MutableStateFlow("")

    // Validation
    val titleError = MutableStateFlow(false)
    val assetError = MutableStateFlow(false)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _savedSuccessfully = MutableStateFlow(false)
    val savedSuccessfully: StateFlow<Boolean> = _savedSuccessfully.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (scheduleId != null) {
            loadExisting(scheduleId)
        }
    }

    // -------------------------------------------------------------------------
    // Load existing schedule for editing
    // -------------------------------------------------------------------------

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            try {
                val existing = repository.getById(accountId, id) ?: return@launch
                title.value = existing.title
                assetId.value = existing.assetId
                category.value = existing.category ?: ""
                scheduleType.value = if (existing.scheduleType == "meter")
                    ScheduleTypeSelection.BY_METER
                else
                    ScheduleTypeSelection.BY_DATE
                frequencyPreset.value = ScheduleFrequencyPreset.fromRrule(existing.rrule)
                meterType.value = existing.meterType ?: "odometer"
                meterInterval.value = existing.meterInterval?.toString() ?: ""
                meterCurrent.value = existing.meterDue?.toString() ?: ""

                // End-repeat options
                endType.value = existing.endType ?: "never"
                endCount.value = existing.endCount?.toInt()?.coerceAtLeast(1) ?: 1
                if (existing.endDate != null) {
                    endDate.value = RruleHelper.epochMsToLocalDate(existing.endDate)
                }

                // nextDueAt doubles as the start date for date-based schedules
                if (existing.nextDueAt != null) {
                    RruleHelper.epochMsToLocalDate(existing.nextDueAt)?.let {
                        startDate.value = it
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditScheduleVM] loadExisting failed")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Asset picker callback
    // -------------------------------------------------------------------------

    /** Called when the user returns from the AssetPickerScreen with a selection. */
    fun onAssetSelected(id: String, name: String) {
        assetId.value = id
        assetName.value = name
        assetError.value = false
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    fun save() {
        // Validate
        titleError.value = title.value.isBlank()
        assetError.value = assetId.value.isBlank()
        if (titleError.value || assetError.value) return

        val accountId = identityManager.getActiveAccountId() ?: run {
            _error.value = "No active account"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                val rrule = if (scheduleType.value == ScheduleTypeSelection.BY_DATE) {
                    RruleHelper.rruleForPreset(frequencyPreset.value)
                } else null

                val startEpochMs = startDate.value
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val id = scheduleId ?: UUID.randomUUID().toString()

                val resolvedEndType = endType.value.takeIf {
                    scheduleType.value == ScheduleTypeSelection.BY_DATE && rrule != null
                } ?: "never"

                val resolvedEndDate: Long? = if (resolvedEndType == "date") {
                    endDate.value
                        ?.atStartOfDay(ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli()
                } else null

                val resolvedEndCount: Long? =
                    if (resolvedEndType == "count") endCount.value.toLong() else null

                val entity = ScheduleEntity(
                    scheduleId = id,
                    assetId = assetId.value,
                    accountId = accountId,
                    title = title.value.trim(),
                    category = category.value.trim().ifBlank { null },
                    scheduleType = if (scheduleType.value == ScheduleTypeSelection.BY_METER)
                        "meter" else "calendar",
                    rrule = rrule,
                    endType = resolvedEndType,
                    endCount = resolvedEndCount,
                    endDate = resolvedEndDate,
                    meterType = meterType.value.ifBlank { null },
                    meterDue = meterCurrent.value.toDoubleOrNull(),
                    meterInterval = meterInterval.value.toDoubleOrNull(),
                    lastCompletedAt = null,
                    nextDueAt = if (scheduleType.value == ScheduleTypeSelection.BY_DATE)
                        startEpochMs else null,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0L,
                    seq = null,
                )

                repository.upsert(accountId, entity)
                _savedSuccessfully.value = true
            } catch (e: Exception) {
                Timber.e(e, "[AddEditScheduleVM] save failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
