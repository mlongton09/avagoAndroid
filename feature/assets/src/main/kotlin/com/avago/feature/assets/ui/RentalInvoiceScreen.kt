package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.model.InvoiceLineItem
import com.avago.core.network.model.RentalInvoice
import com.avago.feature.assets.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PAYMENT_METHODS = listOf("Cash", "Credit Card", "Bank Transfer", "Check", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalInvoiceScreen(
    invoiceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RentalInvoiceViewModel = hiltViewModel(),
) {
    val invoice by viewModel.invoice.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPaySheet by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.rental_invoice_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (isLoading && invoice == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            invoice?.let { inv ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Invoice header card
                    InvoiceHeaderCard(invoice = inv)
                    Spacer(Modifier.height(12.dp))

                    // Date range card
                    InvoiceDatesCard(invoice = inv)
                    Spacer(Modifier.height(12.dp))

                    // Line items
                    if (inv.line_items.isNotEmpty()) {
                        InvoiceLineItemsCard(lineItems = inv.line_items)
                        Spacer(Modifier.height(12.dp))
                    }

                    // Totals card
                    InvoiceTotalsCard(invoice = inv)
                    Spacer(Modifier.height(20.dp))

                    // Action buttons based on status
                    when (inv.status) {
                        "draft" -> {
                            Button(
                                onClick = { viewModel.sendInvoice() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                            ) {
                                Text(stringResource(R.string.rental_invoice_send))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showVoidConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                enabled = !isLoading,
                            ) {
                                Text(stringResource(R.string.rental_invoice_void))
                            }
                        }

                        "sent" -> {
                            Button(
                                onClick = { showPaySheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                            ) {
                                Text(stringResource(R.string.rental_invoice_mark_paid))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showVoidConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                enabled = !isLoading,
                            ) {
                                Text(stringResource(R.string.rental_invoice_void))
                            }
                        }

                        "paid" -> {
                            val paidOn = listOfNotNull(
                                inv.paid_at?.let { formatInvoiceDate(it) },
                                inv.payment_method,
                            ).joinToString(" · ")
                            if (paidOn.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = paidOn,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }

                        "void" -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.rental_invoice_status_void),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    // Void confirmation dialog
    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text(stringResource(R.string.rental_invoice_void)) },
            text = { Text("This invoice will be voided and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.voidInvoice()
                        showVoidConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.rental_invoice_void))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidConfirm = false }) {
                    Text(stringResource(R.string.asset_cancel))
                }
            },
        )
    }

    // Pay bottom sheet
    if (showPaySheet) {
        PaymentBottomSheet(
            onDismiss = { showPaySheet = false },
            onConfirm = { method, payNotes ->
                viewModel.payInvoice(paymentMethod = method, paymentNotes = payNotes)
                showPaySheet = false
            },
        )
    }
}

@Composable
private fun InvoiceHeaderCard(invoice: RentalInvoice, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.invoice_number,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = invoice.asset_name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InvoiceStatusChip(status = invoice.status)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = invoice.customer_name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            invoice.due_date?.let { due ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rental_invoice_due_date, formatInvoiceDate(due)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InvoiceDatesCard(invoice: RentalInvoice, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Period",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatInvoiceDate(invoice.start_at)} → ${formatInvoiceDate(invoice.end_at)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun InvoiceLineItemsCard(
    lineItems: List<InvoiceLineItem>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.rental_invoice_line_items),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            lineItems.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${item.quantity} ${item.unit} @ ${"%.2f".format(item.unit_price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${"%.2f".format(item.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceTotalsCard(invoice: RentalInvoice, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TotalRow(
                label = stringResource(R.string.rental_invoice_subtotal),
                value = formatCurrency(invoice.subtotal, invoice.currency),
            )
            if (invoice.tax_rate > 0) {
                Spacer(Modifier.height(6.dp))
                TotalRow(
                    label = stringResource(R.string.rental_invoice_tax, invoice.tax_rate * 100),
                    value = formatCurrency(invoice.tax_amount, invoice.currency),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TotalRow(
                label = stringResource(R.string.rental_invoice_total),
                value = formatCurrency(invoice.total_amount, invoice.currency),
                isBold = true,
            )
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun InvoiceStatusChip(status: String, modifier: Modifier = Modifier) {
    val (color, label) = when (status) {
        "draft" -> MaterialTheme.colorScheme.outline to stringResource(R.string.rental_invoice_status_draft)
        "sent" -> MaterialTheme.colorScheme.primary to stringResource(R.string.rental_invoice_status_sent)
        "paid" -> Color(0xFF2E7D32) to stringResource(R.string.rental_invoice_status_paid)
        "void" -> MaterialTheme.colorScheme.error to stringResource(R.string.rental_invoice_status_void)
        else -> MaterialTheme.colorScheme.outline to status
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (method: String?, notes: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMethod by remember { mutableStateOf<String?>(null) }
    var payNotes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.rental_invoice_mark_paid),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedMethod ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.rental_invoice_payment_method)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    PAYMENT_METHODS.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method) },
                            onClick = {
                                selectedMethod = method
                                expanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = payNotes,
                onValueChange = { payNotes = it },
                label = { Text(stringResource(R.string.rental_invoice_payment_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onConfirm(selectedMethod, payNotes.takeIf { it.isNotBlank() }) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rental_invoice_confirm_pay))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.asset_cancel))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatInvoiceDate(isoString: String): String = try {
    val instant = Instant.parse(isoString)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    instant.atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
} catch (_: Exception) {
    isoString
}

private fun formatCurrency(amount: Double, currency: String): String = try {
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
