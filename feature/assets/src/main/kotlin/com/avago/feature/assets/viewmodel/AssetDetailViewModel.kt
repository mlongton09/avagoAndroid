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
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityFilter
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetMeter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.assets.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Log entries grouped by year for the sticky-header list on AssetDetailScreen. */
data class LogsByYear(
    val year: Int,
    val entries: List<Activity>,
)

/** Groups activities by calendar year of [Activity.occurredAt], newest first. */
internal fun groupLogsByYear(logs: List<Activity>, category: String?): List<LogsByYear> {
    val filtered = if (category == null) logs else logs.filter { it.categoryLabel == category }
    val cal = Calendar.getInstance()
    return filtered.sortedByDescending { it.occurredAt }
        .groupBy { cal.timeInMillis = it.occurredAt; cal.get(Calendar.YEAR) }
        .entries
        .sortedByDescending { it.key }
        .map { (year, entries) -> LogsByYear(year, entries) }
}

/** Recently used category keys on [logs], most recent first. */
internal fun recentLogCategories(logs: List<Activity>, limit: Int = 8): List<String> =
    logs.sortedByDescending { it.occurredAt }.mapNotNull { it.categoryLabel?.takeIf(String::isNotBlank) }.distinct().take(limit)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val assets: AssetRepository,
    private val activities: ActivityRepository,
    locations: LocationRepository,
    userPrefsRepository: UserPreferencesRepository,
    permissionsManager: PermissionsManager,
) : ViewModel() {

    private val assetId: String = checkNotNull(savedStateHandle["assetId"]) {
        "assetId is required in SavedStateHandle for AssetDetailViewModel"
    }

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val canEditAsset: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.ASSETS_EDIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.ASSETS_EDIT))

    /** Adding a meter (and logging readings) needs `assets.log_meter_reading`. */
    val canAddMeter: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.ASSETS_LOG_METER_READING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.ASSETS_LOG_METER_READING))

    /** The distance unit (`mi` / `km`) a new odometer is created with. */
    val distanceUnit: StateFlow<String> = userPrefsRepository.distanceUnitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "mi")

    val currencyCode: StateFlow<String> = userPrefsRepository.currencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    val asset: StateFlow<Asset?> = assets.observeAsset(assetId)
        .catch { e -> Timber.e(e, "[AssetDetailViewModel] asset flow error"); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The asset's location row, for its address (`locations`). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val location: StateFlow<Location?> = asset.map { it?.locationId }.distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else locations.observeLocations(includeArchived = true).map { all -> all.firstOrNull { it.id == id } }
        }
        .catch { e -> Timber.e(e, "[AssetDetailViewModel] location flow error"); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val allLogs: StateFlow<List<Activity>> = activities.observeActivities(ActivityFilter(assetId = assetId))
        .catch { e -> Timber.e(e, "[AssetDetailViewModel] log flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableCategories: StateFlow<List<String>> = allLogs
        .map { logs -> logs.mapNotNull { it.categoryLabel?.takeIf(String::isNotBlank) }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentCategories: StateFlow<List<String>> = allLogs
        .map { recentLogCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logsByYear: StateFlow<List<LogsByYear>> = combine(allLogs, _categoryFilter, ::groupLogsByYear)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCost: StateFlow<BigDecimal> = allLogs
        .map { logs -> logs.fold(BigDecimal.ZERO) { acc, a -> acc + a.totalCost } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BigDecimal.ZERO)

    val lastServiceDate: StateFlow<Long?> = allLogs
        .map { logs -> logs.maxOfOrNull { it.occurredAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entryCount: StateFlow<Int> = allLogs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val openWorkOrderCount: StateFlow<Int> = asset
        .map { it?.openWorkOrders ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val daysSinceLastService: StateFlow<Int?> = lastServiceDate
        .map { latest -> latest?.let { TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it).toInt() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The asset's default meter (or its only one); null when the asset tracks no meter. */
    val meter: StateFlow<AssetMeter?> = assets.observeMeters(assetId)
        .map { meters -> meters.firstOrNull { it.isDefault } ?: meters.firstOrNull() }
        .catch { e -> Timber.e(e, "[AssetDetailViewModel] meter flow error"); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestMeterReading: StateFlow<Double?> = meter
        .map { it?.lastValue?.toDouble() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _showMeterDialog = MutableStateFlow(false)
    val showMeterDialog: StateFlow<Boolean> = _showMeterDialog.asStateFlow()

    private val _isSavingMeter = MutableStateFlow(false)
    val isSavingMeter: StateFlow<Boolean> = _isSavingMeter.asStateFlow()

    private val attachments: StateFlow<List<Attachment>> = assets.observeAttachments(AttachmentTarget.ToAsset(assetId))
        .catch { e -> Timber.e(e, "[AssetDetailViewModel] attachment flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Image attachments, in sort order. */
    val photos: StateFlow<List<Attachment>> = attachments
        .map { list -> list.filter { it.file?.isImage == true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Non-image attachments (manuals, invoices, PDFs). */
    val documents: StateFlow<List<Attachment>> = attachments
        .map { list -> list.filter { it.file?.isImage != true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onAddMeterReadingTapped() {
        _showMeterDialog.value = true
    }

    fun onDismissMeterDialog() {
        _showMeterDialog.value = false
    }

    fun saveMeterReading(value: Double) {
        viewModelScope.launch {
            _isSavingMeter.value = true
            try {
                assets.recordReading(assetId, BigDecimal.valueOf(value))
                _showMeterDialog.value = false
            } catch (e: Exception) {
                Timber.e(e, "[AssetDetailViewModel] meter reading failed")
                _errors.tryEmit(e.message ?: "Could not save the reading")
            } finally {
                _isSavingMeter.value = false
            }
        }
    }

    /** Starts tracking a [kind] meter in [unit]; the first meter of an asset becomes its default. */
    fun addMeter(kind: String, unit: String) {
        viewModelScope.launch {
            _isSavingMeter.value = true
            try {
                assets.createMeter(assetId, kind, unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "[AssetDetailViewModel] meter rejected")
                _errors.tryEmit(appContext.getString(R.string.asset_meter_add_failed))
            } catch (e: IllegalStateException) {
                Timber.e(e, "[AssetDetailViewModel] meter not queued")
                _errors.tryEmit(appContext.getString(R.string.asset_meter_add_failed))
            } finally {
                _isSavingMeter.value = false
            }
        }
    }

    fun addBatchLogEntries(categoryIds: List<String>, odometerValue: Double?) {
        val distinctIds = categoryIds.distinct().filter { it.isNotBlank() && it != "service" }
        if (distinctIds.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val meterValue = odometerValue?.takeIf { it > 0.0 }?.let(BigDecimal::valueOf)
            try {
                distinctIds.forEach { categoryId ->
                    activities.create(
                        ActivityDraft(
                            assetId = assetId,
                            type = ActivityType.forCategory(categoryId),
                            categoryLabel = categoryId,
                            title = localizedCategoryName(categoryId),
                            occurredAt = now,
                            meterValue = meterValue,
                        ),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[AssetDetailViewModel] batch log failed")
                _errors.tryEmit(e.message ?: "Could not add the log entries")
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

    /** Copies an entry's descriptive fields to a new entry dated now (no meter, no costs, no files). */
    fun cloneLogEntry(entry: Activity) {
        viewModelScope.launch {
            try {
                activities.create(
                    ActivityDraft(
                        assetId = assetId,
                        type = entry.type,
                        categoryLabel = entry.categoryLabel,
                        title = entry.title,
                        occurredAt = System.currentTimeMillis(),
                        performedByUserId = entry.performedByUserId,
                        performedByName = entry.performedByName,
                        providerName = entry.providerName,
                        currency = entry.currency,
                        notes = entry.notes,
                        payload = entry.payload,
                        attributes = entry.attributes,
                    ),
                )
            } catch (e: Exception) {
                Timber.e(e, "[AssetDetailViewModel] clone failed")
                _errors.tryEmit(e.message ?: "Could not clone the entry")
            }
        }
    }

    suspend fun generatePdf(context: Context): Uri? = withContext(Dispatchers.IO) {
        val currentAsset = asset.value ?: return@withContext null
        val logs = allLogs.value
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
                currentAsset.modelYear?.toString(),
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
            logs.sortedByDescending { it.occurredAt }.forEach { log ->
                if (y > 780f) return@forEach
                canvas.drawText("• ${log.displayTitle} — ${dateFormatter.format(Date(log.occurredAt))}", 40f, y, bodyPaint)
                y += 16f
                val cost = log.totalCost
                if (cost.signum() > 0) {
                    canvas.drawText("  Cost: $${cost.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}", 40f, y, smallPaint)
                    y += 14f
                }
            }
            document.finishPage(page)
            val dir = File(context.filesDir, "pdf_reports").also { it.mkdirs() }
            val file = File(dir, "maintenance_report_${currentAsset.id}.pdf")
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
