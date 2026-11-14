package com.avago.feature.rentalsops.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.auth.IdentityManager
import com.avago.feature.rentalsops.data.ApiResult
import com.avago.feature.rentalsops.data.RentalOpsApi
import com.avago.feature.rentalsops.domain.str
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rentalOpsPushStore: DataStore<Preferences> by preferencesDataStore(name = "rentalops_push_prefs")

/** One row of `GET …/notification-prefs` (only events the caller may receive are listed). */
data class NotificationPref(val event: String, val description: String?, val pushEnabled: Boolean)

/**
 * Per-event push switches. The server's `notification-prefs` (stage C) is the source of truth
 * — it stops sending disabled events. A device-local mirror (per account) is kept so:
 * - the settings screen renders offline;
 * - users without `rental.view` (drive-only; the endpoint answers 403) can still mute events
 *   on this device.
 */
@Singleton
class RentalOpsPushPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
    private val api: RentalOpsApi,
) {
    private fun key(accountId: String?, event: String) = booleanPreferencesKey("push:${accountId ?: "-"}:$event")

    /** Local mirror for [accountId]: event → enabled (default on, as on the server). */
    fun local(accountId: String?): Flow<Map<String, Boolean>> = ctx.rentalOpsPushStore.data.map { p ->
        RentalPushRouter.EVENTS.associateWith { p[key(accountId, it)] ?: true }
    }

    suspend fun isEnabled(event: String): Boolean =
        local(identity.getActiveAccountId()).first()[event] ?: true

    sealed class Load {
        data class Server(val items: List<NotificationPref>) : Load()
        /** 403: the caller cannot manage server prefs; device-only switches apply. */
        data object DeviceOnly : Load()
        data class Offline(val message: String) : Load()
    }

    /** Loads the server prefs and refreshes the local mirror. */
    suspend fun load(): Load {
        val a = identity.getActiveAccountId() ?: return Load.Offline("signed out")
        return when (val r = api.notificationPrefs(a)) {
            is ApiResult.Ok -> {
                val items = parse(r.body)
                mirror(a, items)
                Load.Server(items)
            }
            is ApiResult.Http -> if (r.status == 403) Load.DeviceOnly else Load.Offline(r.message)
            is ApiResult.Transport -> Load.Offline(r.message)
        }
    }

    /**
     * Sets [event]. With [server] true the change is PUT first and only mirrored locally once
     * the server accepted it (so the screen never shows a state the server does not have).
     */
    suspend fun set(event: String, on: Boolean, server: Boolean): Load {
        val a = identity.getActiveAccountId() ?: return Load.Offline("signed out")
        if (!server) {
            ctx.rentalOpsPushStore.edit { it[key(a, event)] = on }
            return Load.DeviceOnly
        }
        return when (val r = api.putNotificationPref(a, event, on)) {
            is ApiResult.Ok -> {
                val items = parse(r.body)
                mirror(a, items)
                Load.Server(items)
            }
            is ApiResult.Http -> if (r.status == 403) Load.DeviceOnly else Load.Offline(r.message)
            is ApiResult.Transport -> Load.Offline(r.message)
        }
    }

    private suspend fun mirror(accountId: String, items: List<NotificationPref>) {
        ctx.rentalOpsPushStore.edit { p -> items.forEach { p[key(accountId, it.event)] = it.pushEnabled } }
    }

    companion object {
        fun parse(body: JsonObject): List<NotificationPref> =
            (body["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
                val event = o.str("event") ?: return@mapNotNull null
                NotificationPref(event, o.str("description"), (o["push_enabled"] as? JsonPrimitive)?.booleanOrNull ?: true)
            }
    }
}
