package com.avago.core.sync

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats a chat message timestamp for display in the UI.
 *
 * - Same calendar day as [relativeTo]: shows time only, e.g. "3:45 PM"
 * - Different calendar day: shows month + day + time, e.g. "Jan 5 3:45 PM"
 *
 * Mirrors the iOS ChatTimestampFormatter behaviour for parity.
 */
class ChatTimestampFormatter {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("MMM d h:mm a", Locale.US)

    /**
     * @param date      The timestamp to format.
     * @param relativeTo The reference point (typically "now") used to decide whether
     *                  [date] falls on the same calendar day.
     */
    fun format(date: Date, relativeTo: Date = Date()): String {
        val cal1 = Calendar.getInstance().apply { time = date }
        val cal2 = Calendar.getInstance().apply { time = relativeTo }
        val sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            timeFormat.format(date)
        } else {
            dateTimeFormat.format(date)
        }
    }
}
