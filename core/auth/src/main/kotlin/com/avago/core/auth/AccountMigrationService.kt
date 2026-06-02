package com.avago.core.auth

import android.database.sqlite.SQLiteDatabase
import com.avago.core.data.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AnonymousMigrationCandidate(
    val sourceAccountId: String,
    val assetCount: Int,
)

@Singleton
class AccountMigrationService @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val accountManifest: AccountManifest,
    private val databaseFactory: DatabaseFactory,
) {
    suspend fun pendingAnonymousMigration(): AnonymousMigrationCandidate? = withContext(Dispatchers.IO) {
        val activeAccountId = tokenStore.activeAccountId ?: return@withContext null
        val account = accountManifest.allAccounts()
            .firstOrNull { it.accountId == activeAccountId && it.isAnonymous }
            ?: return@withContext null
        val assetCount = runCatching {
            databaseFactory.get(account.accountId).assetDao().countRealAssets(account.accountId)
        }.onFailure { Timber.w(it, "AccountMigrationService: failed to count anonymous assets") }
            .getOrDefault(0)
        runCatching { databaseFactory.close(account.accountId) }
        if (assetCount > 0) AnonymousMigrationCandidate(account.accountId, assetCount) else null
    }

    suspend fun migrateAnonymousToAuthenticated(sourceAccountId: String, newAccountId: String): Boolean =
        withContext(Dispatchers.IO) {
            val migrated = runCatching {
                // Force destination Room schema materialization before raw-SQLite reopen — Room is lazy
                // and won't create the .db file until the first DAO query runs.
                databaseFactory.get(newAccountId).openHelper.writableDatabase
                performLocalMigration(sourceAccountId, newAccountId)
            }
                .onFailure { Timber.w(it, "AccountMigrationService: failed to migrate $sourceAccountId to $newAccountId") }
                .getOrDefault(false)
            if (migrated) discardAnonymousAccount(sourceAccountId)
            migrated
        }

    /** Backwards-compatible cleanup for callers that only know the destination account. */
    suspend fun migrateAnonymousToAuthenticated(newAccountId: String) {
        val anonymousAccounts = accountManifest.allAccounts().filter { it.isAnonymous && it.accountId != newAccountId }
        for (anonAccount in anonymousAccounts) {
            discardAnonymousAccount(anonAccount.accountId)
        }
    }

    suspend fun discardAnonymousAccount(accountId: String) = withContext(Dispatchers.IO) {
        tokenStore.clearTokens(accountId)
        accountManifest.remove(accountId)
        databaseFactory.deleteDatabase(accountId)
        Timber.d("AccountMigrationService: anonymous account $accountId removed")
    }

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

    private fun performLocalMigration(sourceAccountId: String, newAccountId: String): Boolean {
        if (sourceAccountId == newAccountId) return true
        val sourceFile = databaseFactory.databaseFile(sourceAccountId)
        if (!sourceFile.exists()) return false
        databaseFactory.close(sourceAccountId)
        databaseFactory.close(newAccountId)
        val destFile = databaseFactory.databaseFile(newAccountId)
        if (!destFile.exists()) return false

        val db = SQLiteDatabase.openDatabase(destFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        return try {
            // FK off during the bulk copy — INSERT OR IGNORE keeps destination rows on PK collision
            // so we don't trigger ON DELETE CASCADE that would wipe pre-existing child rows.
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.beginTransaction()
            db.execSQL("ATTACH DATABASE ? AS source", arrayOf(sourceFile.absolutePath))
            for (table in accountScopedTables(db)) {
                copyAccountRows(db, table, sourceAccountId, newAccountId)
            }
            db.setTransactionSuccessful()
            true
        } finally {
            runCatching { db.endTransaction() }
            runCatching { db.execSQL("DETACH DATABASE source") }
            runCatching { db.execSQL("PRAGMA foreign_keys = ON") }
            db.close()
        }
    }

    private fun accountScopedTables(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                if (columnsFor(db, table).contains("account_id")) tables.add(table)
            }
        }
        return tables
    }

    private fun copyAccountRows(db: SQLiteDatabase, table: String, sourceAccountId: String, newAccountId: String) {
        val columns = columnsFor(db, table)
        if (columns.isEmpty()) return
        val insertColumns = columns.joinToString(", ") { it.quotedIdentifier() }
        val selectColumns = columns.joinToString(", ") { column ->
            if (column == "account_id") "? AS ${column.quotedIdentifier()}"
            else "source.${table.quotedIdentifier()}.${column.quotedIdentifier()}"
        }
        db.execSQL(
            "INSERT OR IGNORE INTO main.${table.quotedIdentifier()} ($insertColumns) " +
                "SELECT $selectColumns FROM source.${table.quotedIdentifier()} WHERE ${"account_id".quotedIdentifier()} = ?",
            arrayOf(newAccountId, sourceAccountId),
        )
    }

    private fun columnsFor(db: SQLiteDatabase, table: String): List<String> {
        val columns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(${table.quotedIdentifier()})", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
        }
        return columns
    }

    private fun String.quotedIdentifier(): String = "\"" + replace("\"", "\"\"") + "\""
}
