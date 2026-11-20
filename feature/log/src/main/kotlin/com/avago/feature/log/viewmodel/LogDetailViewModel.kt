package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.ConfigDocuments
import com.avago.core.data.domain.ConfigRepository
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.feature.log.model.parseInspectionLocaleStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/** Who recorded and who last edited an entry; names resolve through the account's workers. */
data class LogAudit(
    val createdAt: Long,
    val createdBy: String?,
    val editedAt: Long?,
    val editedBy: String?,
) {
    companion object {
        /** Saves within this window of creation count as part of recording the entry. */
        const val EDIT_GRACE_MS = 60_000L

        fun of(log: Activity, namesByUserId: Map<String, String>): LogAudit {
            val updatedAt = log.updatedAt
            val edited = updatedAt != null && updatedAt - log.createdAt > EDIT_GRACE_MS
            return LogAudit(
                createdAt = log.createdAt,
                createdBy = log.createdByUserId?.let(namesByUserId::get),
                editedAt = updatedAt.takeIf { edited },
                editedBy = log.updatedByUserId?.takeIf { edited }?.let(namesByUserId::get),
            )
        }
    }
}

data class LogDetailUiState(
    val log: Activity? = null,
    val costLines: List<CostLine> = emptyList(),
    val photos: List<Attachment> = emptyList(),
    val audit: LogAudit? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** Lines plus their tax, in the record currency. */
    val totalCost: BigDecimal get() = costLines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total + taxOf(l, log?.currency) }

    companion object {
        fun taxOf(line: CostLine, currency: String?): BigDecimal {
            val minor = line.taxAmountMinor ?: return BigDecimal.ZERO
            val digits = currency?.let { runCatching { Currency.getInstance(it.uppercase()).defaultFractionDigits }.getOrNull() }
                ?.takeIf { it >= 0 } ?: 2
            return BigDecimal.valueOf(minor, digits)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogDetailViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val assets: AssetRepository,
    private val config: ConfigRepository,
    workers: WorkerRepository,
    userPrefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _entryId = MutableStateFlow<String?>(null)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val preferredCurrency: StateFlow<String> = userPrefsRepository.currencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    val uiState: StateFlow<LogDetailUiState> = _entryId.filterNotNull()
        .flatMapLatest { id ->
            combine(
                activities.observeActivity(id),
                activities.observeCostLines(id),
                assets.observeAttachments(AttachmentTarget.ToActivity(id)),
                workers.observeWorkers(),
            ) { log, lines, files, people ->
                val names = people.mapNotNull { w -> w.userId?.let { it to w.displayName } }.toMap()
                LogDetailUiState(
                    log = log,
                    costLines = lines,
                    photos = files.filter { it.file?.isImage != false },
                    audit = log?.let { LogAudit.of(it, names) },
                    isLoading = false,
                )
            }
        }
        .catch { e ->
            Timber.e(e, "[LogDetailViewModel] observe failed")
            emit(LogDetailUiState(isLoading = false, error = e.message))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogDetailUiState())

    /** Money is shown in the record's own currency; the preference applies when the record has none. */
    val currencyCode: StateFlow<String> = combine(uiState, preferredCurrency) { state, pref -> state.log?.currency ?: pref }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    /** Unit of the asset's default meter (mi, km, h); falls back to the distance preference. */
    val distanceUnit: StateFlow<String> = combine(
        uiState.map { it.log?.assetId }.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else assets.observeMeters(id)
        },
        userPrefsRepository.distanceUnitFlow,
    ) { meters, pref ->
        val meter = meters.firstOrNull { it.isDefault } ?: meters.firstOrNull()
        when (meter?.unit?.lowercase()) {
            null, "" -> pref
            "h", "hr", "hrs", "hours" -> "hrs"
            else -> meter.unit
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "mi")

    /** Asset type of the log's asset (damage-map outline, web `sketchKindFor`); null until known. */
    val assetType: StateFlow<String?> = uiState.map { it.log?.assetId }
        .flatMapLatest { id -> if (id == null) flowOf(null) else assets.observeAsset(id).map { it?.assetTypeCode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Flat `insp.*` -> localized text map for the device locale (resolves defect and item labels). */
    val inspectionStrings: StateFlow<Map<String, String>> = config.observeDocuments(ConfigRepository.LOCALE)
        .map { docs ->
            ConfigDocuments.pick(docs, ConfigRepository.LOCALE_INSPECTION, null, Locale.getDefault())
                ?.payload
                .let(::parseInspectionLocaleStrings)
        }
        .catch { e ->
            Timber.e(e, "[LogDetailViewModel] inspection locale strings failed")
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setEntryId(entryId: String) {
        _entryId.value = entryId
    }

    /** Makes [attachmentId] the entry's primary photo (first in sort order). */
    fun setPrimaryPhoto(attachmentId: String) {
        val entryId = _entryId.value ?: return
        viewModelScope.launch {
            runCatching { assets.setPrimaryAttachment(AttachmentTarget.ToActivity(entryId), attachmentId) }
                .onFailure { e ->
                    Timber.e(e, "[LogDetailViewModel] set primary photo failed for $entryId")
                    _errors.tryEmit(e.message ?: e.javaClass.simpleName)
                }
        }
    }

    fun deleteLog(onDeleted: () -> Unit) {
        val entryId = _entryId.value ?: return
        viewModelScope.launch {
            runCatching { activities.delete(entryId) }
                .onSuccess { onDeleted() }
                .onFailure { e ->
                    Timber.e(e, "[LogDetailViewModel] delete failed for $entryId")
                    _errors.tryEmit(e.message ?: e.javaClass.simpleName)
                }
        }
    }
}
