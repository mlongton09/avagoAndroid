package com.avago.feature.inventory.parts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.StockingPolicyDraft
import com.avago.core.data.domain.formatQty
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import com.avago.feature.inventory.ui.LocationPickerSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal

data class StockingLevelItem(
    val stockingLevel: StockingPolicy,
    val partName: String?,
    val partSku: String?,
    val locationName: String?,
)

data class StockingLevelsUiState(
    val items: List<StockingLevelItem> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

/** The levels being edited: a new policy when [policyId] is null (part and location are chosen). */
data class StockingLevelForm(
    val policyId: String? = null,
    val productId: String? = null,
    val productName: String? = null,
    val locationId: String? = null,
    val locationName: String? = null,
    val minQty: String = "",
    val maxQty: String = "",
    val reorderQty: String = "",
    val economicOrderQty: String = "",
    val safetyQty: String = "",
    val consumptionWindowDays: String = "",
)

@HiltViewModel
class StockingLevelsViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    locations: LocationRepository,
    admin: AdminRepository,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    /** Setting levels needs `stocking_levels.manage`. */
    val canManage: StateFlow<Boolean> = admin.observeAllowed(MANAGE_PERMISSION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _form = MutableStateFlow<StockingLevelForm?>(null)
    val form: StateFlow<StockingLevelForm?> = _form.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<StockingLevelsUiState> = combine(
        combine(
            inventory.observePolicies(),
            inventory.observeProductStock(includeArchived = true),
            locations.observeLocations(includeArchived = true),
        ) { policies, stock, locs ->
            val products = stock.associateBy { it.product.id }
            val locationNames = locs.associate { it.id to it.name }
            policies
                .map { policy ->
                    val product = products[policy.productId]?.product
                    StockingLevelItem(
                        stockingLevel = policy,
                        partName = product?.name,
                        partSku = product?.sku,
                        locationName = locationNames[policy.locationId],
                    )
                }
                .sortedWith(compareBy({ it.partName ?: "" }, { it.locationName ?: "" }, { it.stockingLevel.id }))
        },
        searchQuery,
    ) { items, query ->
        val filteredItems = if (query.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.partName?.contains(query, ignoreCase = true) == true ||
                    item.partSku?.contains(query, ignoreCase = true) == true
            }
        }
        StockingLevelsUiState(
            items = filteredItems,
            isLoading = false,
            searchQuery = query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockingLevelsUiState(),
    )

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }

    fun startNew() {
        _form.value = StockingLevelForm()
    }

    fun edit(item: StockingLevelItem) {
        val p = item.stockingLevel
        _form.value = StockingLevelForm(
            policyId = p.id,
            productId = p.productId,
            productName = item.partName,
            locationId = p.locationId,
            locationName = item.locationName,
            minQty = p.minQty.text(),
            maxQty = p.maxQty.text(),
            reorderQty = p.reorderQty.text(),
            economicOrderQty = p.economicOrderQty.text(),
            safetyQty = p.safetyQty.text(),
            consumptionWindowDays = p.consumptionWindowDays?.toString().orEmpty(),
        )
    }

    fun updateForm(transform: (StockingLevelForm) -> StockingLevelForm) {
        _form.value = _form.value?.let(transform)
    }

    fun closeForm() {
        _form.value = null
    }

    fun clearError() {
        _error.value = null
    }

    /** False when a level is not a valid number (the form stays open). */
    fun save(): Boolean {
        val f = _form.value ?: return false
        val productId = f.productId ?: return false
        val locationId = f.locationId ?: return false
        val levels = listOf(f.minQty, f.maxQty, f.reorderQty, f.economicOrderQty, f.safetyQty).map { it.qtyOrBlank() }
        val window = f.consumptionWindowDays.trim().let { if (it.isEmpty()) null else it.toIntOrNull() ?: -1 }
        if (levels.any { it == INVALID } || (window != null && window <= 0)) return false
        val draft = StockingPolicyDraft(
            productId = productId,
            locationId = locationId,
            minQty = levels[0],
            maxQty = levels[1],
            reorderQty = levels[2],
            economicOrderQty = levels[3],
            safetyQty = levels[4],
            consumptionWindowDays = window,
        )
        _form.value = null
        act { inventory.setStockingPolicy(draft) }
        return true
    }

    fun delete(policyId: String) {
        _form.value = null
        act { inventory.deleteStockingPolicy(policyId) }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                Timber.e(e, "StockingLevelsViewModel: action failed")
                _error.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    companion object {
        const val MANAGE_PERMISSION = "stocking_levels.manage"
        private val INVALID = BigDecimal("-1")

        private fun BigDecimal?.text(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()

        /** Null for blank, [INVALID] for text that is not a non-negative number. */
        private fun String.qtyOrBlank(): BigDecimal? {
            val trimmed = trim()
            if (trimmed.isEmpty()) return null
            return trimmed.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: INVALID
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockingLevelsScreen(
    onBack: () -> Unit,
    viewModel: StockingLevelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    form?.let { current ->
        StockingLevelSheet(
            form = current,
            onChange = viewModel::updateForm,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = viewModel::closeForm,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(onClick = viewModel::startNew) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.stocking_level_add))
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.stocking_levels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AvagoSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                placeholder = stringResource(R.string.stocking_levels_search_hint),
            )

            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState(
                    message = stringResource(R.string.stocking_levels_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.stockingLevel.id }) { item ->
                        StockingLevelCard(
                            item = item,
                            modifier = if (canManage) Modifier.clickable { viewModel.edit(item) } else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockingLevelCard(
    item: StockingLevelItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.partName ?: item.stockingLevel.productId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(item.partSku, item.locationName).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StockValue(
                    label = stringResource(R.string.stocking_level_min_qty),
                    value = item.stockingLevel.minQty,
                )
                StockValue(
                    label = stringResource(R.string.stocking_level_max_qty),
                    value = item.stockingLevel.maxQty,
                )
                StockValue(
                    label = stringResource(R.string.stocking_level_reorder_qty),
                    value = item.stockingLevel.reorderQty,
                )
                StockValue(
                    label = stringResource(R.string.stocking_level_safety_stock),
                    value = item.stockingLevel.safetyQty,
                )
            }
        }
    }
}

@Composable
private fun RowScope.StockValue(
    label: String,
    value: BigDecimal?,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.formatQty() ?: stringResource(R.string.stocking_level_not_set),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockingLevelSheet(
    form: StockingLevelForm,
    onChange: ((StockingLevelForm) -> StockingLevelForm) -> Unit,
    onSave: () -> Boolean,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickPart by remember { mutableStateOf(false) }
    var pickLocation by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val isNew = form.policyId == null

    if (pickPart) {
        ProductPickerSheet(
            onProductSelected = { product ->
                onChange { it.copy(productId = product.id, productName = product.name) }
                pickPart = false
            },
            onDismiss = { pickPart = false },
        )
    }
    if (pickLocation) {
        LocationPickerSheet(
            onLocationPicked = { location ->
                onChange { it.copy(locationId = location?.id, locationName = location?.name) }
                pickLocation = false
            },
            onDismiss = { pickLocation = false },
            showClearOption = false,
        )
    }
    if (confirmDelete && form.policyId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.stocking_level_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(form.policyId)
                }) { Text(stringResource(R.string.stocking_level_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (isNew) R.string.stocking_level_new_title else R.string.stocking_level_edit_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ListItem(
                overlineContent = { Text(stringResource(R.string.stocking_level_part)) },
                headlineContent = { Text(form.productName ?: stringResource(R.string.stocking_level_choose_part)) },
                leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isNew) Modifier.clickable { pickPart = true } else Modifier),
            )
            ListItem(
                overlineContent = { Text(stringResource(R.string.stocking_level_location)) },
                headlineContent = { Text(form.locationName ?: stringResource(R.string.stocking_level_choose_location)) },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isNew) Modifier.clickable { pickLocation = true } else Modifier),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelField(stringResource(R.string.stocking_level_min_qty), form.minQty) { v -> onChange { it.copy(minQty = v) } }
                LevelField(stringResource(R.string.stocking_level_max_qty), form.maxQty) { v -> onChange { it.copy(maxQty = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelField(stringResource(R.string.stocking_level_reorder_qty), form.reorderQty) { v -> onChange { it.copy(reorderQty = v) } }
                LevelField(stringResource(R.string.stocking_level_safety_stock), form.safetyQty) { v -> onChange { it.copy(safetyQty = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelField(stringResource(R.string.stocking_level_eoq), form.economicOrderQty) { v ->
                    onChange { it.copy(economicOrderQty = v) }
                }
                LevelField(stringResource(R.string.stocking_level_window_days), form.consumptionWindowDays, KeyboardType.Number) { v ->
                    onChange { it.copy(consumptionWindowDays = v) }
                }
            }
            Text(
                stringResource(R.string.stocking_level_blank_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (invalid) {
                Text(
                    stringResource(R.string.stocking_level_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { invalid = !onSave() },
                enabled = form.productId != null && form.locationId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }
            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stocking_level_delete))
                }
            }
        }
    }
}

@Composable
private fun RowScope.LevelField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.weight(1f),
    )
}
