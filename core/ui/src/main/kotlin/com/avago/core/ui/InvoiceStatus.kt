package com.avago.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Localised rental invoice status (web `rental_invoices.status`). Shared by the rentals (P1)
 * order screens and the rentals operations (P2) statements so both show the same label.
 */
@StringRes
fun invoiceStatusLabel(status: String): Int? = when (status) {
    "draft" -> R.string.rentals_invoice_status_draft
    "sent" -> R.string.rentals_invoice_status_sent
    "issued" -> R.string.rentals_invoice_status_issued
    "partially_paid" -> R.string.rentals_invoice_status_partially_paid
    "paid" -> R.string.rentals_invoice_status_paid
    "credited" -> R.string.rentals_invoice_status_credited
    "void" -> R.string.rentals_invoice_status_void
    else -> null
}

/** Label for [status]; unknown values are de-snaked rather than hidden. */
@Composable
fun invoiceStatusText(status: String): String =
    invoiceStatusLabel(status)?.let { stringResource(it) } ?: status.replace('_', ' ')
