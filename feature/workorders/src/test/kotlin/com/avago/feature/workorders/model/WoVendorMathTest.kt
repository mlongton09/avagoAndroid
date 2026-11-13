package com.avago.feature.workorders.model

import com.avago.feature.workorders.model.WoVendorMath.ActualSource
import com.avago.feature.workorders.model.WoVendorMath.Level
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WoVendorMathTest {

    @Test
    fun `variance within tolerance is on estimate`() {
        val v = WoVendorMath.costVariance(1000.0, 1040.0)!!
        assertEquals(40.0, v.diff)
        assertEquals(4.0, v.pct)
        assertEquals(Level.ON, v.level)
    }

    @Test
    fun `variance over and under`() {
        assertEquals(Level.OVER, WoVendorMath.costVariance(100.0, 120.0)!!.level)
        val under = WoVendorMath.costVariance(200.0, 150.0)!!
        assertEquals(-50.0, under.diff)
        assertEquals(-25.0, under.pct)
        assertEquals(Level.UNDER, under.level)
    }

    @Test
    fun `zero estimate has null pct and level by sign`() {
        val v = WoVendorMath.costVariance(0.0, 10.0)!!
        assertNull(v.pct)
        assertEquals(Level.OVER, v.level)
        assertEquals(Level.ON, WoVendorMath.costVariance(0.0, 0.0)!!.level)
    }

    @Test
    fun `missing inputs return null`() {
        assertNull(WoVendorMath.costVariance(null, 10.0))
        assertNull(WoVendorMath.costVariance(10.0, null))
        assertNull(WoVendorMath.costVariance(Double.NaN, 10.0))
    }

    @Test
    fun `actual cost prefers invoice then positive lines then wo total`() {
        assertEquals(ActualSource.INVOICE, WoVendorMath.actualCost(50.0, 40.0, 30.0)!!.source)
        assertEquals(ActualSource.LINES, WoVendorMath.actualCost(null, 40.0, 30.0)!!.source)
        assertEquals(ActualSource.WO, WoVendorMath.actualCost(null, 0.0, 30.0)!!.source)
        assertNull(WoVendorMath.actualCost(null, 0.0, null))
    }
}
