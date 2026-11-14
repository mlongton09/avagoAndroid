package com.avago.core.design.chart

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * Palette contrast (WCAG 2.2) and layout rules from AvagoWeb
 * docs/chart-style-guide.md §§ 2, 4, 7. Mirrors web `palette.test.ts`.
 */
class ChartThemeTest {

    private val lightSurface = Color(0xFFFFFFFF)
    private val darkSurface = Color(0xFF161B22)

    private fun channel(c: Float): Double =
        if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertContrast(fg: Color, bg: Color, min: Double, name: String) {
        val ratio = contrast(fg, bg)
        assertTrue(ratio >= min, "$name contrast ${"%.2f".format(ratio)} < $min")
    }

    @Test
    fun `categorical and semantic marks have 3 to 1 non-text contrast`() {
        listOf(ChartTheme.Light to lightSurface, ChartTheme.Dark to darkSurface).forEach { (c, surface) ->
            val mode = if (c.isDark) "dark" else "light"
            c.categorical.forEachIndexed { i, col -> assertContrast(col, surface, 3.0, "$mode categorical[$i]") }
            c.aging.forEachIndexed { i, col -> assertContrast(col, surface, 3.0, "$mode aging[$i]") }
            assertContrast(c.positive, surface, 3.0, "$mode positive")
            assertContrast(c.negative, surface, 3.0, "$mode negative")
            assertContrast(c.neutral, surface, 3.0, "$mode neutral")
            assertContrast(c.target, surface, 3.0, "$mode target")
            assertContrast(c.zeroLine, surface, 3.0, "$mode zeroLine")
        }
    }

    @Test
    fun `chart text meets AA 4_5 to 1`() {
        listOf(ChartTheme.Light to lightSurface, ChartTheme.Dark to darkSurface).forEach { (c, surface) ->
            val mode = if (c.isDark) "dark" else "light"
            assertContrast(c.text, surface, 4.5, "$mode text")
            assertContrast(c.textMuted, surface, 4.5, "$mode textMuted")
            assertContrast(c.textSubtle, surface, 4.5, "$mode textSubtle")
        }
    }

    @Test
    fun `palette sizes and primary hue match the web tokens`() {
        assertEquals(8, ChartTheme.Light.categorical.size)
        assertEquals(8, ChartTheme.Dark.categorical.size)
        assertEquals(5, ChartTheme.Light.aging.size)
        assertEquals(5, ChartTheme.Dark.aging.size)
        assertEquals(Color(0xFF2563EB), ChartTheme.Light.categorical(0))
        assertEquals(Color(0xFF60A5FA), ChartTheme.Dark.categorical(0))
        // Index wraps rather than crashing (callers group tails into "Other" first).
        assertEquals(ChartTheme.Light.categorical(0), ChartTheme.Light.categorical(8))
    }

    @Test
    fun `labelStep skips category labels instead of overlapping`() {
        assertEquals(1, ChartLayout.labelStep(widestLabelPx = 30f, slotPx = 40f))
        assertEquals(1, ChartLayout.labelStep(widestLabelPx = 40f, slotPx = 40f))
        assertEquals(2, ChartLayout.labelStep(widestLabelPx = 41f, slotPx = 40f))
        assertEquals(3, ChartLayout.labelStep(widestLabelPx = 90f, slotPx = 30f))
        assertEquals(1, ChartLayout.labelStep(widestLabelPx = 90f, slotPx = 0f))
    }

    @Test
    fun `data labels only when they fit and at most 16 bars`() {
        assertTrue(ChartLayout.dataLabelFits(labelWidthPx = 30f, slotPx = 40f, bars = 12))
        assertFalse(ChartLayout.dataLabelFits(labelWidthPx = 39f, slotPx = 40f, bars = 12))
        assertFalse(ChartLayout.dataLabelFits(labelWidthPx = 10f, slotPx = 40f, bars = 17))
        assertFalse(ChartLayout.dataLabelFits(labelWidthPx = 10f, slotPx = 40f, bars = 0))
    }

    @Test
    fun `groupTail keeps the largest parts and sums the rest into Other`() {
        val parts = listOf("a" to 1.0, "b" to 5.0, "c" to 3.0, "d" to 2.0, "e" to 4.0, "f" to 0.5, "g" to 0.25)
        val grouped = ChartLayout.groupTail(parts, maxSlices = 6, other = "Other")
        assertEquals(listOf("b", "e", "c", "d", "a", "Other"), grouped.map { it.first })
        assertEquals(0.75, grouped.last().second, 1e-9)
        assertEquals(parts.sumOf { it.second }, grouped.sumOf { it.second }, 1e-9)

        val few = ChartLayout.groupTail(listOf("x" to 1.0, "y" to 2.0), maxSlices = 6, other = "Other")
        assertEquals(listOf("y", "x"), few.map { it.first })
    }
}
