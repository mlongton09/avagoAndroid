package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WheelSaveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
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
                val accountId = identityManager.getActiveAccountId() ?: return@launch
                val now = System.currentTimeMillis()
                val db = dbFactory.get(accountId)
                val entry = LogEntity(
                    entryId = UUID.randomUUID().toString(),
                    assetId = assetId,
                    accountId = accountId,
                    title = title,
                    entryDate = now,
                    odometerValue = null,
                    category = category,
                    cost = null,
                    performedBy = null,
                    performedByUserId = null,
                    notes = null,
                    data = data,
                    attributes = null,
                    costMode = null,
                    costItems = null,
                    costLabor = null,
                    costTax = null,
                    currency = null,
                    baseAmount = null,
                    exchangeRateUsed = null,
                    configId = null,
                    configVersion = null,
                    serviceId = null,
                    costMisc = null,
                    parentId = null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0L,
                    seq = null,
                )
                db.logDao().upsert(entry)
                db.syncQueueDao().enqueueWithDedup(
                    SyncQueueEntity(
                        queueId = "log_${entry.entryId}",
                        entityType = "log",
                        entityId = entry.entryId,
                        operation = "insert",
                        serverVersion = 0L,
                        payload = null,
                        syncStatus = "pending",
                        attempts = 0L,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } finally {
                onComplete()
            }
        }
    }
}
