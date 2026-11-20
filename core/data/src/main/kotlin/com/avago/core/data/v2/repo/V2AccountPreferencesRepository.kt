package com.avago.core.data.v2.repo

import com.avago.core.data.domain.AccountPreferencesRepository
import com.avago.core.data.domain.NotificationChannel
import com.avago.core.data.domain.NotificationPreference
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.UserPreferenceChange
import com.avago.core.data.domain.UserPreferenceOverrides
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.commands.PreferenceCommands
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2AccountPreferencesRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : AccountPreferencesRepository {

    override fun observeMine(): Flow<UserPreferenceOverrides?> =
        store.observe(setOf(USER_PREFERENCES), null) { conn -> commands.currentUserId()?.let { mine(conn, it) } }

    override fun observeAccountDefaults(): Flow<UserPreferenceOverrides?> =
        store.observe(setOf(ACCOUNT_PREFERENCES), null) { conn -> accountDefaults(conn) }

    override suspend fun update(changes: List<UserPreferenceChange>) {
        val userId = commands.currentUserId() ?: throw StoreNotReadyException()
        commands.submit(PreferenceCommands.updateUserPreferences(userId, changes, System.currentTimeMillis()))
    }

    override fun observeNotificationPreferences(): Flow<List<NotificationPreference>> =
        store.observe(setOf(NOTIFICATION_PREFERENCES), emptyList()) { conn ->
            commands.currentUserId()?.let { notifications(conn, it) }.orEmpty()
        }

    override suspend fun setNotification(eventKey: String, channel: NotificationChannel, enabled: Boolean) {
        val userId = commands.currentUserId() ?: throw StoreNotReadyException()
        commands.submit(PreferenceCommands.setNotification(userId, eventKey, channel, enabled, System.currentTimeMillis()))
    }

    override fun observeCanEdit(): Flow<Boolean> =
        store.observe(setOf(EFFECTIVE_PERMISSIONS), false) { conn -> canEdit(conn) }

    companion object {
        private const val USER_PREFERENCES = "user_preferences"
        private const val ACCOUNT_PREFERENCES = "account_preferences"
        private const val NOTIFICATION_PREFERENCES = "notification_preferences"
        private const val EFFECTIVE_PERMISSIONS = "effective_permissions"
        const val EDIT_PERMISSION = "settings.edit_display"

        fun canEdit(conn: V2Connection): Boolean =
            conn.query(
                "SELECT allowed FROM effective_permissions WHERE permission_key = ?",
                listOf(EDIT_PERMISSION),
            ).firstOrNull()?.flag("allowed") == true

        fun mine(conn: V2Connection, userId: String): UserPreferenceOverrides? =
            V2OverlayMerge.query(conn, USER_PREFERENCES, "user_id = ?", listOf(userId)) { it["user_id"] == userId }
                .firstOrNull()
                ?.let(::preferences)

        /** The singleton `account_preferences` row; only the server writes it. */
        fun accountDefaults(conn: V2Connection): UserPreferenceOverrides? =
            conn.query("SELECT * FROM $ACCOUNT_PREFERENCES").firstOrNull()?.let(::preferences)

        private fun preferences(r: V2Row) = UserPreferenceOverrides(
            locale = r.strOrNull("locale"),
            currency = r.strOrNull("currency"),
            distanceUnit = r.strOrNull("distance_unit"),
            fuelVolumeUnit = r.strOrNull("fuel_volume_unit"),
            disableQuotes = r.flag("disable_quotes"),
            enableHumanInLoop = r.flag("enable_human_in_loop"),
        )

        fun notifications(conn: V2Connection, userId: String): List<NotificationPreference> =
            V2OverlayMerge.query(conn, NOTIFICATION_PREFERENCES, "user_id = ?", listOf(userId)) { it["user_id"] == userId }
                .mapNotNull { r ->
                    val channel = NotificationChannel.fromWire(r.strOrNull("channel")) ?: return@mapNotNull null
                    NotificationPreference(r.str("event_key"), channel, r.flag("enabled") ?: return@mapNotNull null)
                }
                .sortedWith(compareBy({ it.eventKey }, { it.channel }))

        private fun V2Row.flag(column: String): Boolean? = longOrNull(column)?.let { it == 1L }
    }
}
