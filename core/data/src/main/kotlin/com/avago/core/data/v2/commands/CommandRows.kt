package com.avago.core.data.v2.commands

/** Bookkeeping columns of an overlay-created row. */
internal fun created(nowMillis: Long, userId: String?): Map<String, Any?> = mapOf(
    "created_at" to nowMillis, "updated_at" to nowMillis, "created_by" to userId, "version" to 1L,
)

/** Bookkeeping columns of an overlay-updated row. */
internal fun updated(nowMillis: Long): Map<String, Any?> = mapOf("updated_at" to nowMillis)

/** Trimmed text, null when blank. */
internal fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
