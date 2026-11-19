package com.avago.feature.rentalsops.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.auth.IdentityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rentalOpsPushStore: DataStore<Preferences> by preferencesDataStore(name = "rentalops_push_prefs")

/**
 * Per-event push switches, kept on this device per account. The v2 catalog has no command to
 * write `notification_preferences` (see docs/v2-android-status.md, Catalog requests), so muting
 * only suppresses the notification here; it does not stop the server sending it.
 */
@Singleton
class RentalOpsPushPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
) {
    private fun key(accountId: String?, event: String) = booleanPreferencesKey("push:${accountId ?: "-"}:$event")

    /** Switches for [accountId]: event → enabled (default on). */
    fun local(accountId: String?): Flow<Map<String, Boolean>> = ctx.rentalOpsPushStore.data.map { p ->
        RentalPushRouter.EVENTS.associateWith { p[key(accountId, it)] ?: true }
    }

    suspend fun isEnabled(event: String): Boolean =
        local(identity.getActiveAccountId()).first()[event] ?: true

    suspend fun set(event: String, on: Boolean) {
        val a = identity.getActiveAccountId() ?: return
        ctx.rentalOpsPushStore.edit { it[key(a, event)] = on }
    }
}
