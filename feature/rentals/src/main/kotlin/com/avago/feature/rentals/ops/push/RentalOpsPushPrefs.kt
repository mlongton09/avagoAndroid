package com.avago.feature.rentals.ops.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.AccountPreferencesRepository
import com.avago.core.data.domain.NotificationChannel
import com.avago.core.data.domain.NotificationPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rentalOpsPushStore: DataStore<Preferences> by preferencesDataStore(name = "rentalops_push_prefs")

/** The push switches of the signed-in user: event to enabled, and where they are kept. */
data class PushSwitches(val enabled: Map<String, Boolean>, val synced: Boolean)

/**
 * Per-event push switches. The user's synced `notification_preferences` (push channel) are the
 * source of truth: a switch queues `specialist.notification_preferences.set`, so the server stops
 * sending a muted event to all of the user's devices. Users without `settings.edit_display`
 * cannot store preferences; for them the switches mute events on this device only (per account).
 * Events without a stored row are on (the server default).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalOpsPushPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
    private val account: AccountPreferencesRepository,
) {
    private fun key(accountId: String?, event: String) = booleanPreferencesKey("push:${accountId ?: "-"}:$event")

    private fun device(accountId: String?): Flow<Map<String, Boolean>> = ctx.rentalOpsPushStore.data.map { p ->
        RentalPushRouter.EVENTS.associateWith { p[key(accountId, it)] ?: true }
    }

    fun observe(): Flow<PushSwitches> = account.observeCanEdit().flatMapLatest { canEdit ->
        if (canEdit) {
            account.observeNotificationPreferences().map { PushSwitches(synced(it), synced = true) }
        } else {
            identity.activeAccountId.flatMapLatest(::device).map { PushSwitches(it, synced = false) }
        }
    }

    suspend fun isEnabled(event: String): Boolean = observe().first().enabled[event] ?: true

    suspend fun set(event: String, on: Boolean) {
        if (account.observeCanEdit().first()) {
            account.setNotification(event, NotificationChannel.PUSH, on)
        } else {
            val a = identity.getActiveAccountId() ?: return
            ctx.rentalOpsPushStore.edit { it[key(a, event)] = on }
        }
    }

    companion object {
        fun synced(stored: List<NotificationPreference>): Map<String, Boolean> {
            val push = stored.filter { it.channel == NotificationChannel.PUSH }.associate { it.eventKey to it.enabled }
            return RentalPushRouter.EVENTS.associateWith { push[it] ?: true }
        }
    }
}
