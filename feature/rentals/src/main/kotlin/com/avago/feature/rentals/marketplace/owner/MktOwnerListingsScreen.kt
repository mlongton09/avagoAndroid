package com.avago.feature.rentals.marketplace.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceOwnerRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.domain.MktBodies
import com.avago.feature.rentals.marketplace.domain.MktCategories
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktOwnerListing
import com.avago.feature.rentals.marketplace.domain.MktText
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MARKETPLACE_MANAGE
import com.avago.feature.rentals.marketplace.ui.categoryLabel
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** The summary written in [language] only (no cross-language fallback, so saving never copies EN into DE). */
internal fun summaryIn(l: MktOwnerListing, language: String): String =
    l.summaryI18n.entries.firstOrNull { it.key.substringBefore('-').equals(language, true) && it.value.isNotBlank() }?.value.orEmpty()

data class MktListingsState(
    val allowed: Boolean = true,
    val loading: Boolean = true,
    val items: List<MktOwnerListing>? = null,
    val loadError: MktError? = null,
    val refreshFailed: Boolean = false,
    val savingId: String? = null,
    val error: MktError? = null,
    val saved: Boolean = false,
)

/** Per-item marketplace options (§7 listings): listed flag, category, summary, fulfilment overrides. */
@HiltViewModel
class MktOwnerListingsViewModel @Inject constructor(
    private val repo: MarketplaceOwnerRepository,
    permissions: PermissionsManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MktListingsState())
    val state: StateFlow<MktListingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            permissions.observeCan(PERMISSION_MARKETPLACE_MANAGE).collect { can ->
                val had = _state.value.items != null
                _state.update { it.copy(allowed = can) }
                if (can && !had) load()
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null, refreshFailed = false) }
        viewModelScope.launch {
            when (val r = repo.listings()) {
                is MktResult.Ok -> _state.update { it.copy(loading = false, items = r.value) }
                is MktResult.Err -> _state.update {
                    if (it.items != null && r.error != MktError.Forbidden) it.copy(loading = false, refreshFailed = true)
                    else it.copy(loading = false, loadError = r.error, allowed = it.allowed && r.error != MktError.Forbidden)
                }
            }
        }
    }

    fun setListed(l: MktOwnerListing, listed: Boolean) =
        save(l.copy(listed = listed), summaryIn(l, "en"), summaryIn(l, "de"))

    fun save(l: MktOwnerListing, summaryEn: String, summaryDe: String) {
        if (_state.value.savingId != null) return
        _state.update { it.copy(savingId = l.classId, error = null) }
        viewModelScope.launch {
            when (val r = repo.saveListing(l.classId, MktBodies.listing(l, summaryEn, summaryDe))) {
                is MktResult.Ok -> _state.update { s ->
                    val updated = r.value ?: l
                    s.copy(savingId = null, saved = true, items = s.items?.map { if (it.classId == l.classId) updated else it })
                }
                is MktResult.Err -> {
                    _state.update { it.copy(savingId = null, error = r.error) }
                    if (r.error == MktError.VersionConflict) load()
                }
            }
        }
    }

    fun savedShown() = _state.update { it.copy(saved = false) }
}

@Composable
fun MktOwnerListingsScreen(onBack: () -> Unit, vm: MktOwnerListingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MktOwnerListingsContent(state, onBack, vm::load, vm::setListed, vm::save, vm::savedShown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MktOwnerListingsContent(
    state: MktListingsState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onListed: (MktOwnerListing, Boolean) -> Unit,
    onSave: (MktOwnerListing, String, String) -> Unit,
    onSavedShown: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.mkt_listing_saved)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(savedText)
            onSavedShown()
        }
    }
    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.mkt_my_listings), onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val items = state.items
            when {
                !state.allowed -> NoPermission(stringResource(R.string.mkt_admin_no_permission))
                items == null && state.loading -> SkeletonList(rows = 6)
                items == null -> EmptyState(
                    mktErrorText(state.loadError ?: MktError.Generic, owner = true), icon = Icons.Outlined.Inventory2,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = onRefresh,
                )
                else -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "intro") {
                            Text(
                                stringResource(R.string.mkt_admin_listings_body), style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                            )
                        }
                        if (state.refreshFailed) item(key = "refresh") {
                            ErrorBanner(
                                stringResource(R.string.rentals_refresh_failed), Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg),
                                onRetry = onRefresh, retryLabel = stringResource(R.string.rentals_retry), tone = StatusTone.Warning,
                            )
                        }
                        state.error?.let { e -> item(key = "err") { ErrorBanner(mktErrorText(e, owner = true), Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg)) } }
                        if (items.isEmpty()) item(key = "empty") { EmptyState(stringResource(R.string.mkt_admin_no_listings), icon = Icons.Outlined.Inventory2) }
                        items(items, key = { it.classId }) { l ->
                            ListingRow(l, saving = state.savingId == l.classId, onListed = { onListed(l, it) }, onEdit = { editing = l.classId })
                        }
                    }
                }
            }
        }
    }
    state.items?.firstOrNull { it.classId == editing }?.let { l ->
        ListingEditor(l, saving = state.savingId == l.classId, onDismiss = { editing = null }, onSave = { updated, en, de -> onSave(updated, en, de); editing = null })
    }
}

@Composable
private fun ListingRow(l: MktOwnerListing, saving: Boolean, onListed: (Boolean) -> Unit, onEdit: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Card(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)) {
        Column(Modifier.padding(AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
            SwitchRow(l.name(locale), l.listed && l.eligible, enabled = l.eligible && !saving, onChange = onListed)
            Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                when {
                    !l.eligible -> StatusPill(stringResource(R.string.mkt_admin_not_public), StatusTone.Warning)
                    l.listed -> StatusPill(stringResource(R.string.mkt_listed), StatusTone.Success)
                    else -> StatusPill(stringResource(R.string.mkt_hidden), StatusTone.Neutral)
                }
                l.category?.let { StatusPill(categoryLabel(it), StatusTone.Neutral) }
                if (l.photoCount == 0) StatusPill(stringResource(R.string.mkt_admin_no_photos), StatusTone.Warning)
            }
            MktText.pick(l.summaryI18n, locale)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = onEdit, enabled = !saving, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_admin_edit_details)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ListingEditor(l: MktOwnerListing, saving: Boolean, onDismiss: () -> Unit, onSave: (MktOwnerListing, String, String) -> Unit) {
    var category by rememberSaveable(l.classId) { mutableStateOf(l.category) }
    var en by rememberSaveable(l.classId) { mutableStateOf(summaryIn(l, "en")) }
    var de by rememberSaveable(l.classId) { mutableStateOf(summaryIn(l, "de")) }
    var pickup by rememberSaveable(l.classId) { mutableStateOf(l.offerPickup) }
    var delivery by rememberSaveable(l.classId) { mutableStateOf(l.offerDelivery) }
    var meetup by rememberSaveable(l.classId) { mutableStateOf(l.offerMeetup) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        ) {
            Text(l.name(LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.mkt_admin_category), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                FilterChip(category == null, { category = null }, { Text(stringResource(R.string.mkt_admin_category_none)) }, Modifier.heightIn(min = MinTouch))
                MktCategories.ALL.forEach { c ->
                    FilterChip(category == c, { category = c }, { Text(categoryLabel(c)) }, Modifier.heightIn(min = MinTouch))
                }
            }
            OutlinedTextField(
                en, { en = it.take(300) }, label = { Text(stringResource(R.string.mkt_admin_summary_en)) },
                supportingText = { Text(stringResource(R.string.mkt_admin_summary_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                de, { de = it.take(300) }, label = { Text(stringResource(R.string.mkt_admin_summary_de)) },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            TriState(stringResource(R.string.mkt_admin_override_pickup), pickup) { pickup = it }
            TriState(stringResource(R.string.mkt_admin_override_delivery), delivery) { delivery = it }
            TriState(stringResource(R.string.mkt_admin_override_meetup), meetup) { meetup = it }
            Row(Modifier.fillMaxWidth().padding(vertical = AvagoSpacing.md), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) }
                Button(
                    onClick = { onSave(l.copy(category = category, offerPickup = pickup, offerDelivery = delivery, offerMeetup = meetup), en, de) },
                    enabled = !saving, modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.mkt_admin_save)) }
            }
        }
    }
}

/** Inherit (null) / offered / not offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriState(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
        listOf(null to R.string.mkt_admin_inherit, true to R.string.mkt_admin_yes, false to R.string.mkt_admin_no).forEach { (v, res) ->
            FilterChip(value == v, { onChange(v) }, { Text(stringResource(res)) }, Modifier.heightIn(min = MinTouch))
        }
    }
}

@MktPreviews
@Composable
private fun MktOwnerListingsPreview() {
    AvagoTheme {
        MktOwnerListingsContent(
            MktListingsState(
                loading = false,
                items = listOf(
                    MktOwnerListing("c1", "Utility trailer", emptyMap(), true, true, "trailers", mapOf("en-US" to "5x8, ramp gate"), null, null, true, 3, 1),
                    MktOwnerListing("c2", "Pressure washer", emptyMap(), true, false, null, emptyMap(), null, null, null, 0, 1),
                    MktOwnerListing("c3", "Internal spare", emptyMap(), false, false, null, emptyMap(), null, null, null, 0, 1),
                ),
            ),
            {}, {}, { _, _ -> }, { _, _, _ -> }, {},
        )
    }
}
