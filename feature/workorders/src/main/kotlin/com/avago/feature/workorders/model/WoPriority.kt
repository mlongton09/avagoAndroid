package com.avago.feature.workorders.model

enum class WoPriority(val key: String, val displayName: String) {
    CRITICAL("critical", "Critical"),
    HIGH("high", "High"),
    MEDIUM("medium", "Medium"),
    LOW("low", "Low");

    companion object {
        fun fromKey(key: String?): WoPriority =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}
