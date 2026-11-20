package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow

/**
 * Display preferences: the signed-in user's own (synced `user_preferences`), where a null field
 * inherits the account default, or the account defaults themselves (synced `account_preferences`).
 */
data class UserPreferenceOverrides(
    val locale: String? = null,
    val currency: String? = null,
    /** Server code: `miles` or `km`. */
    val distanceUnit: String? = null,
    /** Server code: `gallon` or `liter`. */
    val fuelVolumeUnit: String? = null,
    val disableQuotes: Boolean? = null,
    val enableHumanInLoop: Boolean? = null,
)

/** One preference to change: a value, or null to go back to the account default. */
sealed interface UserPreferenceChange {
    val field: String

    data class Locale(val value: String?) : UserPreferenceChange { override val field = "locale" }
    data class Currency(val value: String?) : UserPreferenceChange { override val field = "currency" }
    data class DistanceUnit(val value: String?) : UserPreferenceChange { override val field = "distance_unit" }
    data class FuelVolumeUnit(val value: String?) : UserPreferenceChange { override val field = "fuel_volume_unit" }
    data class DisableQuotes(val value: Boolean?) : UserPreferenceChange { override val field = "disable_quotes" }
    data class EnableHumanInLoop(val value: Boolean?) : UserPreferenceChange { override val field = "enable_human_in_loop" }
}

enum class NotificationChannel(val wire: String) {
    PUSH("push"),
    EMAIL("email"),
    ;

    companion object {
        fun fromWire(wire: String?): NotificationChannel? = entries.firstOrNull { it.wire == wire }
    }
}

/** One stored opt-in/opt-out of the signed-in user (synced `notification_preferences`). */
data class NotificationPreference(val eventKey: String, val channel: NotificationChannel, val enabled: Boolean)

/**
 * The signed-in user's synced preferences and the account defaults. Writes are `core.user_preferences.update` and
 * `specialist.notification_preferences.set`: applied at once, sent when online.
 */
interface AccountPreferencesRepository {
    /** The user's own overrides; null when nothing is stored (or nobody is signed in). */
    fun observeMine(): Flow<UserPreferenceOverrides?>

    /** The account's defaults that the user's own preferences override; null before the first sync. */
    fun observeAccountDefaults(): Flow<UserPreferenceOverrides?>

    suspend fun update(changes: List<UserPreferenceChange>)

    /** The user's stored notification switches; events without a row use the server default. */
    fun observeNotificationPreferences(): Flow<List<NotificationPreference>>

    suspend fun setNotification(eventKey: String, channel: NotificationChannel, enabled: Boolean)

    /** Whether the user may change their own synced preferences (`settings.edit_display`). */
    fun observeCanEdit(): Flow<Boolean>
}
