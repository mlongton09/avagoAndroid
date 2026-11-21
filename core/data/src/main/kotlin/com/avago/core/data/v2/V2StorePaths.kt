package com.avago.core.data.v2

import java.io.File

/**
 * File layout of the legacy (v1) and v2 local stores (plan §14.4, decision L12).
 *
 * v2 stores use **generation-specific paths** — `accounts/<account_id>/<user_id>/v2/` — and new
 * file names, so a v2 file can never be mistaken for, or collide with, a legacy file.
 */
object V2StorePaths {
    /** Account directory root shared by the legacy and v2 stores, under `Context.filesDir`. */
    const val ACCOUNTS_DIR = "accounts"

    /** Per-user/account v2 business database (`PRAGMA user_version` [V2Contract.V2_DB_SCHEMA_VERSION]). */
    const val V2_BUSINESS_DB = "avago_v2.db"

    /**
     * Pre-P5-06 name of the per-user/account v2 chat database; renamed to [v2ChatDatabaseName]
     * (with its sidecars) before the first open, then upgraded in place.
     */
    const val V2_CHAT_DB = "chat_v2.db"

    /**
     * Per-account device-wide chat files retired by L42: the Room `ChatDatabase` and the rental-ops
     * `RentalOpsDatabase` in `accounts/<account_id>/`. Deleted (with sidecars) whenever that
     * account's v2 stores open; their data lives in the v2 chat file and the typed business tables.
     */
    val RETIRED_ACCOUNT_DATABASES: List<String> = listOf("chat.db", "rentalops.db")

    /** Generation directory name under `accounts/<account>/<user>/`. */
    const val V2_GENERATION_DIR = "v2"

    /** Cutover marker file in `Context.noBackupFilesDir` (plan §14.4 step 1). */
    const val RESET_MARKER_FILE = "avago_v2_reset.marker"

    /**
     * Legacy per-account Room databases in `accounts/<account_id>/`:
     * AvagoDatabase v20 (`DatabaseFactory`), the retired Room ChatDatabase v8 and the retired
     * RentalOpsDatabase v1 (`RentalOpsDatabaseFactory`).
     */
    val LEGACY_DATABASES: List<String> = listOf("avago.db", "chat.db", "rentalops.db")

    /** SQLite sidecars deleted with each legacy database. */
    val SQLITE_SIDECARS: List<String> = listOf("", "-wal", "-shm", "-journal")

    /** Legacy per-account directories (rental-ops proof media). */
    val LEGACY_ACCOUNT_DIRS: List<String> = listOf("rentalops_media")

    fun v2Directory(filesDir: File, accountId: String, userId: String): File =
        File(filesDir, "$ACCOUNTS_DIR/${safeSegment(accountId)}/${safeSegment(userId)}/$V2_GENERATION_DIR")

    fun v2BusinessDatabase(filesDir: File, accountId: String, userId: String): File =
        File(v2Directory(filesDir, accountId, userId), V2_BUSINESS_DB)

    /** The per-user-and-account chat database file name, `chat-<user>-<account>.sqlite` (L42). */
    fun v2ChatDatabaseName(accountId: String, userId: String): String =
        "chat-${safeSegment(userId)}-${safeSegment(accountId)}.sqlite"

    fun v2ChatDatabase(filesDir: File, accountId: String, userId: String): File =
        File(v2Directory(filesDir, accountId, userId), v2ChatDatabaseName(accountId, userId))

    /** `accounts/<account_id>/`, holding the legacy per-account files. */
    fun accountDirectory(filesDir: File, accountId: String): File =
        File(filesDir, "$ACCOUNTS_DIR/${safeSegment(accountId)}")

    /**
     * File housekeeping before the stores of one user/account open (L42):
     *  - the pre-P5-06 `chat_v2.db` (already per user/account, schema v1) is renamed with its
     *    sidecars to `chat-<user>-<account>.sqlite`, which [V2Database.open] then upgrades in place;
     *  - the retired device-wide `accounts/<account_id>/chat.db` and `rentalops.db` are deleted.
     * Returns the chat database file. Idempotent; never touches the business store.
     */
    fun prepareStoreFiles(filesDir: File, accountId: String, userId: String): File {
        val dir = v2Directory(filesDir, accountId, userId).apply { mkdirs() }
        val chat = File(dir, v2ChatDatabaseName(accountId, userId))
        val previous = File(dir, V2_CHAT_DB)
        if (previous.exists() && !chat.exists()) {
            for (suffix in SQLITE_SIDECARS.reversed()) {
                val from = File(previous.path + suffix)
                if (from.exists() && !from.renameTo(File(chat.path + suffix))) {
                    throw java.io.IOException("could not rename ${from.name}")
                }
            }
        }
        val accountDir = accountDirectory(filesDir, accountId)
        for (db in RETIRED_ACCOUNT_DATABASES) {
            for (suffix in SQLITE_SIDECARS) File(accountDir, db + suffix).delete()
        }
        return chat
    }

    private fun safeSegment(raw: String): String {
        require(raw.isNotBlank() && raw != "." && raw != ".." && raw.none { it == '/' || it == '\\' }) {
            "invalid path segment"
        }
        return raw
    }
}
