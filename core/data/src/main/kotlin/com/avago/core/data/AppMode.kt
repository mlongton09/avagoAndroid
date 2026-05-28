package com.avago.core.data

/**
 * App operational mode. Mirrors iOS AppMode.swift.
 *
 * On iOS this is determined by a build-time Info.plist flag (AVAppMode).
 * On Android it is determined at runtime by [ConnectivityMonitor] so the
 * app can transition seamlessly without a restart.
 */
enum class AppMode {
    /** No network — all reads/writes from local Room DB; mutations queued for sync. */
    OFFLINE,

    /** Network available — [SyncEngine] is active and may push/pull deltas. */
    CONNECTED,
}
