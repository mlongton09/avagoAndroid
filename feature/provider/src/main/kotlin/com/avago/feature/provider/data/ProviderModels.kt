package com.avago.feature.provider.data

import com.avago.core.network.json.bool
import com.avago.core.network.json.double
import com.avago.core.network.json.int
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.long
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong

// Provider workspace (feature C) payloads; see AvagoSvc docs/provider-workspace.md.

data class Profile(val businessName: String, val currency: String) {
    companion object {
        fun from(o: JsonObject) = Profile(o.str("business_name").orEmpty(), o.str("currency") ?: DEFAULT_CURRENCY)
    }
}

internal const val DEFAULT_CURRENCY = "USD"

data class DashJob(
    val id: String,
    val number: String,
    val status: String,
    val title: String?,
    val scheduledFor: String?,
    val customerName: String?,
    val assetName: String?,
)

data class DashDoc(
    val id: String,
    val number: String,
    val status: String?,
    val totalMinor: Long,
    val currency: String,
    val customerName: String?,
    /** valid_until for quotes, due_date for invoices. */
    val date: String?,
)

data class DashCustomer(val id: String, val name: String, val linked: Boolean)

data class Dashboard(
    val todayJobs: List<DashJob>,
    val openQuotes: List<DashDoc>,
    val unpaidInvoices: List<DashDoc>,
    val unpaidTotalMinor: Long,
    val unpaidCount: Int,
    val currency: String,
    val customerCount: Int,
    val linkedCustomerCount: Int,
    val assetsServiced: Int,
    val recentCustomers: List<DashCustomer>,
    val remindersDue: Int,
) {
    companion object {
        fun from(o: JsonObject): Dashboard {
            val cur = o.str("currency") ?: DEFAULT_CURRENCY
            fun doc(d: JsonObject, dateKey: String): DashDoc? {
                val id = d.str("id") ?: return null
                return DashDoc(id, d.str("number").orEmpty(), d.str("status"), d.long("total_minor") ?: 0, d.str("currency") ?: cur, d.str("customer_name"), d.str(dateKey))
            }
            return Dashboard(
                todayJobs = o.objs("today_jobs").mapNotNull { j ->
                    val id = j.str("id") ?: return@mapNotNull null
                    DashJob(id, j.str("number").orEmpty(), j.str("status") ?: JobStatus.SCHEDULED, j.str("title"), j.str("scheduled_for"), j.str("customer_name"), j.str("asset_name"))
                },
                openQuotes = o.objs("open_quotes").mapNotNull { doc(it, "valid_until") },
                unpaidInvoices = o.objs("unpaid_invoices").mapNotNull { doc(it, "due_date") },
                unpaidTotalMinor = o.long("unpaid_total_minor") ?: 0,
                unpaidCount = o.int("unpaid_count") ?: 0,
                currency = cur,
                customerCount = o.int("customer_count") ?: 0,
                linkedCustomerCount = o.int("linked_customer_count") ?: 0,
                assetsServiced = o.int("assets_serviced") ?: 0,
                recentCustomers = o.objs("recent_customers").mapNotNull { c ->
                    val id = c.str("id") ?: return@mapNotNull null
                    DashCustomer(id, c.str("name").orEmpty(), c.bool("linked") == true)
                },
                remindersDue = (o["reminders_due"] as? JsonArray)?.size ?: o.int("reminders_due") ?: 0,
            )
        }
    }
}

data class Customer(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val notes: String?,
    val linked: Boolean,
    val assetCount: Int?,
    val connectionStatus: String?,
) {
    companion object {
        fun from(o: JsonObject): Customer? {
            val id = o.str("id") ?: return null
            return Customer(
                id = id,
                name = o.str("name").orEmpty(),
                email = o.str("email"),
                phone = o.str("phone"),
                notes = o.str("notes"),
                linked = o.bool("linked") ?: (o.str("owner_account_id") != null),
                assetCount = o.int("asset_count"),
                connectionStatus = o.str("connection_status"),
            )
        }
    }
}

data class CustomerAsset(
    val id: String,
    val customerId: String?,
    val name: String,
    val serviceType: String,
    val make: String?,
    val model: String?,
    val year: Int?,
    val vinSerial: String?,
    val meterUnit: String?,
    val lastMeter: Double?,
    val ownerAssetId: String?,
) {
    /** "2019 Cat 320" style summary, or null when nothing is known. */
    val summary: String? get() = listOfNotNull(year?.toString(), make, model).joinToString(" ").ifBlank { null }

    companion object {
        fun from(o: JsonObject): CustomerAsset? {
            val id = o.str("id") ?: return null
            return CustomerAsset(
                id = id,
                customerId = o.str("customer_id"),
                name = o.str("name").orEmpty(),
                serviceType = o.str("service_type") ?: "other",
                make = o.str("make"),
                model = o.str("model"),
                year = o.int("year"),
                vinSerial = o.str("vin_serial"),
                meterUnit = o.str("meter_unit"),
                lastMeter = o.double("last_meter"),
                ownerAssetId = o.str("owner_asset_id"),
            )
        }
    }
}

data class DocLine(
    val kind: String,
    val description: String,
    val quantity: Double,
    val unitPriceMinor: Long,
    val taxable: Boolean,
) {
    val amountMinor: Long get() = (quantity * unitPriceMinor).roundToLong()

    companion object {
        fun from(o: JsonObject): DocLine? {
            val desc = o.str("description") ?: return null
            val qty = (o["quantity"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() } ?: 1.0
            return DocLine(o.str("kind") ?: "fee", desc, qty, o.long("unit_price_minor") ?: 0, o.bool("taxable") ?: true)
        }

        fun list(o: JsonObject): List<DocLine> = o.objs("lines").mapNotNull(::from)
    }
}

data class Totals(val subtotalMinor: Long, val taxMinor: Long, val totalMinor: Long, val labourMinor: Long, val partsMinor: Long) {
    companion object {
        fun from(o: JsonObject?): Totals? = o?.let {
            Totals(it.long("subtotal_minor") ?: 0, it.long("tax_minor") ?: 0, it.long("total_minor") ?: 0, it.long("labour_minor") ?: 0, it.long("parts_minor") ?: 0)
        }
    }
}

data class Quote(
    val id: String,
    val number: String,
    val status: String,
    val title: String?,
    val notes: String?,
    val lines: List<DocLine>,
    val subtotalMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val currency: String,
    val validUntil: String?,
    val sentAt: String?,
    val declineReason: String?,
    val jobId: String?,
    val customerId: String?,
    val customerName: String?,
) {
    /** Drafts and already-sent quotes can be (re)sent while they are still open. */
    val canSend: Boolean get() = status == "draft" || status == "sent"

    companion object {
        val STATUSES = listOf("draft", "sent", "approved", "declined", "expired", "converted")

        fun from(o: JsonObject): Quote? {
            val id = o.str("id") ?: return null
            return Quote(
                id, o.str("number").orEmpty(), o.str("status") ?: "draft", o.str("title"), o.str("notes"), DocLine.list(o),
                o.long("subtotal_minor") ?: 0, o.long("tax_minor") ?: 0, o.long("total_minor") ?: 0, o.str("currency") ?: DEFAULT_CURRENCY,
                o.str("valid_until"), o.str("sent_at"), o.str("decline_reason"), o.str("job_id"), o.str("customer_id"), o.str("customer_name"),
            )
        }
    }
}

data class Job(
    val id: String,
    val number: String,
    val status: String,
    val title: String?,
    val description: String?,
    val notes: String?,
    val scheduledFor: String?,
    val startedAt: String?,
    val completedAt: String?,
    val labourHours: Double?,
    val meterReading: Double?,
    val meterUnit: String?,
    val lines: List<DocLine>,
    val invoiceId: String?,
    val customerId: String?,
    val customerAssetId: String?,
    val customerName: String?,
) {
    val nextStatuses: List<String> get() = JobStatus.next(status)
    val canInvoice: Boolean get() = status == JobStatus.COMPLETED && invoiceId == null

    companion object {
        fun from(o: JsonObject): Job? {
            val id = o.str("id") ?: return null
            return Job(
                id, o.str("number").orEmpty(), o.str("status") ?: JobStatus.SCHEDULED, o.str("title"), o.str("description"), o.str("notes"),
                o.str("scheduled_for"), o.str("started_at"), o.str("completed_at"), o.double("labour_hours"), o.double("meter_reading"),
                o.str("meter_unit"), DocLine.list(o), o.str("invoice_id"), o.str("customer_id"), o.str("customer_asset_id"), o.str("customer_name"),
            )
        }
    }
}

data class Invoice(
    val id: String,
    val number: String,
    val status: String,
    val notes: String?,
    val lines: List<DocLine>,
    val subtotalMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val currency: String,
    val dueDate: String?,
    val sentAt: String?,
    val paidAt: String?,
    val paidMethod: String?,
    val paidReference: String?,
    val jobId: String?,
    val customerId: String?,
    val customerName: String?,
) {
    val canSend: Boolean get() = (status == "draft" || status == "sent") && totalMinor > 0
    val canMarkPaid: Boolean get() = status == "draft" || status == "sent"

    companion object {
        val STATUSES = listOf("draft", "sent", "paid", "void")

        fun from(o: JsonObject): Invoice? {
            val id = o.str("id") ?: return null
            return Invoice(
                id, o.str("number").orEmpty(), o.str("status") ?: "draft", o.str("notes"), DocLine.list(o),
                o.long("subtotal_minor") ?: 0, o.long("tax_minor") ?: 0, o.long("total_minor") ?: 0, o.str("currency") ?: DEFAULT_CURRENCY,
                o.str("due_date"), o.str("sent_at"), o.str("paid_at"), o.str("paid_method"), o.str("paid_reference"),
                o.str("job_id"), o.str("customer_id"), o.str("customer_name"),
            )
        }
    }
}

data class CustomerDetail(
    val customer: Customer,
    val assets: List<CustomerAsset>,
    val connectionStatus: String?,
    val quotes: List<Quote>,
    val jobs: List<Job>,
    val invoices: List<Invoice>,
) {
    companion object {
        fun from(o: JsonObject): CustomerDetail? {
            val c = o.obj("customer")?.let(Customer::from) ?: return null
            val linked = o.bool("linked") ?: c.linked
            return CustomerDetail(
                customer = c.copy(linked = linked),
                assets = o.objs("assets").mapNotNull(CustomerAsset::from),
                connectionStatus = o.obj("connection")?.str("status"),
                quotes = o.objs("quotes").mapNotNull(Quote::from),
                jobs = o.objs("jobs").mapNotNull(Job::from),
                invoices = o.objs("invoices").mapNotNull(Invoice::from),
            )
        }
    }
}

data class JobPhoto(val id: String, val url: String?)

data class JobDetail(
    val job: Job,
    val customer: Customer?,
    val asset: CustomerAsset?,
    val photos: List<JobPhoto>,
    val lineTotals: Totals?,
    val currency: String,
) {
    companion object {
        fun from(o: JsonObject, currency: String): JobDetail? {
            val job = o.obj("job")?.let(Job::from) ?: return null
            val customer = o.obj("customer")?.let(Customer::from)
            return JobDetail(
                job = job.copy(customerName = job.customerName ?: customer?.name),
                customer = customer,
                asset = o.obj("asset")?.let(CustomerAsset::from),
                photos = o.objs("photos").mapNotNull { p -> p.str("photo_id")?.let { JobPhoto(it, p.str("download_url")) } },
                lineTotals = Totals.from(o.obj("line_totals")),
                currency = currency,
            )
        }
    }
}

data class QuoteDetail(val quote: Quote, val customer: Customer?, val totals: Totals) {
    companion object {
        fun from(o: JsonObject): QuoteDetail? {
            val q = o.obj("quote")?.let(Quote::from) ?: return null
            val c = o.obj("customer")?.let(Customer::from)
            return QuoteDetail(
                q.copy(customerName = q.customerName ?: c?.name),
                c,
                Totals.from(o.obj("totals")) ?: Totals(q.subtotalMinor, q.taxMinor, q.totalMinor, 0, 0),
            )
        }
    }
}

data class InvoiceDetail(val invoice: Invoice, val customer: Customer?, val totals: Totals, val onlinePaymentAvailable: Boolean) {
    companion object {
        fun from(o: JsonObject): InvoiceDetail? {
            val i = o.obj("invoice")?.let(Invoice::from) ?: return null
            val c = o.obj("customer")?.let(Customer::from)
            return InvoiceDetail(
                i.copy(customerName = i.customerName ?: c?.name),
                c,
                Totals.from(o.obj("totals")) ?: Totals(i.subtotalMinor, i.taxMinor, i.totalMinor, 0, 0),
                o.bool("online_payment_available") == true,
            )
        }
    }
}

/** Result of a send call: a customer link to share, and whether the server e-mailed it. */
data class SendResult(val linkUrl: String?, val emailed: Boolean) {
    companion object {
        fun from(o: JsonObject) = SendResult(o.str("link_url") ?: o.str("url"), o.bool("emailed") == true)
    }
}

/** Job lifecycle; mirrors `job_transition` in the backend domain. */
object JobStatus {
    const val SCHEDULED = "scheduled"
    const val IN_PROGRESS = "in_progress"
    const val ON_HOLD = "on_hold"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
    val ALL = listOf(SCHEDULED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED)

    fun next(from: String): List<String> = when (from) {
        SCHEDULED -> listOf(IN_PROGRESS, COMPLETED, CANCELLED)
        IN_PROGRESS -> listOf(ON_HOLD, COMPLETED, CANCELLED)
        ON_HOLD -> listOf(IN_PROGRESS, CANCELLED)
        else -> emptyList()
    }
}

val SERVICE_TYPES = listOf("auto", "diesel", "marine", "small_engine", "hydraulics", "calibration", "equipment", "other")
val METER_UNITS = listOf("hours", "mi", "km")

/** Parses a non-negative decimal typed with either '.' or ',' as the separator. */
internal fun parseDecimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

data class CustomerForm(val name: String = "", val email: String = "", val phone: String = "", val notes: String = "") {
    /** Field key to error code (`required`, `email`). */
    fun errors(): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "required")
        if (email.isNotBlank() && !EMAIL.matches(email.trim())) put("email", "email")
    }

    fun toBody(): JsonObject = jsonBody(
        "name" to name.trim(),
        "email" to email.trim().ifBlank { null },
        "phone" to phone.trim().ifBlank { null },
        "notes" to notes.trim().ifBlank { null },
    )

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

data class AssetForm(
    val name: String = "",
    val serviceType: String = "equipment",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val vinSerial: String = "",
) {
    fun errors(): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "required")
        if (year.isNotBlank() && year.trim().toIntOrNull()?.takeIf { it in 1900..2100 } == null) put("year", "year")
    }

    fun toBody(): JsonObject = jsonBody(
        "name" to name.trim(),
        "service_type" to serviceType,
        "make" to make.trim().ifBlank { null },
        "model" to model.trim().ifBlank { null },
        "year" to year.trim().toIntOrNull(),
        "vin_serial" to vinSerial.trim().ifBlank { null },
    )
}

/** Status change with the optional completion details the backend accepts. */
data class StatusForm(
    val status: String,
    val labourHours: String = "",
    val meterReading: String = "",
    val meterUnit: String = "hours",
    val notes: String = "",
) {
    fun errors(): Map<String, String> = buildMap {
        if (labourHours.isNotBlank() && parseDecimal(labourHours) == null) put("labour_hours", "number")
        if (meterReading.isNotBlank() && parseDecimal(meterReading) == null) put("meter_reading", "number")
    }

    fun toBody(): JsonObject {
        val meter = parseDecimal(meterReading)
        return jsonBody(
            "status" to status,
            "labour_hours" to parseDecimal(labourHours),
            "meter_reading" to meter,
            "meter_unit" to meterUnit.takeIf { meter != null },
            "notes" to notes.trim().ifBlank { null },
        )
    }

    companion object {
        fun forJob(job: Job, to: String) = StatusForm(
            status = to,
            labourHours = job.labourHours?.let(::plain).orEmpty(),
            meterReading = job.meterReading?.let(::plain).orEmpty(),
            meterUnit = job.meterUnit?.takeIf { it in METER_UNITS } ?: "hours",
        )

        private fun plain(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    }
}

internal fun photoIdFrom(o: JsonObject): String? = o.obj("photo")?.str("photo_id") ?: o.str("photo_id")
