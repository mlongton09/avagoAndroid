package com.avago.feature.rentalsops.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rentalOpsPushStore: DataStore<Preferences> by preferencesDataStore(name = "rentalops_push_prefs")

/**
 * Per-category on/off switches for rental push notifications (device-local).
 *
 * TODO(stage C): mirror to the server's `rental_notification_prefs (event, push_enabled)` once
 * an endpoint exists, so the server stops sending disabled events instead of the device
 * dropping them.
 */
@Singleton
class RentalOpsPushPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private fun key(c: RentalPushRouter.Category) = booleanPreferencesKey("push_${c.key}")

    val enabled: Flow<Map<RentalPushRouter.Category, Boolean>> = ctx.rentalOpsPushStore.data.map { p ->
        RentalPushRouter.Category.entries.associateWith { p[key(it)] ?: true }
    }

    suspend fun isEnabled(c: RentalPushRouter.Category): Boolean = enabled.first()[c] ?: true

    suspend fun set(c: RentalPushRouter.Category, on: Boolean) {
        ctx.rentalOpsPushStore.edit { it[key(c)] = on }
    }
}
