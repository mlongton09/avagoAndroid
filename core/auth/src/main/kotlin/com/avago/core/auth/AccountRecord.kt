package com.avago.core.auth

import kotlinx.serialization.Serializable

@Serializable
data class AccountRecord(
    val accountId: String,
    val userId: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val role: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
