package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.PermissionStore
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.InspectionDefectEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.sync.DefectOutbox
import com.avago.core.sync.DefectRegisterSync
import com.avago.core.sync.SyncEngine
import com.avago.core.ui.AvagoToast
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.OpenDefects
import com.avago.feature.log.model.humanizeIfKey
import com.avago.feature.log.model.parseInspectionLocaleStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

data class OpenDefectsUiState(
    val defects: List<InspectionV2.OpenDefect> = emptyList(),
    /** False when derived from local inspection history (hint shown). */
    val serverBacked: Boolean = false,
    /** Lifecycle changes can be queued (false once the service is known to lack the register). */
    val lifecycleAvailable: Boolean = false,
    /** Rows with a local change / inspection that has not synced yet. */
    val pendingIds: Set<String> = emptySet(),
    /** Rows known only from local history (their changes queue as derived ops). */
    val derivedIds: Set<String> = emptySet(),
    /** defect id → log entry id the row opens (only entries present locally). */
    val openTargets: Map<String, String> = emptyMap(),
    /** `insp.*` → localized text for labels / corner labels / section titles. */
    val strings: Map<String, String> = emptyMap(),
    val canEdit: Boolean = false,
    val canCreateWo: Boolean = false,
) {
    fun label(key: String?): String? = key?.let { strings[it] ?: humanizeIfKey(it) }
}

/**
 * Backs the asset Log "Open defects" panel (web `OpenDefectsPanel`), offline-first: everything is
 * read from Room — the register snapshot `SyncEngine` / [DefectRegisterSync] keep in
 * `inspection_defects` (or, before / without a register, the asset's cached inspection history)
 * overlaid with queued `inspection_defect` changes from `sync_queue`. The network is never awaited:
 * opening the panel only nudges a background refresh, and lifecycle changes are applied locally and
 * queued (client-generated idempotency id) for the sync engine to push.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OpenDefectsViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val permissionStore: PermissionStore,
    private val defectRegister: DefectRegisterSync,
    private val syncEngine: SyncEngine,
    val toast: AvagoToast,
) : ViewModel() {

    private val assetId = MutableStateFlow<String?>(null)

    private val _state = MutableStateFlow(OpenDefectsUiState())
    val state: StateFlow<OpenDefectsUiState> = _state.asStateFlow()

    /** One-shot outcome of a local lifecycle change (true = applied and queued). */
    private val _updateResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val updateResults: SharedFlow<Boolean> = _updateResults.asSharedFlow()

    private data class Inputs(
        val logs: List<LogEntity>,
        val cached: List<InspectionDefectEntity>,
        val registerSeq: Long?,
        val queue: List<SyncQueueEntity>,
    )

    init {
        viewModelScope.launch {
            combine(
                permissionStore.observePermission(PERM_EDIT),
                permissionStore.observePermission(PERM_WO_CREATE),
            ) { edit, wo -> edit to wo }.collect { (edit, wo) ->
                _state.update { it.copy(canEdit = edit, canCreateWo = wo) }
            }
        }
        viewModelScope.launch {
            combine(identity.activeAccountId.filterNotNull(), assetId.filterNotNull()) { a, b -> a to b }
                .flatMapLatest { (accountId, asset) -> observeLocal(accountId, asset).map { Triple(accountId, asset, it) } }
                .catch { e -> Timber.e(e, "[OpenDefectsViewModel] local defects flow failed") }
                .collectLatest { (accountId, asset, inputs) ->
                    val strings = loadStrings(accountId)
                    val view = buildView(asset, inputs)
                    val localIds = inputs.logs.mapTo(HashSet()) { it.entryId }
                    val register = OpenDefects.registerOf(inputs.registerSeq)
                    _state.update { s ->
                        s.copy(
                            defects = view.defects,
                            serverBacked = view.source == OpenDefects.Source.SERVER,
                            lifecycleAvailable = register != OpenDefects.Register.UNSUPPORTED,
                            pendingIds = view.pendingIds,
                            derivedIds = view.derivedIds,
                            openTargets = view.defects.mapNotNull { d ->
                                OpenDefects.openTarget(d, localIds)?.let { d.id to it }
                            }.toMap(),
                            strings = strings,
                        )
                    }
                }
        }
    }

    fun setAssetId(id: String) {
        if (assetId.value != id) {
            assetId.value = id
            _state.update { it.copy(defects = emptyList(), openTargets = emptyMap(), pendingIds = emptySet(), derivedIds = emptySet()) }
        }
        // Background refresh of the cached register; the panel already shows local data.
        viewModelScope.launch {
            identity.getActiveAccountId()?.let { defectRegister.pullIfStale(it) }
        }
    }

    /**
     * Web panel lifecycle — repaired (+ optional note), deferred or reopened — applied to Room and
     * queued for push (offline-safe). The sync engine resolves it against the server (server wins
     * status conflicts with a non-blocking notice).
     */
    fun updateStatus(defect: InspectionV2.OpenDefect, status: String, note: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        val s = _state.value
        if (!s.canEdit || !s.lifecycleAvailable) return
        val derived = defect.id in s.derivedIds
        val op = DefectOutbox.Op(
            assetId = defect.assetId ?: assetId.value ?: return,
            itemKey = defect.key,
            status = status,
            resolutionNote = note?.trim()?.takeIf { it.isNotEmpty() },
            baseStatus = defect.status,
            derived = derived,
            logId = if (derived) defect.lastSeenLogId ?: defect.sourceLogId else null,
            label = listOfNotNull(s.label(defect.label) ?: defect.label, s.label(defect.cornerLabel)).joinToString(" · "),
        )
        viewModelScope.launch {
            val ok = try {
                defectRegister.enqueueStatusChange(accountId, defect.id, op)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[OpenDefectsViewModel] local defect update failed")
                false
            }
            _updateResults.tryEmit(ok)
            if (ok) {
                try {
                    syncEngine.pushIfNeeded()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Stays queued; the next sync cycle retries.
                    Timber.w(e, "[OpenDefectsViewModel] push after defect update failed")
                }
            }
        }
    }

    private fun observeLocal(accountId: String, asset: String): Flow<Inputs> = flow {
        val db = dbFactory.get(accountId)
        val logs = db.logDao().observeAll(accountId)
            .map { list -> list.filter { it.assetId == asset && it.deletedAt == null } }
            // Web `logsStamp`: re-derive only when the asset's history actually changed.
            .distinctUntilChangedBy { list -> Triple(list.size, list.maxOfOrNull { it.updatedAt }, list.maxOfOrNull { it.serverVersion }) }
        emitAll(
            combine(
                logs,
                db.inspectionDefectDao().observeForAsset(asset),
                db.syncMetadataDao().observeById(DefectOutbox.META_KEY).map { it?.lastServerSeq },
                db.syncQueueDao().observeUnsyncedByTypes(listOf(DefectOutbox.ENTITY_TYPE, LOG_ENTITY)),
            ) { l, cached, seq, queue -> Inputs(l, cached, seq, queue) },
        )
    }

    private fun buildView(asset: String, inputs: Inputs): OpenDefects.LocalView {
        val history = inputs.logs.map { log ->
            InspectionV2.HistoryEntry(
                logId = log.entryId,
                assetId = log.assetId,
                logDate = Instant.ofEpochMilli(log.entryDate).toString(),
                category = log.category,
                data = InspectionV2.parseDataObject(log.data),
            )
        }
        val pending = inputs.queue
            .filter { it.entityType == DefectOutbox.ENTITY_TYPE }
            .mapNotNull { row ->
                DefectOutbox.Op.fromJson(row.payload)
                    ?.takeIf { it.assetId.equals(asset, ignoreCase = true) }
                    ?.let { OpenDefects.PendingChange(row.entityId, it.itemKey, it.status, it.derived) }
            }
        return OpenDefects.localView(
            register = OpenDefects.registerOf(inputs.registerSeq),
            cached = inputs.cached.mapNotNull { OpenDefects.fromCachedRow(it.rowJson, it.status) },
            derived = OpenDefects.deriveForAsset(history, asset),
            unsyncedLogIds = inputs.queue.filter { it.entityType == LOG_ENTITY }.mapTo(HashSet()) { it.entityId },
            pending = pending,
        )
    }

    private suspend fun loadStrings(accountId: String): Map<String, String> = try {
        val rows = dbFactory.get(accountId).configDao().getByPattern("Locale", "Inspection%")
        val locale = java.util.Locale.getDefault().toLanguageTag().lowercase()
        val row = rows.firstOrNull { it.key == "Inspection_$locale" }
            ?: rows.firstOrNull { it.key == "Inspection_en-us" }
            ?: rows.maxByOrNull { it.version }
        parseInspectionLocaleStrings(row?.value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[OpenDefectsViewModel] inspection locale strings failed")
        emptyMap()
    }

    companion object {
        const val PERM_EDIT = "service_records.edit_own"
        const val PERM_WO_CREATE = "work_orders.create"
        private const val LOG_ENTITY = "log"
    }
}
