package com.avago.feature.inventory.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.feature.inventory.R
import com.avago.feature.inventory.parts.ProductPickerSheet
import com.avago.feature.inventory.ui.LocationPickerSheet
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferListScreen(
    onBack: () -> Unit,
    viewModel: TransferListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canRequest by viewModel.canRequest.collectAsStateWithLifecycle()
    val canDecide by viewModel.canDecide.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<TransferListItem, MovementStatus>?>(null) }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    form?.let { current ->
        TransferRequestSheet(
            form = current,
            onChange = viewModel::updateForm,
            onSubmit = viewModel::submitRequest,
            onDismiss = viewModel::closeForm,
        )
    }

    pending?.let { (item, status) ->
        TransferDecisionDialog(
            status = status,
            onConfirm = { note ->
                pending = null
                viewModel.decide(item, status, note)
            },
            onDismiss = { pending = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canRequest) {
                FloatingActionButton(onClick = viewModel::startRequest) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.transfer_request_new))
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.transfer_requests_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedStatus == null,
                        onClick = { viewModel.setStatus(null) },
                        label = { Text(stringResource(R.string.transfer_filter_all)) },
                    )
                }
                items(TRANSFER_STATUSES) { status ->
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { viewModel.setStatus(status) },
                        label = { Text(transferStatusLabel(status)) },
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.transfer_requests_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.transfer.id }) { item ->
                        TransferCard(
                            item = item,
                            decisions = if (canDecide) item.decisions else emptyList(),
                            onDecide = { status -> pending = item to status },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(item: TransferListItem, decisions: List<MovementStatus>, onDecide: (MovementStatus) -> Unit) {
    val transfer = item.transfer
    val dateFmt = SimpleDateFormat("MMM d, yyyy", LocalConfiguration.current.locales[0])
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    transfer.number ?: stringResource(transferKindLabel(transfer.kind)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                val (badgeColor, textColor) = statusColors(transfer.status)
                Badge(containerColor = badgeColor, contentColor = textColor) {
                    Text(transferStatusLabel(transfer.status), modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            val route = listOfNotNull(
                item.fromName?.let { stringResource(R.string.transfer_from, it) },
                item.toName?.let { stringResource(R.string.transfer_to, it) },
            ).joinToString("  ")
            if (route.isNotBlank()) {
                Text(route, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            transfer.lines.forEach { line ->
                val qty = line.quantity ?: line.requestedQty
                Text(
                    "${qty?.stripTrailingZeros()?.toPlainString() ?: "\u2014"} \u00d7 ${line.productName ?: line.productSku ?: line.productId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                dateFmt.format(Date(transfer.effectiveAt ?: transfer.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (transfer.decisionNote ?: transfer.notes)?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (decisions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    decisions.forEach { status ->
                        if (status == MovementStatus.CANCELLED || status == MovementStatus.DECLINED) {
                            OutlinedButton(onClick = { onDecide(status) }) { Text(decisionLabel(status)) }
                        } else {
                            Button(onClick = { onDecide(status) }) { Text(decisionLabel(status)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun decisionLabel(status: MovementStatus): String = stringResource(
    when (status) {
        MovementStatus.APPROVED -> R.string.transfer_decide_approve
        MovementStatus.DECLINED -> R.string.transfer_decide_decline
        MovementStatus.POSTED -> R.string.transfer_decide_post
        else -> R.string.transfer_decide_cancel
    },
)

@Composable
private fun TransferDecisionDialog(status: MovementStatus, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(decisionLabel(status)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == MovementStatus.POSTED) {
                    Text(stringResource(R.string.transfer_decide_post_message))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.transfer_decision_note)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim().ifEmpty { null }) }) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferRequestSheet(
    form: TransferRequestForm,
    onChange: ((TransferRequestForm) -> TransferRequestForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickPart by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    if (pickPart) {
        ProductPickerSheet(
            onProductSelected = { product ->
                onChange { it.copy(productId = product.id, productName = product.name) }
                pickPart = false
            },
            onDismiss = { pickPart = false },
        )
    }
    if (pickFrom || pickTo) {
        val from = pickFrom
        LocationPickerSheet(
            onLocationPicked = { location ->
                onChange {
                    if (from) {
                        it.copy(fromLocationId = location?.id, fromLocationName = location?.name)
                    } else {
                        it.copy(toLocationId = location?.id, toLocationName = location?.name)
                    }
                }
                pickFrom = false
                pickTo = false
            },
            onDismiss = {
                pickFrom = false
                pickTo = false
            },
            showClearOption = false,
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
            Text(stringResource(R.string.transfer_request_new), style = MaterialTheme.typography.titleMedium)
            ListItem(
                overlineContent = { Text(stringResource(R.string.stocking_level_part)) },
                headlineContent = { Text(form.productName ?: stringResource(R.string.stocking_level_choose_part)) },
                leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { pickPart = true },
            )
            ListItem(
                overlineContent = { Text(stringResource(R.string.transfer_request_from)) },
                headlineContent = { Text(form.fromLocationName ?: stringResource(R.string.stocking_level_choose_location)) },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { pickFrom = true },
            )
            ListItem(
                overlineContent = { Text(stringResource(R.string.transfer_request_to)) },
                headlineContent = { Text(form.toLocationName ?: stringResource(R.string.stocking_level_choose_location)) },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { pickTo = true },
            )
            if (form.fromLocationId != null && form.fromLocationId == form.toLocationId) {
                Text(
                    stringResource(R.string.transfer_request_same_location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = form.quantity,
                onValueChange = { v -> onChange { it.copy(quantity = v) } },
                label = { Text(stringResource(R.string.transfer_request_quantity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> onChange { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.transfer_request_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSubmit, enabled = form.isComplete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.transfer_request_submit))
            }
        }
    }
}

private fun transferKindLabel(kind: MovementKind): Int = when (kind) {
    MovementKind.TRANSFER_DISPATCH -> R.string.transfer_kind_dispatch
    MovementKind.TRANSFER_RECEIPT -> R.string.transfer_kind_receipt
    else -> R.string.part_issues_type_transfer
}

@Composable
private fun transferStatusLabel(status: MovementStatus): String = stringResource(
    when (status) {
        MovementStatus.DRAFT -> R.string.transfer_status_draft
        MovementStatus.REQUESTED -> R.string.transfer_status_requested
        MovementStatus.APPROVED -> R.string.transfer_status_approved
        MovementStatus.DECLINED -> R.string.transfer_status_declined
        MovementStatus.POSTED -> R.string.transfer_status_posted
        MovementStatus.CANCELLED -> R.string.transfer_status_cancelled
    },
)

@Composable
private fun statusColors(status: MovementStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        MovementStatus.APPROVED, MovementStatus.POSTED -> scheme.primaryContainer to scheme.onPrimaryContainer
        MovementStatus.DECLINED, MovementStatus.CANCELLED -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}
