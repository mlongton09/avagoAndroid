package com.avago.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.IOException

class TokenRefreshTest {

    private class HttpFailure(val status: Int) : Exception("HTTP $status")

    private var rejected: Throwable? = null

    private suspend fun refreshFailing(failure: Throwable, status: Int? = (failure as? HttpFailure)?.status): Throwable? =
        try {
            TokenRefresh.run<String>(refresh = { throw failure }, onRejected = { rejected = it }, statusOf = { status })
            null
        } catch (e: Throwable) {
            e
        }

    @Test
    fun `successful refresh returns the new tokens without ending the session`() = runTest {
        val result = TokenRefresh.run(refresh = { "tokens" }, onRejected = { rejected = it }, statusOf = { null })
        assertEquals("tokens", result)
        assertNull(rejected)
    }

    @Test
    fun `offline refresh is rethrown and keeps the session`() = runTest {
        val failure = IOException("no network")
        assertSame(failure, refreshFailing(failure))
        assertNull(rejected)
    }

    @Test
    fun `server error or throttling from the refresh endpoint is rethrown and keeps the session`() = runTest {
        for (status in listOf(429, 500, 503)) {
            val failure = HttpFailure(status)
            assertSame(failure, refreshFailing(failure))
            assertNull(rejected, "status $status must not end the session")
        }
    }

    @Test
    fun `rejected refresh token ends the session and yields no tokens`() = runTest {
        for (status in listOf(400, 401, 403)) {
            rejected = null
            val failure = HttpFailure(status)
            val result = TokenRefresh.run<String>(
                refresh = { throw failure },
                onRejected = { rejected = it },
                statusOf = { (it as? HttpFailure)?.status },
            )
            assertNull(result)
            assertSame(failure, rejected)
        }
    }

    @Test
    fun `cancellation is propagated and never ends the session`() = runTest {
        val thrown = refreshFailing(CancellationException("left screen"), status = 401)
        assertInstanceOf(CancellationException::class.java, thrown)
        assertNull(rejected)
    }
}
