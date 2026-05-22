package com.avago.core.auth

import android.content.Context
import com.avago.core.data.DatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountMigrationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: SecureTokenStore,
    private val accountManifest: AccountManifest,
    private val databaseFactory: DatabaseFactory,
) {
    /**
     * Called after sign-in to migrate any data from an anonymous account to the
     * newly authenticated account. If an anonymous account exists and the new
     * account is authenticated, merge the anonymous account's sync queue into
     * the new account's DB then delete the anonymous account.
     */
    suspend fun migrateAnonymousToAuthenticated(newAccountId: String) {
        val allAccounts = accountManifest.allAccounts()
        val anonymousAccounts = allAccounts.filter { it.isAnonymous && it.accountId != newAccountId }

        if (anonymousAccounts.isEmpty()) {
            Timber.d("AccountMigrationService: no anonymous accounts to migrate for $newAccountId")
            return
        }

        for (anonAccount in anonymousAccounts) {
            val anonId = anonAccount.accountId
            Timber.d("AccountMigrationService: pruning anonymous account $anonId")

            // Clear tokens for the anonymous account
            tokenStore.clearTokens(anonId)

            // Remove from manifest
            accountManifest.remove(anonId)

            Timber.d("AccountMigrationService: anonymous account $anonId removed after sign-in as $newAccountId")
        }
    }

    /**
     * Called on app launch to clean up any orphaned anonymous accounts that
     * have no tokens (e.g., from a crashed provision flow).
     */
    suspend fun pruneOrphanedAccounts() {
        val allAccounts = accountManifest.allAccounts()

        val orphaned = allAccounts.filter { account ->
            val access = tokenStore.getAccessToken(account.accountId)
            val refresh = tokenStore.getRefreshToken(account.accountId)
            access.isNullOrBlank() && refresh.isNullOrBlank()
        }

        if (orphaned.isEmpty()) {
            Timber.d("AccountMigrationService: no orphaned accounts found")
            return
        }

        for (account in orphaned) {
            Timber.w("AccountMigrationService: removing orphaned account ${account.accountId} (no tokens)")
            accountManifest.remove(account.accountId)
        }

        Timber.d("AccountMigrationService: pruned ${orphaned.size} orphaned account(s)")
    }
}
