package com.avago.core.data.v2.repo

import com.avago.core.data.domain.NotificationChannel
import com.avago.core.data.domain.NotificationPreference
import com.avago.core.data.domain.UserPreferenceChange
import com.avago.core.data.domain.UserPreferenceOverrides
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Stores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V2AccountPreferencesRepositoryTest {
    private val business = V2Fixtures.business()
    private val active = V2ActiveStores().apply { publish(V2Stores(V2Fixtures.identity, business, V2Fixtures.chat())) }
    private val repo = V2AccountPreferencesRepository(V2StoreAccess(active), V2CommandService(active) {})
    private val user = V2Fixtures.identity.userId

    private suspend fun operations() = business.read { conn ->
        conn.query("SELECT operation FROM pending_commands ORDER BY causal_order").map { it["operation"] }
    }

    @Test
    fun `own preferences are read from the synced row and edits show at once`() = runTest {
        assertNull(repo.observeMine().first())
        business.writeWithoutForeignKeys { tx ->
            tx.execute(
                "INSERT INTO user_preferences (user_id, version, currency, distance_unit, disable_quotes, created_at, updated_at) " +
                    "VALUES (?, 1, 'USD', 'km', 0, 1, 1)",
                listOf(user),
            )
        }
        assertEquals(UserPreferenceOverrides(currency = "USD", distanceUnit = "km", disableQuotes = false), repo.observeMine().first())

        repo.update(listOf(UserPreferenceChange.Currency("EUR"), UserPreferenceChange.DistanceUnit(null)))

        assertEquals(UserPreferenceOverrides(currency = "EUR", disableQuotes = false), repo.observeMine().first())
        assertEquals(listOf("core.user_preferences.update"), operations())
    }

    @Test
    fun `account defaults are read from the synced account row`() = runTest {
        assertNull(repo.observeAccountDefaults().first())
        business.writeWithoutForeignKeys { tx ->
            tx.execute(
                "INSERT INTO account_preferences (singleton, created_at, updated_at, currency, fuel_volume_unit, enable_human_in_loop) " +
                    "VALUES (1, 1, 1, 'NOK', 'liter', 0)",
            )
        }
        assertEquals(
            UserPreferenceOverrides(currency = "NOK", fuelVolumeUnit = "liter", enableHumanInLoop = false),
            repo.observeAccountDefaults().first(),
        )
    }

    @Test
    fun `a notification switch is stored for the user at once`() = runTest {
        repo.setNotification("rental.overdue", NotificationChannel.PUSH, false)

        assertEquals(
            listOf(NotificationPreference("rental.overdue", NotificationChannel.PUSH, false)),
            repo.observeNotificationPreferences().first(),
        )
        assertEquals(listOf("specialist.notification_preferences.set"), operations())
    }

    @Test
    fun `editing needs the settings permission`() = runTest {
        assertFalse(repo.observeCanEdit().first())
        business.writeWithoutForeignKeys { tx ->
            tx.execute("INSERT INTO effective_permissions (permission_key, allowed) VALUES ('settings.edit_display', 1)")
        }
        assertTrue(repo.observeCanEdit().first())
    }
}
