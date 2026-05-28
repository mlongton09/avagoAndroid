package com.avago.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Three-way dispatch policy; values mirror the server `dispatch_config.mode` column.
 * Stable raw values so sync round-trips are lossless.
 *
 * Mirrors iOS AVDispatchMode.
 */
enum class DispatchMode(val raw: String) {
    /** Dispatchers assign all work orders. Techs cannot self-claim. */
    CENTRAL("central"),
    /** Both flows enabled: dispatchers push assignments, techs can also claim. */
    HYBRID("hybrid"),
    /** Techs claim their own. Dispatcher-push assignment is refused. */
    SELF_ASSIGN("self_assign");

    /** True when a dispatcher is allowed to push assignments to techs. */
    val allowsDispatcherAssign: Boolean get() = this == CENTRAL || this == HYBRID

    /** True when a tech is allowed to claim an open work order themselves. */
    val allowsTechClaim: Boolean get() = this == HYBRID || this == SELF_ASSIGN

    companion object {
        fun fromRaw(raw: String): DispatchMode =
            entries.firstOrNull { it.raw == raw } ?: HYBRID
    }
}

/**
 * Caches the per-account dispatch mode in SharedPreferences.
 *
 * One scalar per account — cheap enough to skip a Room table. Refreshed from
 * GET /accounts/:id/dispatch-config at sign-in and on account switch; callers
 * read [currentMode] synchronously and tolerate a stale read between refreshes.
 *
 * Mirrors iOS DispatchModeManager.
 */
@Singleton
class DispatchModeManager @Inject constructor(
    @ApplicationContext context: Context,
    private val identityManager: IdentityManager,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("av_dispatch_mode", Context.MODE_PRIVATE)

    private val _modeChanged = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Emits the accountId whose mode just changed. */
    val modeChanged: SharedFlow<String> = _modeChanged.asSharedFlow()

    /** Mode for a specific account. Defaults to [DispatchMode.HYBRID] when unknown. */
    fun mode(forAccountId: String): DispatchMode {
        val raw = prefs.getString(key(forAccountId), null) ?: return DispatchMode.HYBRID
        return DispatchMode.fromRaw(raw)
    }

    /** Mode for the currently active account. */
    fun currentMode(): DispatchMode {
        val accountId = identityManager.activeAccountId.value ?: return DispatchMode.HYBRID
        return mode(accountId)
    }

    /**
     * Set the mode for an account. Emits on [modeChanged] when the value actually changes.
     */
    fun setMode(mode: DispatchMode, forAccountId: String) {
        val k = key(forAccountId)
        val previous = prefs.getString(k, null)
        if (previous == mode.raw) return
        prefs.edit { putString(k, mode.raw) }
        _modeChanged.tryEmit(forAccountId)
    }

    /** Clears the cached mode — call on sign-out. */
    fun clear(accountId: String) {
        prefs.edit { remove(key(accountId)) }
    }

    private fun key(accountId: String) = "av.dispatch_mode.$accountId"
}
