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

    /** Per-user/account v2 business database (Room schema version 1, plan §15.6). */
    const val V2_BUSINESS_DB = "avago_v2.db"

    /** Reserved name of the per-user/account v2 chat database (plan §4.1); not created yet. */
    const val V2_CHAT_DB = "chat_v2.db"

    /** Generation directory name under `accounts/<account>/<user>/`. */
    const val V2_GENERATION_DIR = "v2"

    /** Cutover marker file in `Context.noBackupFilesDir` (plan §14.4 step 1). */
    const val RESET_MARKER_FILE = "avago_v2_reset.marker"

    /**
     * Legacy per-account Room databases in `accounts/<account_id>/`:
     * AvagoDatabase v20 (`DatabaseFactory`), ChatDatabase v8 (`ChatDatabaseFactory`) and
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

    private fun safeSegment(raw: String): String {
        require(raw.isNotBlank() && raw != "." && raw != ".." && raw.none { it == '/' || it == '\\' }) {
            "invalid path segment"
        }
        return raw
    }
}
