package com.avago.core.data.db

import androidx.room.migration.Migration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the "no silent wipe" rule: the destructive fallback is limited to pre-release
 * versions, so every later version step needs an explicit migration and an exported schema.
 * A version bump without both fails here (in CI) instead of crashing or wiping on devices.
 */
class DatabaseMigrationChainTest {

    @Test
    fun `AvagoDatabase has one migration per step up to the current version`() {
        assertContiguousChain(AVAGO_MIGRATIONS, AVAGO_DESTRUCTIVE_FROM_VERSIONS, AVAGO_DB_VERSION)
    }

    @Test
    fun `exported schemas exist for the current and previous versions`() {
        for ((db, version) in listOf("AvagoDatabase" to AVAGO_DB_VERSION)) {
            for (v in listOf(version - 1, version)) {
                val schema = File("schemas/com.avago.core.data.db.$db/$v.json")
                assertTrue(schema.isFile, "missing exported schema ${schema.path}")
            }
        }
    }

    private fun assertContiguousChain(migrations: Array<Migration>, destructiveFrom: IntArray, current: Int) {
        val byStart = migrations.groupBy { it.startVersion }
        byStart.forEach { (start, list) -> assertEquals(1, list.size, "duplicate migrations from v$start") }
        migrations.forEach { assertEquals(it.startVersion + 1, it.endVersion, "non-single-step migration $it") }

        val oldestMigrated = migrations.minOf { it.startVersion }
        for (v in oldestMigrated until current) {
            assertTrue(v in byStart, "no migration for v$v -> v${v + 1}")
        }
        assertEquals(current, migrations.maxOf { it.endVersion }, "chain does not reach the current version")
        // Destructive recreation only for versions older than the first migration, never on top of one.
        assertEquals((1 until oldestMigrated).toList(), destructiveFrom.sorted())
    }
}
