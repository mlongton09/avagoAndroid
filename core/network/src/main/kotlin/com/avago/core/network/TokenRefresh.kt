package com.avago.core.network

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

/**
 * Outcome policy for `POST /auth/refresh`, used by the bearer `refreshTokens` block.
 *
 * Only an explicit rejection of the refresh token (400/401/403 from the refresh endpoint) ends
 * the session: the stored tokens are cleared and [onRejected] runs (silent re-auth, then
 * sign-out, which deletes the account database). Any other failure — offline, timeout, DNS,
 * 5xx, 429, an unreadable body — is rethrown unchanged, so the original request fails as a
 * transport/server error. Callers already treat those as "retry later" (outboxes keep the row
 * pending), and the tokens stay valid for the next attempt. Cancellation is always rethrown.
 */
internal object TokenRefresh {

    /** Statuses from the refresh endpoint that mean the refresh token itself is no longer valid. */
    fun isSessionRejected(status: Int?): Boolean = status == 400 || status == 401 || status == 403

    suspend fun <T : Any> run(
        refresh: suspend () -> T,
        onRejected: suspend (Throwable) -> Unit,
        statusOf: (Throwable) -> Int? = { (it as? ResponseException)?.response?.status?.value },
    ): T? = try {
        refresh()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (isSessionRejected(statusOf(e))) {
            onRejected(e)
            null
        } else {
            throw e
        }
    }
}
