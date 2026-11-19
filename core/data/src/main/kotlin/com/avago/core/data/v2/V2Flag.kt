package com.avago.core.data.v2

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the server reported for `GET /v2/capabilities` (contract design E1). */
enum class ServerV2Capability {
    /** Never probed, or only transient failures so far. */
    UNKNOWN,

    /** The server has no `/v2` API (404/405): a pre-v2 or rolled-back server. */
    LEGACY,

    /** The server's contract range includes [V2Contract.CLIENT_CONTRACT_VERSION]. */
    COMPATIBLE,

    /** `426 upgrade_required`, or our contract version is outside the server's range. */
    INCOMPATIBLE,
}

/** Persistence for [V2Flag]; device-wide, outside every account database. */
interface V2FlagStorage {
    /** Debug-only override: `true`/`false` forces v2 on/off, `null` follows the server. */
    var debugOverride: Boolean?
    var serverCapability: ServerV2Capability
}

class SharedPreferencesV2FlagStorage(private val prefs: SharedPreferences) : V2FlagStorage {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    override var debugOverride: Boolean?
        get() = if (prefs.contains(KEY_DEBUG_OVERRIDE)) prefs.getBoolean(KEY_DEBUG_OVERRIDE, false) else null
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_DEBUG_OVERRIDE) else putBoolean(KEY_DEBUG_OVERRIDE, value)
            }.commit()
        }

    override var serverCapability: ServerV2Capability
        get() = prefs.getString(KEY_SERVER_CAPABILITY, null)
            ?.let { raw -> ServerV2Capability.entries.firstOrNull { it.name == raw } }
            ?: ServerV2Capability.UNKNOWN
        set(value) {
            prefs.edit().putString(KEY_SERVER_CAPABILITY, value.name).commit()
        }

    companion object {
        /** Kept by [LegacyStoreReset]; see docs/v2-reset-spec.md. */
        const val PREFS_NAME = "avago_v2_flags"
        private const val KEY_DEBUG_OVERRIDE = "debug_override"
        private const val KEY_SERVER_CAPABILITY = "server_capability"
    }
}

/**
 * The schema-v2 capability flag. **Default off.**
 *
 * v2 is enabled when either
 *  - a debuggable build sets the debug override (release builds ignore it), or
 *  - this build ships the v2 data layer ([buildSupportsV2]) and the server reported
 *    [ServerV2Capability.COMPATIBLE], or this install already committed to v2 (the reset
 *    marker is complete — a later legacy answer must not silently reopen legacy stores).
 *
 * [buildSupportsV2] stays `false` until the v2 repositories and sync exist, so a server that
 * starts advertising `/v2/capabilities` can never wipe a scaffold-only build's legacy data.
 */
class V2Flag(
    private val storage: V2FlagStorage,
    private val marker: V2ResetMarker,
    private val isDebuggable: Boolean,
    private val buildSupportsV2: Boolean = BUILD_SUPPORTS_V2,
    /** Internal cutover builds (`-Pv2Default=true`): v2 on unless the debug override says off. */
    private val defaultEnabled: Boolean = false,
) {
    private val _updateRequired = MutableStateFlow(computeUpdateRequired())

    /**
     * True when the "service unavailable, update the app" screen must replace the app UI: the
     * server rejects this v2-capable build's contract version, or v2 is enabled and the server
     * has no v2 API. Always false in the default (non-v2) build.
     */
    val updateRequired: StateFlow<Boolean> = _updateRequired.asStateFlow()

    val debugOverride: Boolean? get() = if (isDebuggable) storage.debugOverride else null

    val serverCapability: ServerV2Capability get() = storage.serverCapability

    fun isEnabled(): Boolean = debugOverride
        ?: (defaultEnabled || (buildSupportsV2 && (storage.serverCapability == ServerV2Capability.COMPATIBLE || marker.isComplete)))

    /** Whether the server probe is worth running at all; false keeps the default build inert. */
    fun shouldProbeServer(): Boolean = buildSupportsV2 || defaultEnabled || debugOverride == true

    fun setDebugOverride(value: Boolean?) {
        if (!isDebuggable) return
        storage.debugOverride = value
        _updateRequired.value = computeUpdateRequired()
    }

    /** Records a probe result; [ServerV2Capability.UNKNOWN] (transient failure) keeps the last answer. */
    fun recordServerCapability(capability: ServerV2Capability) {
        if (capability == ServerV2Capability.UNKNOWN) return
        storage.serverCapability = capability
        _updateRequired.value = computeUpdateRequired()
    }

    private fun computeUpdateRequired(): Boolean {
        val enabled = isEnabled()
        if (!enabled && !buildSupportsV2) return false
        return when (storage.serverCapability) {
            // A v2-capable build the server rejects cannot fall back to anything (plan §11).
            ServerV2Capability.INCOMPATIBLE -> enabled || (buildSupportsV2 && debugOverride != false)
            // A v2 build facing a legacy (e.g. rolled-back) server cannot work (plan §11 rollback
            // step 4). A forced debug override tolerates it so the scaffold runs on today's servers.
            ServerV2Capability.LEGACY -> enabled && debugOverride != true
            ServerV2Capability.UNKNOWN, ServerV2Capability.COMPATIBLE -> false
        }
    }

    companion object {
        /** Flip only together with the v2 repositories/sync (plan §7.3, §15.6). */
        const val BUILD_SUPPORTS_V2 = false
    }
}
