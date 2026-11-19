package com.avago.feature.rentals.domain

import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.RecordSyncState
import com.avago.feature.rentals.ui.readinessUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Readiness checklist rules: transitions, validation, the current check and the screen state. */
class ReadinessTest {
    private val allDone = Readiness.DEFAULT_ITEMS.map { it.copy(done = true) }

    private fun check(id: String, status: String, at: String = "2026-05-01T00:00:00Z", sync: RecordSyncState = RecordSyncState.SAVED) =
        ReadinessCheck(id, 1, "A", null, status, Readiness.DEFAULT_ITEMS, null, Instant.parse(at), sync)

    @Test
    fun `only open checks change and the target must be a known status`() {
        assertTrue(Readiness.canTransition(Readiness.PENDING, Readiness.PASSED))
        assertTrue(Readiness.canTransition(Readiness.FAILED, Readiness.WAIVED))
        assertFalse(Readiness.canTransition(Readiness.PASSED, Readiness.FAILED))
        assertFalse(Readiness.canTransition(Readiness.WAIVED, Readiness.PENDING))
        assertFalse(Readiness.canTransition(Readiness.PENDING, "ready"))
    }

    @Test
    fun `validation mirrors the server rules`() {
        val v = { from: String, to: String, items: List<com.avago.core.data.domain.ReadinessCheckItem>, notes: String?, waive: Boolean ->
            Readiness.validate(from, to, items, notes, waive)
        }
        assertEquals(Readiness.Problem.INCOMPLETE, v(Readiness.PENDING, Readiness.PASSED, Readiness.DEFAULT_ITEMS, null, false))
        assertNull(v(Readiness.PENDING, Readiness.PASSED, allDone, null, false))
        // Waive: permission first, then a reason (server 403 / 400 reason_required).
        assertEquals(Readiness.Problem.NOT_ALLOWED, v(Readiness.PENDING, Readiness.WAIVED, allDone, "ok", false))
        assertEquals(Readiness.Problem.REASON_REQUIRED, v(Readiness.PENDING, Readiness.WAIVED, allDone, "  ", true))
        assertNull(v(Readiness.FAILED, Readiness.WAIVED, Readiness.DEFAULT_ITEMS, "customer collecting", true))
        // Fail: unticked items explain it; with everything ticked a reason is needed.
        assertNull(v(Readiness.PENDING, Readiness.FAILED, Readiness.DEFAULT_ITEMS, null, false))
        assertEquals(Readiness.Problem.REASON_REQUIRED, v(Readiness.PENDING, Readiness.FAILED, allDone, null, false))
        assertEquals(Readiness.Problem.CLOSED, v(Readiness.PASSED, Readiness.FAILED, allDone, "x", true))
    }

    @Test
    fun `current prefers the newest open check then the newest check`() {
        val old = check("a", "passed", "2026-04-01T00:00:00Z")
        val newer = check("b", "passed", "2026-05-01T00:00:00Z")
        assertEquals("b", Readiness.current(listOf(old, newer))!!.id)
        val openOld = check("c", "pending", "2026-03-01T00:00:00Z")
        assertEquals("c", Readiness.current(listOf(old, newer, openOld))!!.id)
        assertNull(Readiness.current(emptyList()))
    }

    @Test
    fun `screen edits open checks and shows decided ones read only until a new check starts`() {
        assertTrue(readinessUi(null, false).editable)
        assertEquals("a", readinessUi(check("a", "failed"), false).check?.id)
        val passed = check("a", "passed")
        assertFalse(readinessUi(passed, false).editable)
        assertNull(readinessUi(passed, true).check)
        assertTrue(readinessUi(passed, true).editable)
        val queued = check("a", "passed", sync = RecordSyncState.PENDING)
        assertFalse(readinessUi(queued, true).editable)
    }
}
