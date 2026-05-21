package com.avago.core.sync

data class SyncConflict(
    val queueId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val displayName: String,
    val conflictMessage: String,
)
