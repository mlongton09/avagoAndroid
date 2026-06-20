package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.model.CreateRentalRequest
import com.avago.core.network.model.RentalCustomer
import com.avago.feature.assets.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val RATE_UNITS_BOOKING = listOf("hourly", "daily", "weekly", "monthly")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalBookingScreen(
    assetId: String,
    onBack: () -> Unit,
    onNavigateToNewCustomer: () -> Unit,
    onBooked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RentalBookingViewModel = hiltViewModel(),
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Step state: 0=Customer, 1=Dates, 2=Confirm
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Booking form state
    var selectedCustomer by remember { mutableStateOf<RentalCustomer?>(null) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var rate by rememberSaveable { mutableStateOf("") }
    var rateUnit by rememberSaveable { mutableStateOf("daily") }
    var notes by rememberSaveable { mutableStateOf("") }

    val stepTitles = listOf(
        stringResource(R.string.rental_booking_step_customer),
        stringResource(R.string.rental_booking_step_dates),
        stringResource(R.string.rental_booking_step_confirm),
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            if (step > 0) step-- else onBack()
                        }) {
                            if (step == 0) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.asset_cancel))
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.rental_booking_back),
                                )
                            }
                        }
                    },
                    title = { Text(stepTitles[step]) },
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / 3f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (step) {
            0 -> CustomerPickerStep(
                customers = customers,
                selectedCustomer = selectedCustomer,
                onSelectCustomer = { selectedCustomer = it },
                onNewCustomer = onNavigateToNewCustomer,
                onNext = { step = 1 },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            1 -> DateRateStep(
                startDate = startDate,
                endDate = endDate,
                rate = rate,
                rateUnit = rateUnit,
                onStartDateChange = { startDate = it },
                onEndDateChange = { endDate = it },
                onRateChange = { rate = it },
                onRateUnitChange = { rateUnit = it },
                notes = notes,
                onNotesChange = { notes = it },
                onNext = { step = 2 },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            2 -> ConfirmBookingStep(
                customer = selectedCustomer,
                startDate = startDate,
                endDate = endDate,
                rate = rate,
                rateUnit = rateUnit,
                notes = notes,
                isLoading = isLoading,
                onConfirm = {
                    val parsedRate = rate.toDoubleOrNull() ?: 0.0
                    val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val request = CreateRentalRequest(
                        asset_id = assetId,
                        rental_customer_id = selectedCustomer?.rental_customer_id,
                        start_at = startInstant.toString(),
                        rate = parsedRate,
                        rate_unit = rateUnit,
                        currency = "USD",
                        customer_name = selectedCustomer?.name,
                        notes = notes.takeIf { it.isNotBlank() },
                    )
                    viewModel.createRental(request, onSuccess = onBooked)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun CustomerPickerStep(
    customers: List<RentalCustomer>,
    selectedCustomer: RentalCustomer?,
    onSelectCustomer: (RentalCustomer?) -> Unit,
    onNewCustomer: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // "No customer" row
            item {
                CustomerPickerRow(
                    name = stringResource(R.string.rental_booking_no_customer),
                    subtitle = null,
                    isSelected = selectedCustomer == null,
                    onClick = { onSelectCustomer(null) },
                )
                HorizontalDivider()
            }
            // "New Customer" row
            item {
                CustomerPickerRow(
                    name = stringResource(R.string.rental_booking_new_customer),
                    subtitle = null,
                    isSelected = false,
                    onClick = onNewCustomer,
                )
                HorizontalDivider()
            }
            // Existing customers
            items(customers, key = { it.rental_customer_id }) { customer ->
                CustomerPickerRow(
                    name = customer.name,
                    subtitle = listOfNotNull(customer.company, customer.email).firstOrNull(),
                    isSelected = selectedCustomer?.rental_customer_id == customer.rental_customer_id,
                    onClick = { onSelectCustomer(customer) },
                )
                HorizontalDivider()
            }
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.rental_booking_next))
        }
    }
}

@Composable
private fun CustomerPickerRow(
    name: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRateStep(
    startDate: LocalDate,
    endDate: LocalDate?,
    rate: String,
    rateUnit: String,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate?) -> Unit,
    onRateChange: (String) -> Unit,
    onRateUnitChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val durationDays = endDate?.let { ChronoUnit.DAYS.between(startDate, it) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Start date
        OutlinedTextField(
            value = startDate.format(DATE_FMT),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.rental_start_date_label)) },
            trailingIcon = {
                IconButton(onClick = { showStartPicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // End date (optional)
        OutlinedTextField(
            value = endDate?.format(DATE_FMT) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.rental_booking_end_date_label)) },
            trailingIcon = {
                Row {
                    if (endDate != null) {
                        IconButton(onClick = { onEndDateChange(null) }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { showEndPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (durationDays != null && durationDays > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${stringResource(R.string.rental_booking_duration_label)}: $durationDays days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Rate
        OutlinedTextField(
            value = rate,
            onValueChange = onRateChange,
            label = { Text(stringResource(R.string.rental_rate_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        // Rate unit chips
        Text(
            text = stringResource(R.string.rental_rate_unit_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RATE_UNITS_BOOKING.forEach { unit ->
                val selected = rateUnit == unit
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onRateUnitChange(unit) },
                ) {
                    Text(
                        text = unit.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.rental_notes_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.rental_booking_next))
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onStartDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        )
                    }
                    showStartPicker = false
                }) { Text(stringResource(R.string.date_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text(stringResource(R.string.asset_cancel))
                }
            },
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (endDate ?: startDate)
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (picked.isBefore(startDate) || picked == startDate) {
                            // end date must be after start date — do not update
                        } else {
                            onEndDateChange(picked)
                        }
                    }
                    showEndPicker = false
                }) { Text(stringResource(R.string.date_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text(stringResource(R.string.asset_cancel))
                }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ConfirmBookingStep(
    customer: RentalCustomer?,
    startDate: LocalDate,
    endDate: LocalDate?,
    rate: String,
    rateUnit: String,
    notes: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedRate = rate.toDoubleOrNull() ?: 0.0
    val durationDays = endDate?.let { ChronoUnit.DAYS.between(startDate, it) }
    val estimatedCost = if (durationDays != null && durationDays > 0 && rateUnit == "daily") {
        parsedRate * durationDays
    } else null

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryRow(
                    label = stringResource(R.string.rental_booking_summary_customer),
                    value = customer?.name ?: stringResource(R.string.rental_booking_no_customer),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val dateRange = if (endDate != null) {
                    "${startDate.format(DATE_FMT)} → ${endDate.format(DATE_FMT)}"
                } else {
                    stringResource(R.string.rental_start_date_label) + ": ${startDate.format(DATE_FMT)}"
                }
                SummaryRow(
                    label = stringResource(R.string.rental_booking_summary_dates),
                    value = dateRange,
                )

                if (durationDays != null && durationDays > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.rental_booking_duration_label)}: $durationDays days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SummaryRow(
                    label = stringResource(R.string.rental_booking_summary_rate),
                    value = "$${"%.2f".format(parsedRate)} / $rateUnit",
                )

                if (estimatedCost != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        label = stringResource(R.string.rental_booking_estimated_cost),
                        value = "$${"%.2f".format(estimatedCost)}",
                        valueWeight = FontWeight.Bold,
                    )
                }

                if (notes.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.rental_notes_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && parsedRate > 0,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.rental_booking_confirm))
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = valueWeight,
        )
    }
}

