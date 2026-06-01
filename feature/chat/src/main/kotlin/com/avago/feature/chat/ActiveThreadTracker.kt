package com.avago.feature.chat

object ActiveThreadTracker {
    @Volatile
    var activeThreadId: String? = null
}
