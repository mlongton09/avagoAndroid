package com.avago.core.network.model

import kotlinx.serialization.Serializable

/** Wire models for the Rentals API. */

@Serializable
data class CreateRentalRequest(
    val asset_id: String,
    val start_at: String,
    val rate: Double,
    val rate_unit: String,  // "hourly", "daily", "weekly", "monthly"
    val currency: String = "USD",
    val rental_customer_id: String? = null,
    val customer_name: String? = null,
    val notes: String? = null,
)

@Serializable
data class RentalResponse(
    val rental_id: String,
    val asset_id: String,
    val start_at: String,
    val end_at: String? = null,
    val rate: Double,
    val rate_unit: String,
    val currency: String,
    val rental_customer_id: String? = null,
    val customer_name: String? = null,
    val notes: String? = null,
    val status: String,  // "active", "ended", "invoiced", "paid"
    val total_amount: Double? = null,
)

// ---------------------------------------------------------------------------
// Rental Customers
// ---------------------------------------------------------------------------

@Serializable
data class RentalCustomer(
    val rental_customer_id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val company: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val created_at: String? = null,
)

@Serializable
data class RentalCustomersEnvelope(
    val customers: List<RentalCustomer>,
)

@Serializable
data class RentalCustomerEnvelope(
    val customer: RentalCustomer,
)

@Serializable
data class CreateRentalCustomerRequest(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val company: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

// ---------------------------------------------------------------------------
// Rental Periods (updated model for new API fields)
// ---------------------------------------------------------------------------

@Serializable
data class RentalPeriod(
    val rental_period_id: String,
    val asset_id: String,
    val rental_customer_id: String? = null,
    val customer_name: String? = null,
    val rate: Double,
    val rate_unit: String,
    val currency: String,
    val start_at: String,
    val end_at: String? = null,
    val status: String,  // "active", "ended", "invoiced", "paid"
    val notes: String? = null,
)

// ---------------------------------------------------------------------------
// Rental Invoices
// ---------------------------------------------------------------------------

@Serializable
data class InvoiceLineItem(
    val description: String,
    val quantity: Double,
    val unit: String,
    val unit_price: Double,
    val total: Double,
)

@Serializable
data class RentalInvoice(
    val rental_invoice_id: String,
    val invoice_number: String,
    val rental_period_id: String,
    val rental_customer_id: String? = null,
    val asset_id: String,
    val asset_name: String,
    val customer_name: String,
    val start_at: String,
    val end_at: String,
    val line_items: List<InvoiceLineItem> = emptyList(),
    val subtotal: Double,
    val tax_rate: Double = 0.0,
    val tax_amount: Double = 0.0,
    val total_amount: Double,
    val currency: String,
    val status: String,  // "draft", "sent", "paid", "void"
    val due_date: String? = null,
    val paid_at: String? = null,
    val payment_method: String? = null,
    val payment_notes: String? = null,
    val notes: String? = null,
)

@Serializable
data class RentalInvoiceEnvelope(
    val invoice: RentalInvoice,
)

@Serializable
data class RentalInvoicesEnvelope(
    val invoices: List<RentalInvoice>,
)

@Serializable
data class UpdateInvoiceRequest(
    val tax_rate: Double? = null,
    val notes: String? = null,
    val due_date: String? = null,
)

@Serializable
data class PayInvoiceRequest(
    val payment_method: String? = null,
    val payment_notes: String? = null,
)

// ---------------------------------------------------------------------------
// Reservations
// ---------------------------------------------------------------------------

@Serializable
data class RentalReservation(
    val reservation_id: String,
    val asset_id: String,
    val rental_customer_id: String? = null,
    val customer_name: String? = null,
    val reserved_from: String,
    val reserved_until: String,
    val status: String,  // "tentative", "confirmed", "cancelled"
    val notes: String? = null,
)

@Serializable
data class ReservationsEnvelope(
    val reservations: List<RentalReservation>,
)

@Serializable
data class ReservationEnvelope(
    val reservation: RentalReservation,
)

@Serializable
data class CreateReservationRequest(
    val asset_id: String,
    val rental_customer_id: String? = null,
    val reserved_from: String,
    val reserved_until: String,
    val notes: String? = null,
)

@Serializable
data class UpdateReservationRequest(
    val status: String? = null,
    val reserved_from: String? = null,
    val reserved_until: String? = null,
    val notes: String? = null,
)

@Serializable
data class StartReservationRequest(
    val rate: Double,
    val rate_unit: String,
    val currency: String = "USD",
)
