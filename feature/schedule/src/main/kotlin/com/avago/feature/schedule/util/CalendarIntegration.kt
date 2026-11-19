package com.avago.feature.schedule.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmTriggerKind

/** Opens the system calendar "add event" UI for a dated occurrence; no-op without a due date. */
fun addOccurrenceToAndroidCalendar(context: Context, occurrence: PmOccurrence, plan: PmPlan?) {
    val dueMs = occurrence.dueAt ?: return
    val title = listOfNotNull(occurrence.assetName, occurrence.planTitle).joinToString(" - ")
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, "PM: $title")
        putExtra(CalendarContract.Events.DESCRIPTION, plan?.description.orEmpty())
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueMs)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        plan?.triggers?.firstOrNull { it.kind == PmTriggerKind.CALENDAR && !it.rrule.isNullOrBlank() }?.rrule?.let {
            putExtra(CalendarContract.Events.RRULE, it.removePrefix("RRULE:"))
        }
    }
    context.startActivity(intent)
}
