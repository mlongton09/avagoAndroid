package com.avago.core.data.v2.commands

import com.avago.core.data.domain.UserPreferenceChange
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.domain.NotificationChannel as DomainChannel

/** Builders for the caller's own preferences (`core.user_preferences`, `notification_preferences`). */
object PreferenceCommands {

    /**
     * `core.user_preferences.update`: set values are sent as fields, null values in `reset` (back to
     * the account default). The overlay shows the change on the caller's row at once.
     */
    fun updateUserPreferences(userId: String, changes: List<UserPreferenceChange>, nowMillis: Long): V2NewCommand {
        require(changes.isNotEmpty()) { "nothing to change" }
        require(changes.map { it.field }.toSet().size == changes.size) { "a preference is changed twice" }
        var payload = CoreUserPreferencesUpdate()
        val reset = mutableListOf<String>()
        val set = linkedMapOf<String, Any?>()
        for (change in changes) {
            when (change) {
                is UserPreferenceChange.Locale -> change.value.clean()?.let { payload = payload.copy(locale = it) }
                is UserPreferenceChange.Currency -> change.value.clean()?.uppercase()?.let { payload = payload.copy(currency = it) }
                is UserPreferenceChange.DistanceUnit -> change.value?.let {
                    require(it in DISTANCE_UNITS) { "distance unit must be one of $DISTANCE_UNITS" }
                    payload = payload.copy(distanceUnit = it)
                }
                is UserPreferenceChange.FuelVolumeUnit -> change.value?.let {
                    require(it in FUEL_VOLUME_UNITS) { "fuel volume unit must be one of $FUEL_VOLUME_UNITS" }
                    payload = payload.copy(fuelVolumeUnit = it)
                }
                is UserPreferenceChange.DisableQuotes -> change.value?.let { payload = payload.copy(disableQuotes = it) }
                is UserPreferenceChange.EnableHumanInLoop -> change.value?.let { payload = payload.copy(enableHumanInLoop = it) }
            }
            val value = payload.toJson()[change.field]
            if (value == null) reset += change.field
            set[change.field] = value?.let(V2RowOp::fromJsonValue)
        }
        if (reset.isNotEmpty()) payload = payload.copy(reset = reset)
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(V2RowOp.upsert("user_preferences", mapOf("user_id" to userId), set + updated(nowMillis))),
        )
    }

    /** `specialist.notification_preferences.set`, shown on the caller's row at once. */
    fun setNotification(userId: String, eventKey: String, channel: DomainChannel, enabled: Boolean, nowMillis: Long): V2NewCommand {
        val key = eventKey.trim()
        require(key.isNotEmpty()) { "an event key is required" }
        val wire = NotificationChannel.entries.single { it.wire == channel.wire }
        return SpecialistNotificationPreferencesSet(eventKey = key, channel = wire, enabled = enabled).toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "notification_preferences",
                    mapOf("user_id" to userId, "event_key" to key, "channel" to wire.wire),
                    mapOf("enabled" to enabled) + updated(nowMillis),
                ),
            ),
        )
    }

    val DISTANCE_UNITS = setOf("miles", "km")
    val FUEL_VOLUME_UNITS = setOf("gallon", "liter")
}
