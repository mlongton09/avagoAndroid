package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.model.RentalReservation
import com.avago.core.network.model.RentalResponse
import com.avago.feature.assets.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetRentalsScreen(
    assetId: String,
    onBack: () -> Unit,
    onOpenBooking: () -> Unit = {},
    onOpenInvoice: (invoiceId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AssetRentalsViewModel = hiltViewModel(),
) {
    val rentals by viewModel.rentals.collectAsStateWithLifecycle()
    val reservations by viewModel.reservations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }
    var endingRentalId by remember { mutableStateOf<String?>(null) }
    var endCondition by remember { mutableStateOf<String?>(null) }
    var endConditionNotes by remember { mutableStateOf("") }
    var endMeter by remember { mutableStateOf("") }
    var endMeterUnit by remember { mutableStateOf("miles") }
    var startingReservation by remember { mutableStateOf<RentalReservation?>(null) }
    var startRate by remember { mutableStateOf("") }
    var startRateUnit by remember { mutableStateOf("daily") }
    var startCondition by remember { mutableStateOf<String?>(null) }
    var startConditionNotes by remember { mutableStateOf("") }
    var startMeter by remember { mutableStateOf("") }
    var startMeterUnit by remember { mutableStateOf("miles") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.rental_list_title)) },
                actions = {
                    // "Book" action opens the multi-step booking flow
                    TextButton(onClick = onOpenBooking) {
                        Text("Book")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rental_create_title))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isLoading && rentals.isEmpty() && reservations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.rental_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Reservations section
                    if (reservations.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.rental_reservations_section),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        items(reservations, key = { "res_${it.reservation_id}" }) { reservation ->
                            ReservationCard(
                                reservation = reservation,
                                onStartRental = {
                                    startingReservation = reservation
                                    startRate = ""
                                    startRateUnit = "daily"
                                    startCondition = null
                                    startConditionNotes = ""
                                    startMeter = ""
                                    startMeterUnit = "miles"
                                },
                            )
                        }
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Split rentals into overdue / active / ended
                    val isOverdueFn = { r: RentalResponse ->
                        r.end_at == null && r.due_at != null &&
                            runCatching { Instant.parse(r.due_at).isBefore(Instant.now()) }.getOrDefault(false)
                    }
                    val overdueRentals = rentals.filter { isOverdueFn(it) }
                    val activeRentals = rentals.filter { it.end_at == null && !isOverdueFn(it) }
                    val endedRentals = rentals.filter { it.end_at != null }

                    // Overdue section
                    if (overdueRentals.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Overdue (${overdueRentals.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        items(overdueRentals, key = { "over_${it.rental_id}" }) { rental ->
                            RentalCard(
                                rental = rental,
                                isOverdue = true,
                                onEndRental = {
                                    endingRentalId = rental.rental_id
                                    endCondition = null
                                    endConditionNotes = ""
                                    endMeter = ""
                                    endMeterUnit = "miles"
                                },
                                onOpenInvoice = { invoiceId -> onOpenInvoice(invoiceId) },
                                onCreateInvoice = { viewModel.createInvoice(rental.rental_id) },
                            )
                        }
                    }

                    // Active rentals section label
                    if (activeRentals.isNotEmpty() || endedRentals.isNotEmpty()) {
                        item {
                            Text(
                                text = "Rentals",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    items(activeRentals + endedRentals, key = { it.rental_id }) { rental ->
                        RentalCard(
                            rental = rental,
                            isOverdue = false,
                            onEndRental = {
                                endingRentalId = rental.rental_id
                                endCondition = null
                                endConditionNotes = ""
                                endMeter = ""
                                endMeterUnit = "miles"
                            },
                            onOpenInvoice = { invoiceId -> onOpenInvoice(invoiceId) },
                            onCreateInvoice = { viewModel.createInvoice(rental.rental_id) },
                        )
                    }
                    // bottom spacer for FAB
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // "End Rental" bottom sheet
    endingRentalId?.let { rentalId ->
        val endSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                endingRentalId = null
                endCondition = null
                endConditionNotes = ""
                endMeter = ""
                endMeterUnit = "miles"
            },
            sheetState = endSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "End Rental",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Condition at return *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("excellent", "good", "fair", "poor").forEach { cond ->
                        FilterChip(
                            selected = endCondition == cond,
                            onClick = { endCondition = cond },
                            label = { Text(cond.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                OutlinedTextField(
                    value = endConditionNotes,
                    onValueChange = { endConditionNotes = it },
                    label = { Text("Condition notes (optional)") },
                    placeholder = { Text("e.g. scratch on rear bumper") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                val endMeterError = endMeter.isNotBlank() && endMeter.toDoubleOrNull() == null
                OutlinedTextField(
                    value = endMeter,
                    onValueChange = { endMeter = it },
                    label = { Text("Final meter reading (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = endMeterError,
                    supportingText = if (endMeterError) { { Text("Enter a valid number") } } else null,
                )
                if (endMeter.isNotBlank()) {
                    Text(
                        text = "Unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hours", "miles", "km").forEach { unit ->
                            FilterChip(
                                selected = endMeterUnit == unit,
                                onClick = { endMeterUnit = unit },
                                label = { Text(unit.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
                val endSubmitHint = when {
                    endCondition == null -> "Select a condition to continue"
                    else -> null
                }
                if (endSubmitHint != null) {
                    Text(
                        text = endSubmitHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Button(
                    onClick = {
                        viewModel.endRental(rentalId, endMeter.toDoubleOrNull(), endCondition?.lowercase(), endConditionNotes.takeIf { it.isNotBlank() })
                        endingRentalId = null
                        endCondition = null
                        endConditionNotes = ""
                        endMeter = ""
                        endMeterUnit = "miles"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = endCondition != null,
                ) {
                    Text(stringResource(R.string.rental_end_button))
                }
            }
        }
    }

    // "Start Rental" bottom sheet — collect rate before converting reservation
    startingReservation?.let { reservation ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                startingReservation = null
                startRate = ""
                startRateUnit = "daily"
                startCondition = null
                startConditionNotes = ""
                startMeter = ""
                startMeterUnit = "miles"
            },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.rental_reservation_start),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = startRate,
                    onValueChange = { startRate = it },
                    label = { Text(stringResource(R.string.rental_rate_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.rental_rate_unit_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("hourly", "daily", "weekly", "monthly").forEach { unit ->
                        FilterChip(
                            selected = startRateUnit == unit,
                            onClick = { startRateUnit = unit },
                            label = { Text(unit.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Text(
                    text = "Condition *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("excellent", "good", "fair", "poor").forEach { cond ->
                        FilterChip(
                            selected = startCondition == cond,
                            onClick = { startCondition = cond },
                            label = { Text(cond.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                OutlinedTextField(
                    value = startConditionNotes,
                    onValueChange = { startConditionNotes = it },
                    label = { Text("Condition notes (optional)") },
                    placeholder = { Text("e.g. minor wear on left side") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                val startMeterError = startMeter.isNotBlank() && startMeter.toDoubleOrNull() == null
                OutlinedTextField(
                    value = startMeter,
                    onValueChange = { startMeter = it },
                    label = { Text("Meter reading (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = startMeterError,
                    supportingText = if (startMeterError) { { Text("Enter a valid number") } } else null,
                )
                if (startMeter.isNotBlank()) {
                    Text(
                        text = "Unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hours", "miles", "km").forEach { unit ->
                            FilterChip(
                                selected = startMeterUnit == unit,
                                onClick = { startMeterUnit = unit },
                                label = { Text(unit.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
                val startSubmitHint = when {
                    startCondition == null && (startRate.isBlank() || startRate.toDoubleOrNull() == null) ->
                        "Enter a rate and select a condition to continue"
                    startCondition == null -> "Select a condition to continue"
                    startRate.isBlank() || startRate.toDoubleOrNull() == null -> "Enter a rate to continue"
                    else -> null
                }
                if (startSubmitHint != null) {
                    Text(
                        text = startSubmitHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Button(
                    onClick = {
                        viewModel.startReservation(
                            reservationId = reservation.reservation_id,
                            rate = startRate.toDoubleOrNull() ?: 0.0,
                            rateUnit = startRateUnit,
                            meterStart = startMeter.toDoubleOrNull(),
                            meterUnit = if (startMeter.isNotBlank()) startMeterUnit else null,
                            condition = startCondition?.lowercase(),
                            conditionNotes = startConditionNotes.takeIf { it.isNotBlank() },
                        )
                        startingReservation = null
                        startRate = ""
                        startRateUnit = "daily"
                        startCondition = null
                        startConditionNotes = ""
                        startMeter = ""
                        startMeterUnit = "miles"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = startRate.toDoubleOrNull() != null && startRate.isNotBlank() && startCondition != null,
                ) {
                    Text(stringResource(R.string.rental_reservation_start))
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateRentalSheet(
            assetId = assetId,
            onDismiss = { showCreateSheet = false },
            onConfirm = { request ->
                showCreateSheet = false
                viewModel.createRental(request)
            },
        )
    }
}

@Composable
private fun RentalCard(
    rental: RentalResponse,
    isOverdue: Boolean = false,
    onEndRental: () -> Unit,
    onOpenInvoice: (invoiceId: String) -> Unit = {},
    onCreateInvoice: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isActive = rental.status == "active"
    val isEnded = rental.status == "ended"
    val isInvoiced = rental.status == "invoiced"
    val isPaid = rental.status == "paid"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rental.customer_name ?: "Internal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isOverdue) {
                        Text(
                            text = "OVERDUE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isInvoiced) {
                        RentalMiniChip(
                            label = stringResource(R.string.rental_invoiced_chip),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (isPaid) {
                        RentalMiniChip(
                            label = stringResource(R.string.rental_paid_chip),
                            color = Color(0xFF2E7D32),
                        )
                    }
                    RentalStatusChip(status = rental.status)
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${formatRentalRate(rental.rate, rental.currency)} / ${rental.rate_unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            val startLabel = formatRentalDate(rental.start_at)
            val endLabel = rental.end_at?.let { formatRentalDate(it) }
            if (endLabel != null) {
                Text(
                    text = "$startLabel → $endLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.rental_start_date_label) + ": $startLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rental.total_amount?.let { total ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.rental_total_label)}: ${formatRentalRate(total, rental.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            rental.notes?.takeIf { it.isNotBlank() }?.let { noteText ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            rental.condition_start?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Start condition: ${it.replaceFirstChar { c -> c.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            rental.condition_end?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Return condition: ${it.replaceFirstChar { c -> c.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val meterDisplay = when {
                rental.meter_start != null && rental.meter_end != null -> {
                    val usage = rental.meter_end - rental.meter_start
                    "Meter: ${rental.meter_start} → ${rental.meter_end} = ${"%.1f".format(usage)} ${rental.meter_unit ?: ""}"
                }
                rental.meter_start != null -> "Start meter: ${rental.meter_start} ${rental.meter_unit ?: ""}"
                else -> null
            }
            if (meterDisplay != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = meterDisplay, style = MaterialTheme.typography.bodySmall)
            }

            if (isActive) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onEndRental,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.rental_end_button))
                }
            }

            // Ended rentals: show "Create Invoice" action
            if (isEnded) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCreateInvoice,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rental_invoice_create))
                }
            }
        }
    }
}

@Composable
private fun RentalMiniChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReservationCard(
    reservation: RentalReservation,
    onStartRental: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reservation.customer_name ?: "Internal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ReservationStatusChip(status = reservation.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatRentalDate(reservation.reserved_from)} → ${formatRentalDate(reservation.reserved_until)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reservation.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (reservation.status != "cancelled") {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onStartRental,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rental_reservation_start))
                }
            }
        }
    }
}

@Composable
private fun ReservationStatusChip(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (status) {
        "confirmed" -> MaterialTheme.colorScheme.primary to stringResource(R.string.rental_reservation_confirmed)
        "cancelled" -> MaterialTheme.colorScheme.error to stringResource(R.string.rental_reservation_cancelled)
        else -> MaterialTheme.colorScheme.outline to stringResource(R.string.rental_reservation_tentative)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RentalStatusChip(
    status: String,
    modifier: Modifier = Modifier,
) {
    val isActive = status == "active"
    val color = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    val label = if (isActive) stringResource(R.string.rental_status_active)
    else stringResource(R.string.rental_status_ended)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun formatRentalDate(isoString: String): String = try {
    val instant = Instant.parse(isoString)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    instant.atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
} catch (_: Exception) {
    isoString
}

private fun formatRentalRate(amount: Double, currency: String): String = try {
    val locale = when (currency.uppercase()) {
        "USD" -> Locale.US
        "EUR" -> Locale.GERMANY
        "GBP" -> Locale.UK
        else -> Locale.getDefault()
    }
    NumberFormat.getCurrencyInstance(locale).format(amount)
} catch (_: Exception) {
    "%.2f %s".format(amount, currency)
}
