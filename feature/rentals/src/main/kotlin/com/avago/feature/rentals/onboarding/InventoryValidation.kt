package com.avago.feature.rentals.onboarding

import java.util.Locale

private const val MAX_SERIAL_BYTES = 200

fun validInventory(mode: String, quantities: List<Int>): Boolean =
    mode in listOf("same", "different") && quantities.size in 1..MAX_INVENTORY &&
        (mode != "same" || quantities.size == 1) &&
        quantities.all { it in 1..MAX_INVENTORY } && quantities.sumOf(Int::toLong) in 1L..MAX_INVENTORY.toLong()

fun validSerials(required: Boolean, quantity: Int, serials: List<String>): Boolean =
    (!required && serials.isEmpty() || serials.size == quantity) &&
        serials.all { it.isNotBlank() && bounded(it, MAX_SERIAL_BYTES) } &&
        serials.map { it.trim().lowercase(Locale.ROOT) }.distinct().size == serials.size
