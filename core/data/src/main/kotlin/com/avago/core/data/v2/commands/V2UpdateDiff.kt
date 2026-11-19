package com.avago.core.data.v2.commands

import com.avago.core.data.domain.FieldNotClearableException
import java.math.BigDecimal

/**
 * Turns "stored value → edited value" pairs into an update command's `set` values, its typed
 * `clear` set and the matching optimistic overlay columns.
 *
 * - unchanged → not sent;
 * - changed to a value → sent in `set`, overlay column updated;
 * - emptied → added to `clear` when the catalog marks the field clearable, otherwise recorded
 *   and [check] throws [FieldNotClearableException] (nothing is queued).
 *
 * Text is compared trimmed with blank = empty; decimals by value (`1.0` == `1`).
 */
class V2UpdateDiff<C : V2WireEnum>(clearable: Collection<C>) {
    private val clearableByWire = clearable.associateBy { it.wire }
    val clear: MutableSet<C> = linkedSetOf()
    private val notClearable = mutableListOf<String>()

    /** Overlay column → new value (null for cleared columns). */
    val overlay: MutableMap<String, Any?> = linkedMapOf()

    val isEmpty: Boolean get() = overlay.isEmpty() && clear.isEmpty()

    fun text(wire: String, old: String?, new: String?, column: String? = wire): String? =
        field(wire, old.normalized(), new.normalized(), column) { it }

    fun decimal(wire: String, old: BigDecimal?, new: BigDecimal?, column: String? = wire, toColumn: (BigDecimal) -> Any? = { it.toPlainString() }): BigDecimal? =
        field(wire, old, new, column, toColumn)

    fun <T : Any> field(wire: String, old: T?, new: T?, column: String? = wire, toColumn: (T) -> Any? = { it }): T? {
        if (same(old, new)) return null
        if (new == null) {
            val c = clearableByWire[wire]
            if (c == null) {
                notClearable += wire
            } else {
                clear += c
                if (column != null) overlay[column] = null
            }
            return null
        }
        if (column != null) overlay[column] = toColumn(new)
        return new
    }

    fun check() {
        if (notClearable.isNotEmpty()) throw FieldNotClearableException(notClearable.toList())
    }

    private fun same(a: Any?, b: Any?): Boolean =
        if (a is BigDecimal && b is BigDecimal) a.compareTo(b) == 0 else a == b

    private fun String?.normalized(): String? = this?.trim()?.ifEmpty { null }

    companion object {
        /** For commands without clearable fields. */
        fun none(): V2UpdateDiff<Nothing> = V2UpdateDiff(emptyList())
    }
}
