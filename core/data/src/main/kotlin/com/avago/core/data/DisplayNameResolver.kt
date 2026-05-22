package com.avago.core.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayNameResolver @Inject constructor(
    private val databaseFactory: DatabaseFactory,
) {
    /**
     * Returns the best display name for [userId] from the local user cache.
     * Priority: displayName > email (part before @) > userId (first 8 chars + "…")
     */
    suspend fun resolve(userId: String, accountId: String): String {
        return try {
            val db = databaseFactory.get(accountId)
            val user = db.userDao().getById(userId)
            when {
                !user?.displayName.isNullOrBlank() -> user!!.displayName!!
                !user?.email.isNullOrBlank() -> user!!.email!!.substringBefore('@')
                userId.length > 8 -> "${userId.take(8)}…"
                else -> userId
            }
        } catch (e: Exception) {
            Timber.w(e, "DisplayNameResolver: failed to resolve userId=$userId")
            userIdFallback(userId)
        }
    }

    /**
     * Synchronous version for use in composables — returns cached value or userId fallback.
     * Prefer [resolve] in coroutine contexts.
     */
    fun resolveSync(userId: String, accountId: String): String {
        return try {
            runBlocking {
                withTimeoutOrNull(100L) {
                    resolve(userId, accountId)
                }
            } ?: userIdFallback(userId)
        } catch (e: Exception) {
            Timber.w(e, "DisplayNameResolver: resolveSync failed for userId=$userId")
            userIdFallback(userId)
        }
    }

    private fun userIdFallback(userId: String): String =
        if (userId.length > 8) "${userId.take(8)}…" else userId
}
