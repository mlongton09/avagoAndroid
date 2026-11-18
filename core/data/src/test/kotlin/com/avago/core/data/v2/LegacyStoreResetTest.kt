package com.avago.core.data.v2

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LegacyStoreResetTest {

    @TempDir
    lateinit var root: File

    private lateinit var files: File
    private lateinit var prefs: File
    private lateinit var dataStore: File
    private lateinit var cache: File
    private lateinit var marker: V2ResetMarker
    private var draftsCleared = 0
    private var wipePrepared = 0

    @BeforeEach
    fun setUp() {
        files = File(root, "files")
        prefs = File(root, "shared_prefs")
        dataStore = File(files, "datastore")
        cache = File(root, "cache")
        marker = V2ResetMarker(File(root, "no_backup/${V2StorePaths.RESET_MARKER_FILE}"))
        seedLegacyInstall()
    }

    private fun touch(dir: File, name: String) = File(dir, name).apply {
        parentFile.mkdirs()
        writeText("x")
    }

    private fun seedLegacyInstall() {
        for (account in listOf("acct-1", "acct-2")) {
            for (db in V2StorePaths.LEGACY_DATABASES) {
                for (suffix in V2StorePaths.SQLITE_SIDECARS) touch(files, "accounts/$account/$db$suffix")
            }
            touch(files, "accounts/$account/rentalops_media/proof.jpg")
        }
        touch(files, "accounts/acct-1/user-1/v2/avago_v2.db")
        touch(files, "accounts_manifest.json")
        touch(files, "asset_photos/a.jpg")
        touch(files, "rental_photos/r.jpg")
        touch(files, "pdf_reports/p.pdf")
        touch(files, "rentalops_route_pings_acct-1.json")
        touch(files, "unrelated_note.txt")
        (LegacyStoreReset.Plan.CLEARED_SHARED_PREFS + LegacyStoreReset.Plan.KEPT_SHARED_PREFS).forEach {
            touch(prefs, "$it.xml")
        }
        touch(prefs, "sync_rate_limit.xml.bak")
        touch(prefs, "com.google.firebase.messaging.xml")
        (LegacyStoreReset.Plan.CLEARED_DATASTORES + LegacyStoreReset.Plan.KEPT_DATASTORES).forEach {
            touch(dataStore, "$it.preferences_pb")
        }
        touch(cache, "chat_captures/c.jpg")
        touch(cache, "photo_1.jpg")
    }

    private fun reset(clearDrafts: suspend () -> Unit = { draftsCleared++ }) = LegacyStoreReset(
        marker = marker,
        dirs = LegacyStoreReset.Dirs(files, prefs, dataStore, cache),
        clearDrafts = clearDrafts,
        beforeWipe = { wipePrepared++ },
    )

    private fun exists(dir: File, path: String) = File(dir, path).exists()

    @Test
    fun `clears legacy stores and keeps sign-in, settings and v2 files`() = runBlocking {
        val result = reset().runIfNeeded()

        assertTrue(result.ran)
        assertEquals(V2ResetState.COMPLETE, marker.read())
        assertEquals(1, draftsCleared)
        assertEquals(1, wipePrepared)

        // Cleared: every legacy DB and sidecar in every account, plus media.
        for (account in listOf("acct-1", "acct-2")) {
            for (db in V2StorePaths.LEGACY_DATABASES) {
                for (suffix in V2StorePaths.SQLITE_SIDECARS) {
                    assertFalse(exists(files, "accounts/$account/$db$suffix"), "$account/$db$suffix")
                }
            }
            assertFalse(exists(files, "accounts/$account/rentalops_media"))
        }
        listOf("asset_photos", "rental_photos", "pdf_reports", "rentalops_route_pings_acct-1.json")
            .forEach { assertFalse(exists(files, it), it) }
        LegacyStoreReset.Plan.CLEARED_SHARED_PREFS.forEach { assertFalse(exists(prefs, "$it.xml"), it) }
        assertFalse(exists(prefs, "sync_rate_limit.xml.bak"))
        LegacyStoreReset.Plan.CLEARED_DATASTORES.forEach { assertFalse(exists(dataStore, "$it.preferences_pb"), it) }
        assertEquals(0, cache.listFiles()!!.size)

        // Kept: tokens, sign-in manifest, device settings, v2 generation dir, unknown files.
        LegacyStoreReset.Plan.KEPT_SHARED_PREFS.forEach { assertTrue(exists(prefs, "$it.xml"), it) }
        LegacyStoreReset.Plan.KEPT_DATASTORES.forEach { assertTrue(exists(dataStore, "$it.preferences_pb"), it) }
        assertTrue(exists(prefs, "avago_secure_prefs.xml"))
        assertTrue(exists(files, "accounts_manifest.json"))
        assertTrue(exists(files, "accounts/acct-1/user-1/v2/avago_v2.db"))
        assertTrue(exists(prefs, "com.google.firebase.messaging.xml"))
        assertTrue(exists(files, "unrelated_note.txt"))
        assertTrue(cache.isDirectory)
    }

    @Test
    fun `second run is a no-op once complete`() = runBlocking {
        reset().runIfNeeded()
        touch(files, "accounts/acct-1/avago.db") // recreated by legacy code would be ignored now

        val second = reset().runIfNeeded()

        assertFalse(second.ran)
        assertTrue(second.deleted.isEmpty())
        assertEquals(1, draftsCleared)
        assertTrue(exists(files, "accounts/acct-1/avago.db"))
    }

    @Test
    fun `interrupted reset leaves marker started and resumes on next run`() = runBlocking {
        val interrupted = reset(clearDrafts = { error("process killed") })

        assertThrows(IllegalStateException::class.java) { runBlocking { interrupted.runIfNeeded() } }
        assertEquals(V2ResetState.STARTED, marker.read())
        assertFalse(exists(files, "accounts/acct-1/avago.db"))
        // Legacy code recreated a store before the next launch; the resumed reset removes it too.
        touch(files, "accounts/acct-1/chat.db-wal")

        val resumed = reset().runIfNeeded()

        assertTrue(resumed.ran)
        assertEquals(V2ResetState.COMPLETE, marker.read())
        assertFalse(exists(files, "accounts/acct-1/chat.db-wal"))
        assertEquals(1, draftsCleared)
        assertTrue(exists(prefs, "avago_secure_prefs.xml"))
    }

    @Test
    fun `fresh install without legacy files completes`() = runBlocking {
        root.listFiles()!!.forEach { it.deleteRecursively() }

        val result = reset().runIfNeeded()

        assertTrue(result.ran)
        assertTrue(result.deleted.isEmpty())
        assertTrue(marker.isComplete)
    }
}
