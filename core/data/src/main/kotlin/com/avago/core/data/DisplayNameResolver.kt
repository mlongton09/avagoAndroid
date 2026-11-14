package com.avago.core.data

import kotlinx.coroutines.CancellationException
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
                !user?.displayName.isNullOrBlank() -> user?.displayName.orEmpty()
                !user?.email.isNullOrBlank() -> user?.email.orEmpty().substringBefore('@')
                userId.length > 8 -> "${userId.take(8)}…"
                else -> userId
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "DisplayNameResolver: failed to resolve userId=$userId")
            userIdFallback(userId)
        }
    }

    private fun userIdFallback(userId: String): String =
        if (userId.length > 8) "${userId.take(8)}…" else userId
}
