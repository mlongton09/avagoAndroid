package com.avago.feature.crew.data

import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult

/** Screen load state for one remote resource. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val error: ApiError) : Load<Nothing>

    fun valueOrNull(): T? = (this as? Ready)?.value
}

fun <T> ApiResult<T>.toLoad(): Load<T> = when (this) {
    is ApiResult.Ok -> Load.Ready(value)
    is ApiResult.Err -> Load.Failed(error)
}

/** One-shot feedback for a snackbar: a success message key or a server error. */
sealed interface Notice {
    data class Done(val messageRes: Int) : Notice
    data class Failed(val error: ApiError) : Notice
}
