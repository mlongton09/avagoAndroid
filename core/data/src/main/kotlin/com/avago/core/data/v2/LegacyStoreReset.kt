package com.avago.core.data.v2

import java.io.File

/**
 * One-time local reset on the first v2-enabled launch (plan §14.4, decisions L12/L13).
 *
 * Every legacy store is disposable (L13: no on-device migration), **except** what keeps the user
 * signed in: auth tokens, the account manifest, and device-level settings. The authoritative
 * enumerated list lives in `docs/v2-reset-spec.md`; [Plan] mirrors it and tests pin it.
 *
 * Crash safety: [V2ResetMarker] is written `reset_started` before anything is deleted and
 * `reset_complete` only after every step succeeded. Each step is idempotent (deleting what is
 * already gone is a no-op), so an interrupted reset simply runs again from the start on the next
 * launch. Once complete, [runIfNeeded] is a no-op.
 *
 * Must run before any legacy database, SharedPreferences or DataStore file is opened in the
 * process (`AvagoApplication.onCreate`, right after Hilt injection), because DataStore forbids
 * two live instances per file and Room keeps open handles.
 */
class LegacyStoreReset(
    private val marker: V2ResetMarker,
    private val dirs: Dirs,
    /** Removes `chat_draft_*` keys from the kept `user_prefs` DataStore. */
    private val clearDrafts: suspend () -> Unit,
    /** Closes any already-open legacy database handles before files are deleted. */
    private val beforeWipe: suspend () -> Unit = {},
) {
    data class Dirs(
        val filesDir: File,
        val sharedPrefsDir: File,
        val dataStoreDir: File,
        val cacheDir: File,
    )

    data class Result(val ran: Boolean, val deleted: List<String>)

    /** The enumerated reset spec (docs/v2-reset-spec.md). */
    object Plan {
        /** SharedPreferences files deleted (`shared_prefs/<name>.xml`, plus `.bak`). */
        val CLEARED_SHARED_PREFS: List<String> = listOf(
            "sync_rate_limit",
            "av_dispatch_mode",
            "screen_context",
            "rental_onboarding",
        )

        /** DataStore files deleted (`datastore/<name>.preferences_pb`). */
        val CLEARED_DATASTORES: List<String> = listOf(
            "rbac_permissions",
            "exchange_rates",
            "rate_limit_backoff",
            "inspection_drafts",
            "rentalops_route_tracking",
            "rentalops_push_prefs",
        )

        /** Top-level directories under `filesDir` deleted (local media and generated reports). */
        val CLEARED_FILES_DIRS: List<String> = listOf("asset_photos", "rental_photos", "pdf_reports")

        /** Top-level file-name prefixes under `filesDir` deleted (route ping queues). */
        val CLEARED_FILES_PREFIXES: List<String> = listOf("rentalops_route_pings_")

        /** SharedPreferences kept: tokens, renter session, device-level runtime flags, v2 flag. */
        val KEPT_SHARED_PREFS: List<String> = listOf(
            "avago_secure_prefs",
            "avago_marketplace_session",
            "avago_runtime_flags",
            "rentalops_route_tracking_ui",
            SharedPreferencesV2FlagStorage.PREFS_NAME,
        )

        /** DataStore kept (only `chat_draft_*` keys are removed from it). */
        val KEPT_DATASTORES: List<String> = listOf("user_prefs")

        /** Files kept under `filesDir`: the signed-in account list. */
        val KEPT_FILES: List<String> = listOf("accounts_manifest.json")
    }

    suspend fun runIfNeeded(): Result {
        if (marker.read() == V2ResetState.COMPLETE) return Result(ran = false, deleted = emptyList())
        marker.write(V2ResetState.STARTED)

        val deleted = mutableListOf<String>()
        beforeWipe()
        wipeLegacyAccountStores(deleted)
        Plan.CLEARED_FILES_DIRS.forEach { delete(File(dirs.filesDir, it), deleted) }
        dirs.filesDir.listFiles()
            ?.filter { f -> f.isFile && Plan.CLEARED_FILES_PREFIXES.any { f.name.startsWith(it) } }
            ?.forEach { delete(it, deleted) }
        Plan.CLEARED_SHARED_PREFS.forEach { name ->
            delete(File(dirs.sharedPrefsDir, "$name.xml"), deleted)
            delete(File(dirs.sharedPrefsDir, "$name.xml.bak"), deleted)
        }
        Plan.CLEARED_DATASTORES.forEach { name ->
            delete(File(dirs.dataStoreDir, "$name.preferences_pb"), deleted)
        }
        clearDrafts()
        dirs.cacheDir.listFiles()?.forEach { delete(it, deleted) }

        marker.write(V2ResetState.COMPLETE)
        return Result(ran = true, deleted = deleted)
    }

    /**
     * Deletes the legacy per-account databases (with `-wal`/`-shm`/`-journal`) and media in every
     * `accounts/<account_id>/`. Generation directories (`<user_id>/v2/`) are never touched.
     */
    private fun wipeLegacyAccountStores(deleted: MutableList<String>) {
        val accountDirs = File(dirs.filesDir, V2StorePaths.ACCOUNTS_DIR).listFiles { f -> f.isDirectory }
            ?: return
        for (accountDir in accountDirs) {
            for (db in V2StorePaths.LEGACY_DATABASES) {
                for (suffix in V2StorePaths.SQLITE_SIDECARS) delete(File(accountDir, db + suffix), deleted)
            }
            V2StorePaths.LEGACY_ACCOUNT_DIRS.forEach { delete(File(accountDir, it), deleted) }
        }
    }

    private fun delete(file: File, deleted: MutableList<String>) {
        if (!file.exists()) return
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!ok && file.exists()) throw java.io.IOException("v2 reset could not delete ${file.name}")
        deleted += file.path
    }
}
