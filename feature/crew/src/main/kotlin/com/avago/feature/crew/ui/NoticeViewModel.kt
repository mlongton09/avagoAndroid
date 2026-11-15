package com.avago.feature.crew.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiResult
import com.avago.feature.crew.data.Notice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared busy flag and snackbar notices for the crew screens. */
abstract class NoticeViewModel : ViewModel() {
    protected val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    protected val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices

    /** Runs one mutation at a time; reports [doneRes] on success or the server error on failure. */
    protected fun <T> act(doneRes: Int?, call: suspend () -> ApiResult<T>, onOk: suspend (T) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val r = call()) {
                    is ApiResult.Ok -> {
                        onOk(r.value)
                        doneRes?.let { _notices.emit(Notice.Done(it)) }
                    }
                    is ApiResult.Err -> _notices.emit(Notice.Failed(r.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }
}
