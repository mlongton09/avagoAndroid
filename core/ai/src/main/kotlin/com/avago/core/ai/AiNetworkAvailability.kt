package com.avago.core.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight network reachability monitor scoped to the AI subsystem.
 *
 * The sync engine has its own connectivity handling; rather than couple the two, the AI
 * subsystem owns a separate monitor — same Android-recommended pattern, no shared state risk.
 *
 * When offline, the Scout composer stays writable but sends are queued; on reconnect the
 * coordinator drains the offline queue with original timestamps preserved.
 *
 * Mirrors iOS NetworkAvailability.swift.
 */
@Singleton
class AiNetworkAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val changed = !_isOnline.value
            _isOnline.value = true
            if (changed) Timber.d("[AiNetworkAvailability] online")
        }

        override fun onLost(network: Network) {
            val changed = _isOnline.value
            _isOnline.value = false
            if (changed) Timber.d("[AiNetworkAvailability] offline")
        }
    }

    init {
        // Seed with current state before registering for updates.
        val cap = connectivityManager.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        _isOnline.value = cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
    }
}
