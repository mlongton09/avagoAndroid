package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.SyncEngine
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the merged Work Order + Log Entry screen.
 *
 * Mirrors iOS AddLogItemViewController with workOrderContext set:
 * - Pre-fills the log form from WO data (title, notes, category, asset)
 * - saveDraft(): persists the log entry and links it to the WO
 * - completeAndClose(): saves log + transitions WO → "completed"
 */
@HiltViewModel
class WorkOrderLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val dbFactory: DatabaseFactory,
    private val syncEngine: SyncEngine,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    val woId: String = requireNotNull(savedStateHandle["woId"])

    // ── WO context (read-only display) ────────────────────────────────────────
    private val _wo = MutableStateFlow<WorkOrderEntity?>(null)
    val wo: StateFlow<WorkOrderEntity?> = _wo.asStateFlow()

    val assetName = MutableStateFlow<String?>(null)
    val assetSubtitle = MutableStateFlow<String?>(null)
    val assetType = MutableStateFlow<String?>(null)

    // ── Log entry form fields (editable) ──────────────────────────────────────
    val logTitle = MutableStateFlow("")
    val logNotes = MutableStateFlow("")
    val logCategory = MutableStateFlow<String?>(null)
    val logCost = MutableStateFlow("")
    val logMeterReading = MutableStateFlow("")
    val logPerformedBy = MutableStateFlow("")

    // ── Category picker support ───────────────────────────────────────────────
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()
    private val _recentCategoryKeys = MutableStateFlow<List<String>>(emptyList())
    val recentCategoryKeys: StateFlow<List<String>> = _recentCategoryKeys.asStateFlow()

    // ── Comments (live from local DB) ─────────────────────────────────────────
    private val _comments = MutableStateFlow<List<WoCommentEntity>>(emptyList())
    val comments: StateFlow<List<WoCommentEntity>> = _comments.asStateFlow()

    // ── Audit history (from server) ───────────────────────────────────────────
    private val _auditEntries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val auditEntries: StateFlow<List<AuditEntry>> = _auditEntries.asStateFlow()

    // ── UI state ──────────────────────────────────────────────────────────────
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val savedSuccessfully = MutableStateFlow(false)
    val completedSuccessfully = MutableStateFlow(false)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { observeComments() }
        viewModelScope.launch { loadAuditHistory() }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private suspend fun load() {
        try {
            val accountId = identityManager.getActiveAccountId() ?: return
            val db = dbFactory.get(accountId)
            val wo = db.workOrderDao().getById(woId) ?: return
            _wo.value = wo

            // Pre-fill form from WO
            logTitle.value = wo.title
            val noteParts = listOfNotNull(wo.description, wo.dispatcherNotes).filter { it.isNotBlank() }
            logNotes.value = noteParts.joinToString("\n\n")
            logCategory.value = wo.category

            // Asset info
            wo.assetId?.let { assetId ->
                val asset = repository.getAssetById(accountId, assetId)
                assetName.value = asset?.name
                assetType.value = asset?.assetType
                assetSubtitle.value = buildSubtitle(asset)

                val typeKey = asset?.assetType?.takeIf { it.isNotBlank() } ?: "light_vehicle"
                loadCategories(typeKey, assetId, accountId, db)
                loadRecentCategories(assetId, accountId, db)

                // Pre-fill meter from latest log for this asset
                db.logDao().observeAll(accountId)
                    .map { logs ->
                        logs.filter { it.assetId == assetId && it.odometerValue != null }
                            .maxByOrNull { it.entryDate }
                    }
                    .first()
                    ?.odometerValue
                    ?.let { logMeterReading.value = "%.1f".format(it) }
            }

            // Pre-fill performer from WO assignment (use tech ID as fallback display)
            wo.assignedTo?.takeIf { it.isNotBlank() }?.let { techId ->
                logPerformedBy.value = techId
            }

            // If WO already has a linked log entry, load it for continuation
            wo.logId?.takeIf { it.isNotBlank() }?.let { existingLogId ->
                db.logDao().getById(existingLogId)?.let { log ->
                    logTitle.value = log.title
                    log.notes?.takeIf { it.isNotBlank() }?.let { logNotes.value = it }
                    log.category?.let { logCategory.value = it }
                    log.cost?.let { logCost.value = it.toString() }
                    log.odometerValue?.let { logMeterReading.value = "%.1f".format(it) }
                    log.performedBy?.takeIf { it.isNotBlank() }?.let { logPerformedBy.value = it }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[WoLogVM] load failed woId=$woId")
        }
    }

    private suspend fun loadCategories(
        assetType: String,
        assetId: String,
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        try {
            val cats = db.configDao().getByKey("ItemCategory", assetType)
                ?.let { parseCategoryIds(it.value) }?.takeIf { it.isNotEmpty() }
                ?: db.configDao().getByKey("system", "log_categories")
                    ?.let { parseCategoryIdsSeeded(it.value, assetType) }
                ?: return
            if (cats.isNotEmpty()) _availableCategories.value = cats
        } catch (e: Exception) {
            Timber.e(e, "[WoLogVM] loadCategories failed")
        }
    }

    private suspend fun loadRecentCategories(
        assetId: String,
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        try {
            _recentCategoryKeys.value = db.logDao().observeAll(accountId)
                .map { logs ->
                    logs.filter { it.assetId == assetId && !it.category.isNullOrBlank() }
                        .sortedByDescending { it.entryDate }
                        .map { it.category!! }.distinct().take(4)
                }.first()
        } catch (e: Exception) {
            Timber.e(e, "[WoLogVM] loadRecentCategories failed")
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Saves the log entry and links it to the WO without completing. */
    fun saveDraft(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            try {
                val logId = persistLogEntry()
                linkLogToWo(logId)
                savedSuccessfully.value = true
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "[WoLogVM] saveDraft failed")
                _errorMessage.value = e.message ?: "Save failed"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Saves the log entry AND transitions the WO status to "completed".
     * Mirrors iOS completeWorkOrderTapped() which chains status transitions
     * and saves the log atomically.
     */
    fun completeAndClose(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isCompleting.value = true
            _errorMessage.value = null
            try {
                val logId = persistLogEntry()
                val accountId = identityManager.getActiveAccountId() ?: error("no account")
                val wo = _wo.value ?: error("WO not loaded")
                val now = System.currentTimeMillis()
                repository.upsert(
                    accountId,
                    wo.copy(
                        logId = logId,
                        status = "completed",
                        completedAt = now,
                        timerStartedAt = null,   // clear timer on complete (matches iOS clearTimer)
                        updatedAt = now,
                    ),
                )
                completedSuccessfully.value = true
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "[WoLogVM] completeAndClose failed")
                _errorMessage.value = e.message ?: "Complete failed"
            } finally {
                _isCompleting.value = false
            }
        }
    }

    fun onCategorySelected(key: String) { logCategory.value = key }
    fun clearError() { _errorMessage.value = null }

    // ── Timer (mirrors iOS WOTimerView delegate) ──────────────────────────────

    /** Start timer: set timerStartedAt, auto-advance assigned → in_progress. */
    fun startTimer() {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val wo = _wo.value ?: return@launch
            val now = System.currentTimeMillis()
            val updated = wo.copy(
                timerStartedAt = now,
                status = if (wo.status == "assigned") "in_progress" else wo.status,
                updatedAt = now,
            )
            repository.upsert(accountId, updated)
            _wo.value = updated
        }
    }

    /** Pause timer: clear timerStartedAt but keep status as in_progress. */
    fun pauseTimer() {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val wo = _wo.value ?: return@launch
            val now = System.currentTimeMillis()
            val updated = wo.copy(timerStartedAt = null, updatedAt = now)
            repository.upsert(accountId, updated)
            _wo.value = updated
        }
    }

    /** Resume timer: set a fresh timerStartedAt. */
    fun resumeTimer() = startTimer()

    // ── Comments ──────────────────────────────────────────────────────────────

    /** Send a new comment, save locally and enqueue sync. */
    fun sendComment(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val accountId = identityManager.getActiveAccountId() ?: return@launch
                val userId = identityManager.getActiveUserId() ?: accountId
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                db.woCommentDao().upsert(
                    WoCommentEntity(
                        commentId = UUID.randomUUID().toString(),
                        woId = woId,
                        authorId = userId,
                        body = trimmed,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0L,
                        seq = null,
                    ),
                )
                syncEngine.requestSync()
            } catch (e: Exception) {
                Timber.e(e, "[WoLogVM] sendComment failed")
            }
        }
    }

    private suspend fun observeComments() {
        val accountId = identityManager.getActiveAccountId() ?: return
        try {
            dbFactory.get(accountId).woCommentDao().observeAll(accountId)
                .map { list -> list.filter { it.woId == woId }.sortedBy { it.createdAt } }
                .collect { _comments.value = it }
        } catch (e: Exception) {
            Timber.e(e, "[WoLogVM] observeComments failed")
        }
    }

    private suspend fun loadAuditHistory() {
        val accountId = identityManager.getActiveAccountId() ?: return
        try {
            val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
            when (val result = serviceClient.getWorkOrderAudit(accountId, woId)) {
                is NetworkResult.Success -> _auditEntries.value = result.data.map { event ->
                    AuditEntry(
                        description = event.event_type,
                        createdAt = Instant.ofEpochMilli(event.occurred_at)
                            .atZone(ZoneId.systemDefault()).format(fmt),
                    )
                }
                else -> Unit
            }
        } catch (e: Exception) {
            Timber.e(e, "[WoLogVM] loadAuditHistory failed")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun persistLogEntry(): String {
        val accountId = identityManager.getActiveAccountId() ?: error("no account")
        val db = dbFactory.get(accountId)
        val wo = _wo.value ?: error("WO not loaded")
        val now = System.currentTimeMillis()

        val existingId = wo.logId?.takeIf { it.isNotBlank() }
        val logId = existingId ?: UUID.randomUUID().toString()
        val existing = existingId?.let { db.logDao().getById(it) }

        db.logDao().upsert(
            LogEntity(
                entryId = logId,
                assetId = wo.assetId ?: error("WO has no asset"),
                accountId = accountId,
                title = logTitle.value.trim().ifBlank { wo.title },
                entryDate = now,
                odometerValue = logMeterReading.value.toDoubleOrNull(),
                category = logCategory.value,
                cost = logCost.value.toDoubleOrNull(),
                performedBy = logPerformedBy.value.trim().takeIf { it.isNotBlank() },
                performedByUserId = null,
                notes = logNotes.value.trim().takeIf { it.isNotBlank() },
                data = null,
                attributes = null,
                costMode = "total",
                costItems = null,
                costLabor = null,
                costTax = null,
                currency = "USD",
                baseAmount = logCost.value.toDoubleOrNull(),
                exchangeRateUsed = 1.0,
                configId = null,
                configVersion = null,
                serviceId = null,
                costMisc = null,
                parentId = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
                serverVersion = existing?.serverVersion ?: 0L,
                seq = null,
            ),
        )
        syncEngine.sync()
        return logId
    }

    private suspend fun linkLogToWo(logId: String) {
        val accountId = identityManager.getActiveAccountId() ?: return
        val wo = _wo.value ?: return
        if (wo.logId != logId) {
            repository.upsert(accountId, wo.copy(logId = logId, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun buildSubtitle(asset: AssetEntity?): String? {
        if (asset == null) return null
        val realEstate = setOf("residential", "multifamily", "office", "industrial", "healthcare", "restaurant")
        if (asset.assetType in realEstate) return asset.addressLine1?.takeIf { it.isNotBlank() }
        return listOfNotNull(asset.year?.takeIf { it > 0 }?.toString(), asset.make, asset.model)
            .joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun parseCategoryIds(json: String): List<String> {
        val arrStart = json.indexOf("\"categories\"").let { if (it >= 0) json.indexOf('[', it) else json.indexOf('[') }
        if (arrStart < 0) return emptyList()
        var depth = 0; var end = arrStart
        for (i in arrStart until json.length) when (json[i]) {
            '[' -> depth++; ']' -> { depth--; if (depth == 0) { end = i; break } }
        }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substring(arrStart, end + 1)).map { it.groupValues[1] }.toList()
    }

    private fun parseCategoryIdsSeeded(json: String, assetType: String): List<String> {
        val blockStart = json.indexOf("\"asset_type\":\"$assetType\"").takeIf { it >= 0 }
            ?: return parseCategoryIds(json)
        val catStart = json.indexOf("\"categories\"", blockStart)
            .let { if (it >= 0) json.indexOf('[', it) else return emptyList() }
        var depth = 0; var end = catStart
        for (i in catStart until json.length) when (json[i]) {
            '[' -> depth++; ']' -> { depth--; if (depth == 0) { end = i; break } }
        }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substring(catStart, end + 1)).map { it.groupValues[1] }.toList()
    }
}
