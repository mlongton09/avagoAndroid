package com.avago.core.data

/**
 * Global application constants. Mirrors iOS Constants.swift.
 */
object Constants {

    // ── Local broadcast action names ──────────────────────────────────────────
    // Android equivalent of iOS NSNotification names.

    const val ACTION_IDENTITY_READY          = "av.identity_ready"
    const val ACTION_ASSET_DATA_CHANGED      = "av.asset_data_changed"
    const val ACTION_LOG_DATA_CHANGED        = "av.log_data_changed"
    const val ACTION_WO_DATA_CHANGED         = "av.wo_data_changed"
    const val ACTION_INVENTORY_DATA_CHANGED  = "av.inventory_data_changed"
    const val ACTION_PART_DATA_CHANGED       = "av.part_data_changed"
    const val ACTION_SYNC_COMPLETED          = "av.sync_completed"
    const val ACTION_SYNC_CONFLICT_DETECTED  = "av.sync_conflict_detected"
    const val ACTION_ACCOUNT_SWITCHED        = "av.account_switched"
    const val ACTION_SIGN_OUT                = "av.sign_out"
    const val ACTION_PERMISSIONS_CHANGED     = "av.permissions_changed"
    const val ACTION_CONFIG_UPDATED          = "av.config_updated"
    const val ACTION_CHAT_MESSAGE_RECEIVED   = "av.chat_message_received"

    // ── SharedPreferences / DataStore keys ────────────────────────────────────

    const val PREF_THEME             = "av.theme"
    const val PREF_CURRENCY          = "av.currency"
    const val PREF_LOCALE_OVERRIDE   = "av.locale_override"
    const val PREF_FUEL_VOLUME_UNIT  = "av.fuel_volume_unit"
    const val PREF_DISTANCE_UNIT     = "av.distance_unit"
    const val PREF_LAST_ACCOUNT_ID   = "av.last_account_id"
    const val PREF_ONBOARDING_DONE   = "av.onboarding_done"

    // ── Odometer / distance unit keys ─────────────────────────────────────────

    const val UNIT_MILES  = "mi"
    const val UNIT_KM     = "km"
    const val UNIT_HOURS  = "hours"
    const val UNIT_DATE   = "date"

    // ── Photo capture config ──────────────────────────────────────────────────

    const val PHOTO_MAX_DIMENSION = 1920
    const val PHOTO_JPEG_QUALITY  = 0.80f

    // ── Sync config ───────────────────────────────────────────────────────────

    const val SYNC_PAGE_LIMIT           = 1_000
    const val SYNC_TOKEN_REFRESH_SECS   = 60
    const val SYNC_MAX_RETRY_ATTEMPTS   = 3
    const val SYNC_BACKOFF_INITIAL_MS   = 1_000L
    const val SYNC_BACKOFF_MAX_MS       = 30_000L

    // ── App identity ──────────────────────────────────────────────────────────

    const val APP_GROUP_ID         = "com.avago"
    const val KEYCHAIN_SERVICE     = "com.avago.tokens"
    const val KEYCHAIN_ACCOUNT_KEY = "av.auth.account"
}
