package com.avago.core.sync

import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Pulls cross-device user preferences from the server and writes them into the local
 * [UserPreferencesRepository] DataStore so that Settings-screen flows emit the
 * server-authoritative values.
 *
 * Non-fatal: any failure is logged and silently swallowed.
 */
@Singleton
class PreferencesSync @Inject constructor(
    private val serviceClientProvider: Provider<AvagoServiceClient>,
    private val prefs: UserPreferencesRepository,
) {

    /**
     * Fetches preferences for [accountId] and writes each non-null field into
     * the DataStore keys that [UserPreferencesRepository] exposes.
     */
    suspend fun refreshFromServer(accountId: String) {
        try {
            when (val result = serviceClientProvider.get().getUserPreferences(accountId)) {
                is NetworkResult.Success -> {
                    val p = result.data
                    p.theme?.let { prefs.setTheme(it) }
                    p.language?.let { prefs.setLanguage(it) }
                    p.distance_unit?.let { prefs.setDistanceUnit(it) }
                    // currency and notifications_enabled are stored separately
                    p.currency?.let { prefs.setCurrency(it) }
                    p.notifications_enabled?.let { prefs.setNotificationsEnabled(it) }
                    Timber.d("PreferencesSync: applied server preferences for $accountId")
                }
                is NetworkResult.Error -> {
                    Timber.w("PreferencesSync: server returned error ${result.code} — keeping local preferences")
                }
                is NetworkResult.Unauthorized -> {
                    Timber.w("PreferencesSync: unauthorized — keeping local preferences")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "PreferencesSync: refreshFromServer failed — keeping local preferences")
        }
    }
}
