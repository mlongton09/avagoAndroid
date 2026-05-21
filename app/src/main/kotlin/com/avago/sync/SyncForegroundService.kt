package com.avago.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SyncForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
