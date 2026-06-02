package com.avago.feature.assets.viewmodel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.repository.AssetRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Log entries grouped by year for the sticky-header list on AssetDetailScreen.
 */
data class LogsByYear(
    val year: Int,
    val entries: List<LogEntity>,
)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: AssetRepository,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val userPrefsRepository: UserPreferencesRepository,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    private val assetId: String = checkNotNull(savedStateHandle["assetId"]) {
        "assetId is required in SavedStateHandle for AssetDetailViewModel"
    }

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    val canEditAsset: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.ASSETS_EDIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.ASSETS_EDIT))

    val currencyCode: StateFlow<String> = userPrefsRepository.currencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    private val accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = identityManager.getActiveAccountId(),
        )

    /**
     * The asset entity for this screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val asset: StateFlow<AssetEntity?> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(null)
            else {
                try {
                    repository.observeAssets(acctId).map { list ->
                        list.firstOrNull { it.assetId == assetId }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Error observing asset $assetId")
                    flowOf(null)
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Asset flow error")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * All log entries for this asset, reactive to the active account.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allLogs: StateFlow<List<LogEntity>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else {
                try {
                    repository.observeLogsForAsset(acctId, assetId)
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Error observing logs for $assetId")
                    flowOf(emptyList())
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Log flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Available category filter options derived from the loaded log entries.
     */
    val availableCategories: StateFlow<List<String>> = _allLogs
        .map { logs -> logs.mapNotNull { it.category }.distinct().sorted() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentCategories: StateFlow<List<String>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else dbFactory.get(acctId).logDao().observeRecentCategories(acctId, assetId)
                .catch { e ->
                    Timber.e(e, "[AssetDetailViewModel] recent category flow error")
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Filtered and year-grouped log entries for the detail screen.
     */
    val logsByYear: StateFlow<List<LogsByYear>> = combine(
        _allLogs,
        _categoryFilter,
    ) { logs, category ->
        val filtered = if (category == null) logs
        else logs.filter { it.category == category }

        // Sort descending by entryDate so newest entries appear first within each year
        val sorted = filtered.sortedByDescending { it.entryDate }

        // Group by calendar year of entryDate
        sorted
            .groupBy { entry ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = entry.entryDate
                cal.get(Calendar.YEAR)
            }
            .entries
            .sortedByDescending { it.key }
            .map { (year, entries) -> LogsByYear(year = year, entries = entries) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Total cost across all log entries for this asset.
     */
    val totalCost: StateFlow<Double> = _allLogs
        .map { logs -> logs.sumOf { it.cost ?: 0.0 } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0,
        )

    /**
     * Most recent log entryDate, or null if no entries exist.
     */
    val lastServiceDate: StateFlow<Long?> = _allLogs
        .map { logs -> logs.maxOfOrNull { it.entryDate } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Total number of log entries for this asset.
     * Mirrors the "entries" column in the iOS AssetDetailHeaderView stats strip.
     */
    val entryCount: StateFlow<Int> = _allLogs
        .map { logs -> logs.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val openWorkOrderCount: StateFlow<Int> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(0)
            else dbFactory.get(acctId).workOrderDao().observeByAsset(assetId)
                .map { workOrders ->
                    workOrders.count { it.status !in setOf("complete", "cancelled") }
                }
                .catch { e ->
                    Timber.e(e, "[AssetDetailViewModel] open work order count error")
                    emit(0)
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /**
     * Number of days elapsed since the most recent log entry, or null if there are no entries.
     * Mirrors the "since service" column in the iOS stats strip (sinceString(forDate:)).
     */
    val daysSinceLastService: StateFlow<Int?> = _allLogs
        .map { logs ->
            val latest = logs.maxOfOrNull { it.entryDate } ?: return@map null
            val elapsed = System.currentTimeMillis() - latest
            TimeUnit.MILLISECONDS.toDays(elapsed).toInt()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Most recent odometer reading from logs, if the asset tracks meters.
     */
    val latestMeterReading: StateFlow<Double?> = _allLogs
        .map { logs ->
            logs
                .filter { it.odometerValue != null }
                .maxByOrNull { it.entryDate }
                ?.odometerValue
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _showMeterDialog = MutableStateFlow(false)
    val showMeterDialog: StateFlow<Boolean> = _showMeterDialog.asStateFlow()

    private val _isSavingMeter = MutableStateFlow(false)
    val isSavingMeter: StateFlow<Boolean> = _isSavingMeter.asStateFlow()

    /**
     * Photos attached to this asset, ordered by sort_order ascending.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else {
                try {
                    dbFactory.get(acctId).photoDao().observeByEntity(assetId, "asset")
                        .catch { e ->
                            Timber.e(e, "[AssetDetailViewModel] Error loading photos for $assetId")
                            emit(emptyList())
                        }
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Could not get photoDao for $acctId")
                    flowOf(emptyList())
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Photos flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Documents attached to this asset, filtered by asset_id.
     * Uses observeAll and filters client-side since DocDao doesn't expose a per-asset query.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<DocEntity>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else {
                try {
                    dbFactory.get(acctId).docDao().observeAll(acctId)
                        .map { docs -> docs.filter { it.assetId == assetId } }
                        .catch { e ->
                            Timber.e(e, "[AssetDetailViewModel] Error loading docs for $assetId")
                            emit(emptyList())
                        }
                } catch (e: Exception) {
                    Timber.e(e, "[AssetDetailViewModel] Could not get docDao for $acctId")
                    flowOf(emptyList())
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetDetailViewModel] Documents flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onAddMeterReadingTapped() {
        _showMeterDialog.value = true
    }

    fun onDismissMeterDialog() {
        _showMeterDialog.value = false
    }

    fun saveMeterReading(value: Double) {
        viewModelScope.launch {
            val acctId = accountId.value ?: return@launch
            _isSavingMeter.value = true
            try {
                val now = System.currentTimeMillis()
                val db = dbFactory.get(acctId)
                val entry = LogEntity(
                    entryId = UUID.randomUUID().toString(),
                    assetId = assetId,
                    accountId = acctId,
                    title = "Meter Reading",
                    entryDate = now,
                    odometerValue = value,
                    category = "meter",
                    cost = null,
                    performedBy = null,
                    performedByUserId = null,
                    notes = null,
                    data = null,
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
                _showMeterDialog.value = false
            } finally {
                _isSavingMeter.value = false
            }
        }
    }


    fun addBatchLogEntries(categoryIds: List<String>, odometerValue: Double?) {
        val distinctIds = categoryIds.distinct().filter { it.isNotBlank() && it != "service" }
        if (distinctIds.isEmpty()) return
        viewModelScope.launch {
            val acctId = accountId.value ?: return@launch
            val now = System.currentTimeMillis()
            val db = dbFactory.get(acctId)
            val entries = distinctIds.map { categoryId ->
                LogEntity(
                    entryId = UUID.randomUUID().toString(),
                    assetId = assetId,
                    accountId = acctId,
                    title = localizedCategoryName(categoryId),
                    entryDate = now,
                    odometerValue = odometerValue ?: 0.0,
                    category = categoryId,
                    cost = null,
                    performedBy = null,
                    performedByUserId = null,
                    notes = null,
                    data = null,
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
            }
            db.logDao().upsertAll(entries)
            entries.forEach { entry ->
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
            }
        }
    }

    private fun localizedCategoryName(categoryId: String): String {
        val resName = "log_cat_${categoryId.replace("-", "_")}"
        val resId = appContext.resources.getIdentifier(resName, "string", appContext.packageName)
        if (resId != 0) return appContext.getString(resId)
        return categoryId.replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }

    fun cloneLogEntry(entry: LogEntity) {
                viewModelScope.launch {
                    val acctId = accountId.value ?: return@launch
                    val now = System.currentTimeMillis()
                    val clone = entry.copy(
                        entryId = UUID.randomUUID().toString(),
                        accountId = acctId,
                        assetId = assetId,
                        entryDate = now,
                        odometerValue = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0L,
                        seq = null,
                    )
                    val db = dbFactory.get(acctId)
                    db.logDao().upsert(clone)
                    db.syncQueueDao().enqueueWithDedup(
                        SyncQueueEntity(
                            queueId = "log_${clone.entryId}",
                            entityType = "log",
                            entityId = clone.entryId,
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
                }
            }

    suspend fun generatePdf(context: Context): Uri? = withContext(Dispatchers.IO) {
        val currentAsset = asset.value ?: return@withContext null
        val logs = _allLogs.value
        val document = PdfDocument()
        return@withContext try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }
            val sectionPaint = Paint().apply {
                textSize = 14f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply { textSize = 12f }
            val smallPaint = Paint().apply {
                textSize = 10f
                color = 0xFF666666.toInt()
            }
            var y = 50f
            canvas.drawText("Maintenance Report: ${currentAsset.name}", 40f, y, titlePaint)
            y += 30f
            val makeModelYear = listOfNotNull(
                currentAsset.year?.toString(),
                currentAsset.make,
                currentAsset.model,
            ).joinToString(" ")
            if (makeModelYear.isNotBlank()) {
                canvas.drawText(makeModelYear, 40f, y, bodyPaint)
                y += 20f
            }
            val generatedAt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
            canvas.drawText("Generated: $generatedAt", 40f, y, smallPaint)
            y += 30f
            canvas.drawText("Service History (${logs.size} entries)", 40f, y, sectionPaint)
            y += 20f
            val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            logs.sortedByDescending { it.entryDate }.forEach { log ->
                if (y > 780f) return@forEach
                canvas.drawText("• ${log.title} — ${dateFormatter.format(Date(log.entryDate))}", 40f, y, bodyPaint)
                y += 16f
                val cost = log.cost
                if (cost != null && cost > 0) {
                    canvas.drawText("  Cost: $${"%.2f".format(cost)}", 40f, y, smallPaint)
                    y += 14f
                }
            }
            document.finishPage(page)
            val dir = File(context.filesDir, "pdf_reports").also { it.mkdirs() }
            val file = File(dir, "maintenance_report_${currentAsset.assetId}.pdf")
            FileOutputStream(file).use { document.writeTo(it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Timber.e(e, "PDF generation failed")
            null
        } finally {
            document.close()
        }
    }

    fun onCategoryFilterChanged(category: String?) {
        _categoryFilter.value = category
    }
}
