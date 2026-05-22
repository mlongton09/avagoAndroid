package com.avago.core.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ToastStyle { Success, Info, Warning, Error }

data class ToastEvent(val message: String, val style: ToastStyle)

@Singleton
class AvagoToast @Inject constructor() {
    private val _events = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ToastEvent> = _events.asSharedFlow()

    fun show(message: String, style: ToastStyle = ToastStyle.Info) {
        _events.tryEmit(ToastEvent(message, style))
    }

    fun error(message: String) = show(message, ToastStyle.Error)
    fun success(message: String) = show(message, ToastStyle.Success)
    fun warning(message: String) = show(message, ToastStyle.Warning)
}
