package com.avago.core.data.v2

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class V2SqliteRuntimeTest {

    @Test
    fun `the WAL-reset fix is 3_51_3 or a verified upstream backport`() {
        listOf("3.51.3", "3.53.4", "3.60.0", "4.0.0", "3.50.7", "3.50.9", "3.44.6", "3.44.7").forEach {
            assertTrue(V2SqliteRuntime.hasWalResetFix(it), it)
        }
        val unfixed = listOf("3.51.2", "3.51.0", "3.50.6", "3.50.1", "3.45.0", "3.44.5", "3.37.0", "3.18.2")
        (unfixed + listOf("", "3.51", "x.y.z")).forEach {
            assertFalse(V2SqliteRuntime.hasWalResetFix(it), it)
        }
    }

    @Test
    fun `the test runtime meets the floor and an older runtime is refused`() {
        V2SqliteRuntime.check(JdbcV2Connection())
        val old = object : V2Connection {
            override fun execute(sql: String, args: List<Any?>): Long = 0
            override fun query(sql: String, args: List<Any?>): List<V2Row> = listOf(mapOf("v" to "3.50.1"))
            override fun close() = Unit
        }
        assertThrows<V2SqlException> { V2SqliteRuntime.check(old) }
        assertThrows<V2SqlException> { V2Database.open(old, V2StoreKind.CHAT, V2Fixtures.identity) }
    }
}
