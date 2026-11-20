package com.avago.core.auth

import com.avago.core.network.AccessDenial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccessDenialMonitorTest {
    private val active = MutableStateFlow<String?>("acc-1")
    private val monitor = AccessDenialMonitor(active)

    @Test
    fun `a membership denial of the active account is shown until cleared`() = runTest {
        monitor.onAccessDenied(AccessDenial.MEMBERSHIP_SUSPENDED, "acc-1")

        assertEquals(AccessDenialMonitor.Denied("acc-1", AccessDenial.MEMBERSHIP_SUSPENDED), monitor.denied.first())
        assertEquals(AccessDenial.MEMBERSHIP_SUSPENDED, monitor.deniedFor("acc-1"))
        monitor.clear()
        assertNull(monitor.denied.first())
    }

    @Test
    fun `a membership denial of another account is ignored`() = runTest {
        monitor.onAccessDenied(AccessDenial.MEMBERSHIP_INACTIVE, "acc-2")

        assertNull(monitor.denied.first())
        assertNull(monitor.deniedFor("acc-1"))
    }

    @Test
    fun `a user denial applies to the active account whatever the path`() = runTest {
        monitor.onAccessDenied(AccessDenial.USER_DEACTIVATED, "acc-2")

        assertEquals(AccessDenial.USER_DEACTIVATED, monitor.deniedFor("acc-1"))
    }

    @Test
    fun `switching workspace hides the denial of the previous one`() = runTest {
        monitor.onAccessDenied(AccessDenial.MEMBERSHIP_SUSPENDED, null)
        active.value = "acc-2"

        assertNull(monitor.denied.first())
        assertNull(monitor.deniedFor("acc-2"))
    }

    @Test
    fun `nothing is recorded while signed out`() = runTest {
        active.value = null
        monitor.onAccessDenied(AccessDenial.USER_SUSPENDED, null)
        active.value = "acc-1"

        assertNull(monitor.denied.first())
    }
}
