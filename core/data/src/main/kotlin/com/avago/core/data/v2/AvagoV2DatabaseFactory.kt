package com.avago.core.data.v2

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Store gate (plan §14.4 step 2): v2 stores open only when v2 is enabled and the legacy reset has
 * completed. The app runs in one process (no `android:process` components), so this process-local
 * check plus running the reset before any store/worker starts is the whole gate.
 */
class V2StoreGate(private val flag: V2Flag, private val marker: V2ResetMarker) {
    fun isOpen(): Boolean = flag.isEnabled() && marker.isComplete
}

/**
 * Opens per-user/account [AvagoV2Database] instances. Mirrors `DatabaseFactory`/`ChatDatabaseFactory`
 * but with generation-specific paths ([V2StorePaths]) and no destructive fallback of any kind.
 *
 * Follow-up (plan §4.2): switch to the bundled SQLite driver (`androidx.sqlite:sqlite-bundled`,
 * `BundledSQLiteDriver`) before the generated STRICT/UPSERT schema lands.
 */
@Singleton
class AvagoV2DatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: V2StoreGate,
) {
    private val mutex = Mutex()
    private val instances = mutableMapOf<String, AvagoV2Database>()

    suspend fun get(accountId: String, userId: String): AvagoV2Database = mutex.withLock {
        check(gate.isOpen()) { "v2 store is gated: v2 disabled or legacy reset not complete" }
        instances.getOrPut(key(accountId, userId)) { build(accountId, userId) }
    }

    suspend fun close(accountId: String, userId: String) = mutex.withLock {
        instances.remove(key(accountId, userId))?.close()
    }

    suspend fun closeAll() = mutex.withLock {
        instances.values.forEach { runCatching { it.close() } }
        instances.clear()
    }

    private suspend fun build(accountId: String, userId: String): AvagoV2Database {
        val file = V2StorePaths.v2BusinessDatabase(context.filesDir, accountId, userId)
        file.parentFile?.mkdirs()
        val db = Room.databaseBuilder(context, AvagoV2Database::class.java, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        db.markerDao().insertIfAbsent(
            V2StoreMarkerEntity(
                schemaVersion = V2Contract.V2_DB_SCHEMA_VERSION,
                contractVersion = V2Contract.CLIENT_CONTRACT_VERSION,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return db
    }

    private fun key(accountId: String, userId: String) = "$accountId/$userId"
}
