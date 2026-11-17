package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.Money
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderWorklistTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private fun order(
        id: String,
        status: String = OrderStatus.ON_RENT,
        due: Instant? = now.minusSeconds(1),
        balance: Long? = 100L,
        currency: String = "EUR",
        source: String? = null,
    ): OrderView = OrderView.of(
        RentalRecordEntity(
            entity = RentalEntity.ORDER, id = id, updatedAt = 0,
            json = buildJsonObject {
                put("id", id); put("number", "R-$id"); put("customer_name", "Fleet Customer")
                put("status", status); put("currency", currency)
                put("start_at", now.minusSeconds(3600).toString())
                due?.let { put("end_at", it.toString()) }
                balance?.let { put("balance_minor", it) }
                source?.let { put("source", it) }
            }.toString(),
        ),
    )

    @Test
    fun `every service lifecycle status has an exact worklist`() {
        val statuses = OrderFilter.entries.mapNotNull { it.status }
        assertEquals(9, statuses.size)
        val orders = statuses.map { order(it, it) } + order("future", "future_status")
        for (filter in OrderFilter.entries.filter { it.status != null }) {
            assertEquals(listOf(filter.status), OrderWorklist.select(orders, filter, "", now).map { it.status })
        }
        assertEquals(orders.size, OrderWorklist.select(orders, OrderFilter.ALL, "", now).size)
        assertEquals(orders.size - 2, OrderWorklist.select(orders, OrderFilter.ACTIVE, "", now).size)
    }

    @Test
    fun `overdue returns include off-rent requests but not returned or missing due dates`() {
        val orders = listOf(
            order("recent"), order("old", due = now.minusSeconds(600)),
            order("off-rent", OrderStatus.OFF_RENT_REQUESTED),
            order("returned", OrderStatus.RETURNED), order("cancelled", OrderStatus.CANCELLED),
            order("reserved", OrderStatus.RESERVED), order("unknown-date", due = null),
            order("exactly-now", due = now), order("future", due = now.plusSeconds(1)),
        )
        assertEquals(
            listOf("old", "off-rent", "recent"),
            OrderWorklist.select(orders, OrderFilter.OVERDUE, "", now).map { it.id },
        )
        assertTrue(OrderWorklist.overdue(orders.first { it.id == "exactly-now" }, now.plusMillis(1)))
    }

    @Test
    fun `balance worklist includes closed unpaid orders without counting quotes credits or absent money`() {
        val rows = listOf(
            order("closed", OrderStatus.CLOSED), order("returned", OrderStatus.RETURNED),
            order("reserved", OrderStatus.RESERVED), order("quoted", OrderStatus.QUOTED),
            order("draft", OrderStatus.DRAFT), order("cancelled", OrderStatus.CANCELLED),
            order("paid", balance = 0), order("credit", balance = -100), order("missing", balance = null),
            order("local-draft").copy(localState = RentalRecordEntity.STATE_DRAFT),
        )
        assertEquals(
            setOf("closed", "returned", "reserved"),
            OrderWorklist.select(rows, OrderFilter.BALANCE_DUE, "", now).map { it.id }.toSet(),
        )
    }

    @Test
    fun `minor unit totals stay exact and separate currencies including zero and three decimal currencies`() {
        val rows = listOf(
            order("eur", balance = 9_007_199_254_740_991L),
            order("cent", balance = 1), order("yen", balance = 123, currency = "JPY"),
            order("dinar", balance = 1001, currency = "KWD"), order("credit", balance = -20),
        )
        assertEquals(
            listOf(Money(9_007_199_254_740_992L, "EUR"), Money(123, "JPY"), Money(1001, "KWD")),
            OrderWorklist.balances(rows),
        )
    }

    @Test
    fun `search intersects status and works across a 488 order account snapshot`() {
        val rows = (1..488).map { order("$it", if (it % 2 == 0) OrderStatus.RESERVED else OrderStatus.ON_RENT) }
        assertEquals(244, OrderWorklist.select(rows, OrderFilter.RESERVED, " fleet CUSTOMER ", now).size)
        assertEquals(listOf("488"), OrderWorklist.select(rows, OrderFilter.RESERVED, " r-488 ", now).map { it.id })
        assertTrue(OrderWorklist.select(rows, OrderFilter.ON_RENT, "r-488", now).isEmpty())
    }

    @Test
    fun `legacy mirrors stay visible but have no modern mutations`() {
        val legacy = order("legacy", source = "legacy")
        assertTrue(legacy.isLegacy)
        assertTrue(legacy.actions.isEmpty())
        assertFalse(OrderWorklist.canRecordPayment(legacy))
        assertFalse(HandoverProgress.mayHandover(legacy, "in"))
        assertEquals(listOf(legacy), OrderWorklist.select(listOf(legacy), OrderFilter.OVERDUE, "", now))
    }

    @Test
    fun `handover actions follow the service status machine`() {
        assertTrue("extend" in order("reserved", OrderStatus.RESERVED).actions)
        for (status in OrderFilter.entries.mapNotNull { it.status }) {
            assertEquals(
                status in setOf(OrderStatus.RESERVED, OrderStatus.ON_RENT),
                HandoverProgress.mayHandover(order("o", status), "out"),
            )
            assertEquals(
                status in setOf(OrderStatus.ON_RENT, OrderStatus.OFF_RENT_REQUESTED),
                HandoverProgress.mayHandover(order("o", status), "in"),
            )
        }
        assertFalse(HandoverProgress.mayHandover(null, "out"))
    }

    @Test
    fun `account switch and logout clear old orders before the next database emits`() = runTest {
        val accounts = MutableStateFlow<String?>("A")
        val feeds = mapOf("A" to MutableSharedFlow<List<OrderView>>(), "B" to MutableSharedFlow<List<OrderView>>())
        val states = mutableListOf<OrderWorklistState>()
        backgroundScope.launch {
            observeOrderWorklist(
                accounts, flow { emitAll(feeds.getValue(requireNotNull(accounts.value))) },
                MutableStateFlow(OrderFilter.ALL), MutableStateFlow(""), MutableStateFlow(now),
            ).collect { states += it }
        }
        runCurrent()
        feeds.getValue("A").emit(listOf(order("a")))
        runCurrent()
        assertEquals(listOf("a"), states.last().orders.map { it.id })
        accounts.value = "B"
        runCurrent()
        assertEquals(OrderWorklistState("B"), states.last())
        feeds.getValue("A").emit(listOf(order("late-a")))
        runCurrent()
        assertEquals(OrderWorklistState("B"), states.last())
        feeds.getValue("B").emit(listOf(order("b")))
        runCurrent()
        assertEquals(listOf("b"), states.last().orders.map { it.id })
        accounts.value = null
        runCurrent()
        assertEquals(OrderWorklistState(), states.last())
    }

    @Test
    fun `partial returns preserve on-rent or off-rent status until rental and accessory lines are complete`() {
        fun line(kind: String, status: String) = buildJsonObject { put("kind", kind); put("status", status) }
        for (status in listOf(OrderStatus.ON_RENT, OrderStatus.OFF_RENT_REQUESTED)) {
            assertEquals(status, HandoverProgress.status(status, "in", listOf(line("rental", "returned"), line("accessory", "out"))))
            assertEquals(status, HandoverProgress.status(status, "in", listOf(line("rental", "open"))))
            assertEquals(status, HandoverProgress.status(status, "in", emptyList()))
            assertEquals(
                OrderStatus.RETURNED,
                HandoverProgress.status(status, "in", listOf(line("rental", "returned"), line("accessory", "cancelled"), line("fee", "open"))),
            )
        }
    }

    @Test
    fun `a line handover cannot change another lines allocation or an unassociated allocation`() {
        val selected = buildJsonObject { put("order_line_id", "selected") }
        val other = buildJsonObject { put("order_line_id", "other") }
        assertTrue(HandoverProgress.affectsAllocation(selected, "selected"))
        assertFalse(HandoverProgress.affectsAllocation(other, "selected"))
        assertFalse(HandoverProgress.affectsAllocation(buildJsonObject {}, "selected"))
    }

    @Test
    fun `lifecycle permissions match the service action permissions`() {
        assertEquals("rental.quote", OrderStatus.permission("quote"))
        for (action in listOf("hold", "reserve", "cancel", "no-show", "extend")) {
            assertEquals("rental.reserve", OrderStatus.permission(action))
        }
        for (action in listOf("checkout", "swap")) {
            assertEquals("rental.checkout", OrderStatus.permission(action))
        }
        for (action in listOf("checkin", "off-rent")) {
            assertEquals("rental.checkin", OrderStatus.permission(action))
        }
        assertEquals("rental.invoice_issue", OrderStatus.permission("close"))
    }

    @Test
    fun `payment configuration is explicit only when the server has actually reported it`() {
        assertNull(OnlinePayments.unavailable(null))
        assertNull(OnlinePayments.unavailable(buildJsonObject {}))
        assertNull(OnlinePayments.unavailable(buildJsonObject { put("online_payments_enabled", true) }))
        assertEquals(
            OnlinePayments.Unavailable.NOT_CONFIGURED,
            OnlinePayments.unavailable(buildJsonObject { put("online_payments_enabled", false); put("reason", "not_configured") }),
        )
        assertEquals(
            OnlinePayments.Unavailable.DISABLED,
            OnlinePayments.unavailable(buildJsonObject { put("online_payments_enabled", false); put("reason", "server_disabled") }),
        )
    }

    @Test
    fun `handover controls reflect remaining lines rather than offering duplicate checkouts`() {
        fun line(status: String) = LineView(
            id = "l", orderId = "o", kind = "rental", classId = null, assetId = "a", description = null,
            qty = 1.0, startAt = null, endAt = null, amount = null, tax = null, status = status,
        )
        val onRent = order("o")
        val out = HandoverProgress.actions(onRent, listOf(line("out")))
        assertTrue("checkin" in out)
        assertFalse("checkout" in out)
        val partialPickup = HandoverProgress.actions(onRent, listOf(line("out"), line("open")))
        assertTrue("checkout" in partialPickup && "checkin" in partialPickup)
        assertFalse("checkin" in HandoverProgress.actions(onRent, listOf(line("returned"))))
        assertTrue(HandoverProgress.actions(onRent.copy(source = "legacy"), listOf(line("out"))).isEmpty())
    }
}
