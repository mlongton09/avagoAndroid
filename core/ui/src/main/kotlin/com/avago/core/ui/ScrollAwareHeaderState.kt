package com.avago.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Scroll-direction–aware header state — mirrors iOS AssetsListViewController / InventoryListViewController
 * pattern: header hides on scroll down, reappears on scroll up.
 *
 * Direction change is triggered by any consecutive delta > 5px (matching iOS 5pt threshold).
 * Uses [onPostScroll] so we only react to scroll the list actually consumed, not phantom
 * events at boundaries.
 */
class ScrollAwareHeaderState : NestedScrollConnection {

    private val _headerVisible = mutableStateOf(true)
    val headerVisible: State<Boolean> = _headerVisible

    private var isScrollingDown = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = consumed.y
        if (abs(delta) > 5f) {
            val newDown = delta < 0 // negative y = list scrolling down (content moving up)
            if (newDown != isScrollingDown) {
                isScrollingDown = newDown
                _headerVisible.value = !newDown
            }
        }
        return Offset.Zero
    }

    fun show() {
        _headerVisible.value = true
        isScrollingDown = false
    }
}

@Composable
fun rememberScrollAwareHeaderState(): ScrollAwareHeaderState = remember { ScrollAwareHeaderState() }
