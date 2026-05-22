package com.avago.feature.assets.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    modifier: Modifier = Modifier,
    viewModel: AssetRentalsViewModel = hiltViewModel(),
) {
    val rentals by viewModel.rentals.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }
    var pendingEndRentalId by remember { mutableStateOf<String?>(null) }

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
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.rental_list_title)) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
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
            if (!isLoading && rentals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
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
                    items(rentals, key = { it.rental_id }) { rental ->
                        RentalCard(
                            rental = rental,
                            onEndRental = { pendingEndRentalId = rental.rental_id },
                        )
                    }
                    // bottom spacer for FAB
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // "End Rental" confirmation dialog
    pendingEndRentalId?.let { rentalId ->
        AlertDialog(
            onDismissRequest = { pendingEndRentalId = null },
            title = { Text(stringResource(R.string.rental_end_confirm_title)) },
            text = { Text(stringResource(R.string.rental_end_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.endRental(rentalId)
                        pendingEndRentalId = null
                    },
                ) {
                    Text(stringResource(R.string.rental_end_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEndRentalId = null }) {
                    Text(stringResource(R.string.asset_cancel))
                }
            },
        )
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
    onEndRental: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = rental.status == "active"

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
                Text(
                    text = rental.customer_name ?: "Internal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                RentalStatusChip(status = rental.status)
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
        }
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
} catch (e: Exception) {
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
} catch (e: Exception) {
    "%.2f %s".format(amount, currency)
}
