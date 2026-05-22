package com.avago.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.avago.core.network.TokenProvider
import com.avago.core.network.TokenStorage
import java.util.UUID
import javax.inject.Singleton

private const val PREFS_FILE = "avago_secure_prefs"
private const val KEY_DEVICE_ID = "av_device_id"
private const val KEY_PUSH_TOKEN = "av_push_token"

@Singleton
class SecureTokenStore(
    context: Context,
) : TokenProvider, TokenStorage {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Set by IdentityManager after it determines the active account.
     * The TokenProvider / TokenStorage implementations read from this field so
     * no circular Hilt dependency is introduced.
     */
    @Volatile
    var activeAccountId: String? = null

    // ---------------------------------------------------------------------------
    // TokenProvider impl
    // ---------------------------------------------------------------------------

    override suspend fun accessToken(): String =
        activeAccountId?.let { getAccessToken(it) } ?: ""

    override suspend fun refreshToken(): String =
        activeAccountId?.let { getRefreshToken(it) } ?: ""

    // ---------------------------------------------------------------------------
    // TokenStorage impl
    // ---------------------------------------------------------------------------

    override suspend fun storeTokens(accessToken: String, refreshToken: String) {
        activeAccountId?.let { storeTokens(it, accessToken, refreshToken) }
    }

    override suspend fun clearTokens() {
        activeAccountId?.let { clearTokens(it) }
    }

    // ---------------------------------------------------------------------------
    // Per-account tokens
    // ---------------------------------------------------------------------------

    fun storeAccessToken(accountId: String, token: String) {
        prefs.edit().putString(accessKey(accountId), token).apply()
    }

    fun getAccessToken(accountId: String): String? =
        prefs.getString(accessKey(accountId), null)

    fun storeRefreshToken(accountId: String, token: String) {
        prefs.edit().putString(refreshKey(accountId), token).apply()
    }

    fun getRefreshToken(accountId: String): String? =
        prefs.getString(refreshKey(accountId), null)

    fun storeTokens(accountId: String, access: String, refresh: String) {
        prefs.edit()
            .putString(accessKey(accountId), access)
            .putString(refreshKey(accountId), refresh)
            .apply()
    }

    fun clearTokens(accountId: String) {
        prefs.edit()
            .remove(accessKey(accountId))
            .remove(refreshKey(accountId))
            .apply()
    }

    fun clearAllTokens() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("av_access_token_") || it.startsWith("av_refresh_token_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // ---------------------------------------------------------------------------
    // Device ID — single UUID shared across all accounts on this device
    // ---------------------------------------------------------------------------

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    // ---------------------------------------------------------------------------
    // Push token
    // ---------------------------------------------------------------------------

    fun storePushToken(token: String) {
        prefs.edit().putString(KEY_PUSH_TOKEN, token).apply()
    }

    fun getPushToken(): String? = prefs.getString(KEY_PUSH_TOKEN, null)

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun accessKey(accountId: String) = "av_access_token_$accountId"
    private fun refreshKey(accountId: String) = "av_refresh_token_$accountId"
}
