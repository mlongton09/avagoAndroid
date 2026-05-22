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
    val memberships: List<AccountMembership> = emptyList(),
    val isAnonymous: Boolean = false,
)

@Serializable
data class AccountMembership(
    val accountId: String,
    val role: String,
    val isRoot: Boolean = false,
)
