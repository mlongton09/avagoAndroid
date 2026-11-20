package com.avago.core.data.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V2SyncHintsTest {
    private val account = "0190a6f0-0000-7000-8000-000000000001"

    @Test
    fun `a hint pulls only when its head is ahead of the committed cursor`() {
        assertEquals(V2SyncHints.Outcome.AHEAD, V2SyncHints.decide(account, "42", account, 41))
        assertEquals(V2SyncHints.Outcome.STALE, V2SyncHints.decide(account, "42", account, 42))
        assertEquals(V2SyncHints.Outcome.AHEAD, V2SyncHints.decide(account, "1", account, null), "nothing synced yet")
        assertEquals(V2SyncHints.Outcome.AHEAD, V2SyncHints.decide(null, "9", account, 3), "the account is optional")
    }

    @Test
    fun `hints for another account wait and unreadable heads pull`() {
        val other = "0190a6f0-0000-7000-8000-000000000002"
        assertEquals(V2SyncHints.Outcome.OTHER_ACCOUNT, V2SyncHints.decide(other, "42", account, 1))
        assertEquals(V2SyncHints.Outcome.MALFORMED, V2SyncHints.decide(account, null, account, 1))
        assertTrue(V2SyncHints.Outcome.MALFORMED.syncs)
    }
}
