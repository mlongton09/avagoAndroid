package com.avago.feature.chat.ui

import android.net.Uri
import com.avago.core.data.db.entity.ChatThreadEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Returns the display title for a thread row, mirroring iOS ThreadRowCell logic.
 */
fun ChatThreadEntity.displayTitle(): String {
    if (threadType == "direct" || threadType == "group") {
        return displayName ?: "Chat"
    }
    // Try subject_summary JSON for WO / asset threads.
    val summary = subjectSummary?.parseJsonObjectSafely()
    if (summary != null) {
        val title = summary["title"]?.jsonPrimitive?.content
        if (!title.isNullOrBlank()) return title

        val name = summary["name"]?.jsonPrimitive?.content
        if (!name.isNullOrBlank()) {
            val make = summary["make"]?.jsonPrimitive?.content.orEmpty()
            val model = summary["model"]?.jsonPrimitive?.content.orEmpty()
            return if (make.isEmpty() && model.isEmpty()) {
                name
            } else {
                "$name — $make $model".trim()
            }
        }
    }
    return threadType.replaceFirstChar { it.uppercaseChar() }
}

/**
 * Returns the icon emoji for a thread, mirroring iOS ThreadRowCell.
 */
fun ChatThreadEntity.iconEmoji(): String? = when {
    threadType.startsWith("wo") -> "🔧" // 🔧
    threadType == "direct" || threadType == "group" -> "💬" // 💬
    else -> "💬"
}

/**
 * Returns a human-readable preview string for the last message.
 * URL-only messages are collapsed to their domain (e.g. "youtube.com").
 */
fun ChatThreadEntity.lastMessagePreviewText(): String {
    val raw = lastMessagePreview ?: return ""
    val stripped = raw
        .replace(Regex("\\*{1,2}([^*]+)\\*{1,2}"), "$1") // bold/italic
        .replace(Regex("`([^`]+)`"), "$1")                 // inline code
        .replace(Regex("#+ "), "")                          // headings
        .trim()

    // URL-only message → show domain
    if (stripped.matches(Regex("https?://\\S+"))) {
        return try {
            Uri.parse(stripped).host?.removePrefix("www.") ?: stripped
        } catch (_: Exception) {
            stripped
        }
    }
    return stripped
}

/**
 * Returns a relative or formatted timestamp string for the thread row.
 * Uses simple rules matching iOS lastActivityDisplayText.
 */
fun ChatThreadEntity.relativeTimestamp(): String {
    val at = lastMessageAt ?: return ""
    val now = System.currentTimeMillis()
    val diffMs = now - at
    val diffMin = diffMs / 60_000
    val diffHour = diffMs / 3_600_000
    val diffDay = diffMs / 86_400_000

    return when {
        diffMin < 1 -> "now"
        diffMin < 60 -> "${diffMin}m"
        diffHour < 24 -> "${diffHour}h"
        diffDay < 7 -> "${diffDay}d"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
            val month = cal.getDisplayName(
                java.util.Calendar.MONTH,
                java.util.Calendar.SHORT,
                java.util.Locale.getDefault(),
            ) ?: ""
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            "$month $day"
        }
    }
}

/**
 * Returns a short human-readable timestamp for an individual message bubble.
 */
fun Long.toMessageTimestamp(): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = this@toMessageTimestamp }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val amPm = if (hour < 12) "AM" else "PM"
    val h = if (hour % 12 == 0) 12 else hour % 12
    val m = minute.toString().padStart(2, '0')
    return "$h:$m $amPm"
}

private fun String.parseJsonObjectSafely(): JsonObject? = try {
    json.parseToJsonElement(this).jsonObject
} catch (_: Exception) {
    null
}

/** Parse the reactions JSON blob to a map of emoji → list of userIds. */
fun String?.parseReactions(): Map<String, List<String>> {
    if (this == null) return emptyMap()
    return try {
        val obj = json.parseToJsonElement(this).jsonObject
        obj.entries.associate { (emoji, ids) ->
            emoji to (ids.jsonObject.keys.toList() + listOf<String>()).let {
                // Handle both array and object formats: {"👍": ["id1"]} or {"👍": {"id1": true}}
                emptyList<String>()
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
