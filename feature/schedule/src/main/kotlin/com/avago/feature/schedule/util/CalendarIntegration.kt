package com.avago.feature.schedule.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.avago.core.data.db.entity.ScheduleEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Launches the system calendar "add event" UI pre-populated with schedule details.
 *
 * The due date is derived from [ScheduleEntity.nextDueAt] when present; if absent the
 * function returns without launching an intent.
 */
fun addScheduleToAndroidCalendar(
    context: Context,
    schedule: ScheduleEntity,
    assetName: String,
) {
    val dueMs: Long = computeDueDateMs(schedule) ?: return

    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(
            CalendarContract.Events.TITLE,
            "PM: $assetName - ${schedule.category ?: schedule.title}",
        )
        putExtra(CalendarContract.Events.DESCRIPTION, schedule.category ?: "")
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueMs)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        if (!schedule.rrule.isNullOrBlank()) {
            putExtra(CalendarContract.Events.RRULE, schedule.rrule)
        }
    }

    context.startActivity(intent)
}

/**
 * Returns the epoch-millisecond timestamp to use as the calendar event begin time.
 *
 * Priority order:
 * 1. [ScheduleEntity.nextDueAt] (pre-computed by the server / sync)
 * 2. For date-based schedules with an RRULE: next occurrence from today
 * 3. `null` — caller should not launch the intent
 */
private fun computeDueDateMs(schedule: ScheduleEntity): Long? {
    schedule.nextDueAt?.let { return it }

    val rrule = schedule.rrule ?: return null
    val next: LocalDate = RruleHelper.nextOccurrence(rrule, LocalDate.now()) ?: return null
    return next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

