package com.avago.core.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private val RETRY_DELAYS_MS = listOf(75L, 600L, 2200L)

suspend fun <T> withRetry(
    tag: String = "withRetry",
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    for ((attempt, delayMs) in RETRY_DELAYS_MS.withIndex()) {
        try {
            return block()
        } catch (e: Exception) {
            if (!isTransient(e)) throw e
            lastException = e
            Timber.w("$tag attempt ${attempt + 1} failed (${e.javaClass.simpleName}), retrying in ${delayMs}ms")
            delay(delayMs)
        }
    }
    return block()
}

private fun isTransient(e: Exception): Boolean = when (e) {
    is UnknownHostException -> true
    is SocketTimeoutException -> true
    is IOException -> true
    is ServerResponseException -> e.response.status.value in 500..599
    is ClientRequestException -> e.response.status.value == 429
    else -> false
}
