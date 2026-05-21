package com.avago.core.network

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String) : NetworkResult<Nothing>()
    object Unauthorized : NetworkResult<Nothing>()
}

inline fun <T> NetworkResult<T>.onSuccess(block: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) block(data)
    return this
}

inline fun <T> NetworkResult<T>.onError(block: (code: Int, message: String) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Error) block(code, message)
    return this
}

inline fun <T> NetworkResult<T>.onUnauthorized(block: () -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Unauthorized) block()
    return this
}

fun <T> NetworkResult<T>.getOrNull(): T? = (this as? NetworkResult.Success)?.data

fun <T> NetworkResult<T>.getOrThrow(): T = when (this) {
    is NetworkResult.Success -> data
    is NetworkResult.Error -> error("Network error $code: $message")
    is NetworkResult.Unauthorized -> error("Unauthorized")
}
