package com.avago.feature.workorders.model

enum class WoPriority(val key: String, val displayName: String) {
    CRITICAL("critical", "P1 · Critical"),
    HIGH("high", "P2 · High"),
    MEDIUM("medium", "P3 · Medium"),
    LOW("low", "P4 · Low");

    companion object {
        fun fromKey(key: String?): WoPriority =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}
