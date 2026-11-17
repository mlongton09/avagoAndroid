package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** Operational views of the account's synced orders, not invoice ageing or availability. */
enum class OrderFilter(val status: String? = null) {
    ACTIVE, ALL, OVERDUE, BALANCE_DUE,
    DRAFT(OrderStatus.DRAFT),
    QUOTED(OrderStatus.QUOTED),
    HELD(OrderStatus.HELD),
    RESERVED(OrderStatus.RESERVED),
    ON_RENT(OrderStatus.ON_RENT),
    OFF_RENT_REQUESTED(OrderStatus.OFF_RENT_REQUESTED),
    RETURNED(OrderStatus.RETURNED),
    CLOSED(OrderStatus.CLOSED),
    CANCELLED(OrderStatus.CANCELLED),
}

object OrderWorklist {
    fun overdue(order: OrderView, now: Instant): Boolean =
        order.status in setOf(OrderStatus.ON_RENT, OrderStatus.OFF_RENT_REQUESTED) &&
            order.endAt?.isBefore(now) == true

    /** A quote/draft is not a receivable; a closed order can still have an unpaid balance. */
    fun balanceDue(order: OrderView): Money? = order.balance?.takeIf {
        it.minor > 0 && !order.isDraft &&
            order.status !in setOf(OrderStatus.DRAFT, OrderStatus.QUOTED, OrderStatus.CANCELLED)
    }

    fun canRecordPayment(order: OrderView): Boolean = !order.isLegacy && !order.isDraft

    fun select(orders: List<OrderView>, filter: OrderFilter, query: String, now: Instant): List<OrderView> {
        val search = query.trim()
        val matched = orders.filter { o ->
            val inView = when (filter) {
                OrderFilter.ACTIVE -> o.status !in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED)
                OrderFilter.ALL -> true
                OrderFilter.OVERDUE -> overdue(o, now)
                OrderFilter.BALANCE_DUE -> balanceDue(o) != null
                else -> o.status == filter.status
            }
            inView && (search.isEmpty() || o.number?.contains(search, true) == true ||
                o.customerName?.contains(search, true) == true)
        }
        // Most overdue first; all other worklists retain the established newest-start order.
        return if (filter == OrderFilter.OVERDUE) {
            matched.sortedWith(compareBy<OrderView> { it.endAt }.thenBy { it.id })
        } else {
            matched.sortedWith(compareByDescending<OrderView> { it.startAt ?: Instant.MIN }.thenBy { it.id })
        }
    }

    /** Separate currencies and never count quotes, credits or missing balances as money due. */
    fun balances(orders: List<OrderView>): List<Money> =
        orders.mapNotNull(::balanceDue).groupBy { it.currency }.toSortedMap().map { (currency, amounts) ->
            amounts.fold(Money.zero(currency)) { total, amount -> total + amount }
        }
}

data class OrderWorklistState(
    val accountId: String? = null,
    val orders: List<OrderView> = emptyList(),
)

/** Clear the previous account before waiting for its replacement's database or filters. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeOrderWorklist(
    accounts: Flow<String?>,
    orders: Flow<List<OrderView>>,
    filter: Flow<OrderFilter>,
    query: Flow<String>,
    now: Flow<Instant>,
): Flow<OrderWorklistState> = accounts.flatMapLatest { account ->
    flow {
        emit(OrderWorklistState(account))
        if (account != null) {
            emitAll(combine(orders, filter, query, now) { rows, f, q, at ->
                OrderWorklistState(account, OrderWorklist.select(rows, f, q, at))
            })
        }
    }
}
