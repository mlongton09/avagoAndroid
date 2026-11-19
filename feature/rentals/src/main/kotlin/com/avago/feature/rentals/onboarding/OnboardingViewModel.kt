package com.avago.feature.rentals.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.PermissionStore
import com.avago.core.data.domain.SyncRepository
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetTaxonomyRepository
import com.avago.feature.assets.data.AssetScopeRepository
import com.avago.feature.assets.model.AssetTaxonomy
import com.avago.feature.assets.model.AssetTypeCatalog
import com.avago.feature.assets.model.text
import com.avago.feature.assets.model.AssetSuggestionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import com.avago.core.data.domain.Location

data class OnboardingUi(
    val session: DraftSession = DraftSession(),
    val shared: ItemEditor = ItemEditor(),
    val taxonomy: AssetTaxonomy? = null,
    val catalog: AssetTypeCatalog? = null,
    val busy: Boolean = false,
    val suggesting: Boolean = false,
    val permitted: Boolean = false,
    val parsed: JsonObject? = null,
    val notice: String? = null,
    val locations: List<Location> = emptyList(),
)

private const val EDIT_DEBOUNCE_MS = 700L
private const val SUGGEST_NAME_MIN = 3

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // Explicit DI and a single screen's typed event handlers.
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: IdentityManager,
    private val permissions: PermissionStore,
    private val repository: OnboardingRepository,
    private val taxonomyRepository: AssetTaxonomyRepository,
    private val scopes: AssetScopeRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("rental_onboarding", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui = _ui.asStateFlow()
    private var account: String? = null
    private var editVersion = 0L
    private var request: Job? = null
    private var suggestions: Job? = null
    private var persist: Job? = null
    private var importFile: Pair<String, String>? = null
    private val cache = mutableMapOf<String, JsonObject>()
    private val required = listOf(
        "rental.view", "rental.catalog", "rental.settings", "rental.portal_manage",
        "rental.marketplace_manage", "assets.create",
    )

    init {
        viewModelScope.launch {
            combine(permissions.permissions, permissions.isRoot) { _, _ -> required.all(permissions::hasPermission) }
                .collect { allowed -> _ui.value = _ui.value.copy(permitted = allowed) }
        }
        viewModelScope.launch {
            identity.activeAccountId.collectLatest { id ->
                // Account boundaries cancel network work, retain that account's local draft, and clear caches.
                account?.let { writeLocal(it, _ui.value.session, _ui.value.shared) }
                request?.cancel(); suggestions?.cancel(); persist?.cancel()
                account = id; editVersion++; cache.clear(); importFile = null
                val local = id?.let { prefs.getString(it, null) }?.let {
                    runCatching {
                        val obj = Json.parseToJsonElement(it).jsonObject
                        DraftSession(
                            OnboardingEditor.local(obj.getValue("editor").toString()),
                            obj.getValue("revision").jsonPrimitive.long,
                        )
                    }.getOrNull()
                }
                val localShared = id?.let { prefs.getString(it, null) }?.let {
                    runCatching {
                        Json.decodeFromJsonElement(
                            ItemEditor.serializer(), Json.parseToJsonElement(it).jsonObject.getValue("shared"),
                        )
                    }.getOrNull()
                }
                _ui.value = OnboardingUi(session = local ?: DraftSession(), shared = localShared ?: ItemEditor(),
                    permitted = required.all(permissions::hasPermission))
                if (id != null) reload(replaceLocal = local == null, rebase = false)
            }
        }
    }

    private fun current(id: String) = account == id && identity.getActiveAccountId() == id
    private fun session(change: (DraftSession) -> DraftSession) {
        _ui.value = _ui.value.copy(session = change(_ui.value.session))
    }
    fun error(message: String) { session { it.copy(error = message) } }
    private fun rememberLocal() {
        persist?.cancel()
        val id = account ?: return
        val snapshot = _ui.value.session
        val shared = _ui.value.shared
        persist = viewModelScope.launch {
            delay(EDIT_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                writeLocal(id, snapshot, shared)
            }
        }
    }
    private fun writeLocal(id: String, snapshot: DraftSession, shared: ItemEditor) {
        prefs.edit().putString(id, buildJsonObject {
            put("revision", snapshot.revision)
            put("editor", Json.parseToJsonElement(snapshot.editor.localJson()))
            put("shared", Json.encodeToJsonElement(ItemEditor.serializer(), shared))
        }.toString()).apply()
    }
    override fun onCleared() {
        account?.let { writeLocal(it, _ui.value.session, _ui.value.shared) }
    }
    fun edit(change: (OnboardingEditor) -> OnboardingEditor) {
        if (_ui.value.busy || _ui.value.session.publishedSuccessfully) return
        editVersion++
        session { it.copy(editor = change(it.editor), error = null) }
        rememberLocal()
    }
    fun editStore(change: (OnboardingDraft) -> OnboardingDraft) = edit { it.copy(draft = change(it.draft)) }
    fun editShared(change: (ItemEditor) -> ItemEditor) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(shared = change(_ui.value.shared)); rememberLocal()
    }
    fun editRow(id: String, change: (ItemEditor) -> ItemEditor) {
        edit { editor -> editor.copy(rows = editor.rows.map { if (it.id == id) change(it) else it }) }
        if (_ui.value.session.editor.rows.size == 1) suggest(auto = true)
    }
    fun removeRow(id: String) = edit { it.copy(rows = it.rows.filterNot { row -> row.id == id }) }
    fun addRow() = edit {
        it.appendRows(listOf(_ui.value.shared.copy(id = java.util.UUID.randomUUID().toString())))
    }
    fun paste(text: String): Boolean {
        if (_ui.value.busy || _ui.value.session.publishedSuccessfully) return false
        return try {
            edit { it.pasteNames(text, _ui.value.shared) }
            true
        } catch (e: IllegalArgumentException) {
            error(e.message ?: "inventory_limit: 500")
            false
        }
    }
    /** Explicit shared application; existing manual row classifications are never replaced. */
    fun applyShared() = edit { editor ->
        val shared = _ui.value.shared
        editor.copy(rows = editor.rows.map { row ->
            val selected = if (row.item.classification?.text("source") == "manual") row.item.classification
                else shared.item.classification
            row.copy(
                item = row.item.copy(
                    asset_type = shared.item.asset_type, operator_mode = shared.item.operator_mode,
                    classification = selected,
                ),
                rate = shared.rate,
            )
        })
    }
    fun applyOperator() = edit { editor ->
        editor.copy(rows = editor.rows.map {
            it.copy(item = it.item.copy(operator_mode = _ui.value.shared.item.operator_mode))
        })
    }

    /** Explicit refresh after conflict rebases revision while preserving all local edits. */
    fun reload(replaceLocal: Boolean = false, rebase: Boolean = true) {
        val id = account ?: return
        val version = editVersion
        launchRequest(id) {
            val state = repository.get(id)
            if (current(id)) {
                session { it.receive(state, replaceLocal && version == editVersion, rebase) }
                loadMetadata(id)
                rememberLocal()
            }
        }
    }

    private suspend fun loadMetadata(id: String) {
        when (val taxonomy = taxonomyRepository.load()) {
            is ApiResult.Ok -> if (current(id)) _ui.value = _ui.value.copy(taxonomy = taxonomy.value)
            is ApiResult.Err -> if (current(id)) error("taxonomy: ${taxonomy.error.status} ${taxonomy.error.message}")
        }
        val catalog = scopes.catalog()
        if (current(id)) {
            _ui.value = _ui.value.copy(catalog = catalog)
            if (catalog.entries.isEmpty()) error("asset_type_configuration_unavailable")
            val locations = repository.locations()
            if (current(id)) _ui.value = _ui.value.copy(locations = locations)
        }
    }

    private fun launchRequest(id: String, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        request = viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(id)) error(e.message ?: "invalid_service_response") }
            finally { if (current(id)) _ui.value = _ui.value.copy(busy = false) }
        }
    }

    private fun prepareDraft(publish: Boolean): OnboardingDraft? = try {
        require(required.all(permissions::hasPermission)) { "permission_or_feature" }
        val snapshot = _ui.value.session
        val draft = snapshot.editor.toDraft(_ui.value.taxonomy)
        val errors = validateDraft(draft, _ui.value.catalog, _ui.value.taxonomy, publish)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        require(!publish || snapshot.editor.importError == null) { snapshot.editor.importError.orEmpty() }
        draft
    } catch (e: IllegalArgumentException) {
        error(e.message ?: "invalid_draft"); null
    } catch (e: IllegalStateException) {
        error(e.message ?: "invalid_draft"); null
    }

    fun save(publish: Boolean = false) {
        val id = account?.takeUnless { _ui.value.busy } ?: return
        val draft = prepareDraft(publish) ?: return
        val snapshot = _ui.value.session
        launchRequest(id) {
            val result = persistOnboarding(repository, id, snapshot, draft, publish)
            if (current(id)) {
                session { result.copy(editor = it.editor) }
                rememberLocal()
                // Existing sync batches each response page and watermark inside one Room transaction.
                if (publish && result.publishedSuccessfully) sync.syncNow()
            }
        }
    }

    fun cancelSuggestions() { suggestions?.cancel(); _ui.value = _ui.value.copy(suggesting = false) }
    fun suggest(auto: Boolean = false) {
        suggestions?.cancel()
        val id = account ?: return
        val snapshots = _ui.value.session.editor.rows.filter {
            it.item.name.trim().length >= SUGGEST_NAME_MIN &&
                it.item.asset_type.isNotBlank() && it.item.classification == null
        }
        if (snapshots.isEmpty()) return
        suggestions = viewModelScope.launch {
            if (auto) delay(EDIT_DEBOUNCE_MS)
            _ui.value = _ui.value.copy(suggesting = true)
            try {
                for (row in snapshots) {
                    yield() // Cache hits still yield so a large identical-name batch remains cancellable.
                    if (!current(id)) break
                    suggestRow(id, row)
                }
            } finally { if (current(id)) _ui.value = _ui.value.copy(suggesting = false) }
        }
    }

    private suspend fun suggestRow(id: String, row: ItemEditor) {
        val key = "$id|${row.item.name.trim()}|${row.item.asset_type}"
        val response = cache[key]?.let { ApiResult.Ok(it) }
            ?: taxonomyRepository.suggest(id, row.item.name.trim(), row.item.asset_type)
        if (current(id)) when (response) {
            is ApiResult.Err -> error("Qwen: ${response.error.status} ${response.error.message}")
            is ApiResult.Ok -> {
                cache[key] = response.value
                applySuggestion(AssetSuggestionTarget(id, row.id, row.item.name, row.item.asset_type), response.value)
            }
        }
    }

    private fun applySuggestion(target: AssetSuggestionTarget, response: JsonObject) {
        val live = _ui.value.session.editor.rows.find { it.id == target.row } ?: return
        val liveTarget = AssetSuggestionTarget(account.orEmpty(), live.id, live.item.name, live.item.asset_type)
        if (target.matches(liveTarget, live.item.classification)) {
            val selected = _ui.value.taxonomy?.acceptSuggestion(live.item.classification, response)
            if (selected == null) _ui.value = _ui.value.copy(notice = "category_review_required")
            else edit { editor -> editor.copy(rows = editor.rows.map {
                if (it.id == live.id) it.copy(item = it.item.copy(classification = selected)) else it
            }) }
        }
    }

    fun importFile(uri: Uri) {
        val id = account ?: return
        launchRequest(id) {
            val file = readImportDocument(context, uri)
            if (current(id)) { importFile = file; parseFile(id, file, null) }
        }
    }
    fun selectSheet(sheet: String) {
        val file = importFile ?: return
        val id = account ?: return
        launchRequest(id) { parseFile(id, file, sheet) }
    }
    private suspend fun parseFile(id: String, file: Pair<String, String>, sheet: String?) {
        when (val result = repository.parse(id, file.first, file.second, sheet)) {
            is ApiResult.Ok -> if (current(id)) _ui.value = _ui.value.copy(parsed = result.value)
            is ApiResult.Err -> if (current(id)) session { it.receive(result, false) }
        }
    }
    fun acceptImport() {
        try {
            val rows = importRows(_ui.value.parsed ?: return, _ui.value.shared)
            edit { it.copy(draft = it.draft.copy(mode = "different")).appendRows(rows) }
            _ui.value = _ui.value.copy(parsed = null)
        } catch (e: IllegalArgumentException) { error(e.message ?: "import_invalid") }
    }
}
