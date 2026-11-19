package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ActivityType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WheelSaveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val activities: ActivityRepository,
) : ViewModel() {
    private val assetId: String = checkNotNull(savedStateHandle["assetId"])

    fun saveWheelConfig(
        position: String,
        tireSize: String,
        rimSize: String,
        brand: String,
        notes: String,
        onComplete: () -> Unit,
    ) {
        val payload = JSONObject()
            .put("type", "wheel_configuration")
            .put("position", position)
            .put("tire_size", tireSize)
            .put("rim_size", rimSize)
            .put("brand", brand)
            .put("notes", notes)
            .toString()
        saveLog(
            title = "Wheel Configuration",
            category = "tire_inspection",
            data = payload,
            onComplete = onComplete,
        )
    }

    fun saveWheelData(
        treadDepthMm: String,
        tirePressurePsi: String,
        lastInspectionMs: Long?,
        nextInspectionMs: Long?,
        condition: String,
        onComplete: () -> Unit,
    ) {
        val payload = JSONObject()
            .put("type", "tire_inspection")
            .put("tread_depth_mm", treadDepthMm)
            .put("tire_pressure_psi", tirePressurePsi)
            .put("last_inspection_ms", lastInspectionMs)
            .put("next_inspection_ms", nextInspectionMs)
            .put("condition", condition)
            .toString()
        saveLog(
            title = "Wheel Inspection",
            category = "tire_inspection",
            data = payload,
            onComplete = onComplete,
        )
    }

    private fun saveLog(
        title: String,
        category: String,
        data: String,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                activities.create(
                    ActivityDraft(
                        assetId = assetId,
                        type = ActivityType.forCategory(category),
                        categoryLabel = category,
                        title = title,
                        payload = data,
                    ),
                )
            } catch (e: Exception) {
                Timber.e(e, "[WheelSaveViewModel] save failed")
            } finally {
                onComplete()
            }
        }
    }
}
