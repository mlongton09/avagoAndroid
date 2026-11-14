package com.avago.feature.rentalsops.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RentalOpsAccessTest {

    @Test
    fun `a driver without rental view lands on their deliveries and sees no operations`() {
        val a = RentalOpsAccess.from(setOf("rental.drive"), root = false)
        assertTrue(a.driverOnly)
        assertTrue(a.tasks)
        assertFalse(a.operations)
    }

    @Test
    fun `counter staff see the operations entry, not the driver landing`() {
        val a = RentalOpsAccess.from(setOf("rental.view", "rental.drive"), root = false)
        assertFalse(a.driverOnly)
        assertTrue(a.operations)
        assertFalse(RentalOpsAccess.from(setOf("rental.view"), root = false).driverOnly)
    }

    @Test
    fun `no rental permission hides everything`() {
        val a = RentalOpsAccess.from(emptySet(), root = false)
        assertFalse(a.any)
        assertFalse(a.operations)
        assertFalse(a.driverOnly)
    }
}
