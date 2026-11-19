package com.avago.feature.rentals.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.Attachment
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentals.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

// Rentals UX pass 2 kit: unit photos, overflow menu with a destructive group, "Show all (N)",
// readable width on tablets and the refresh-failed banner. See docs/rentals-ux-polish-android.md.

/** Rows shown before "Show all (N)" in long histories (customer orders, unit bookings). */
internal const val PREVIEW_ROWS = 3

/** Permission for recording payments (`rental.payment_record`, same key as iOS). */
internal const val PERMISSION_PAYMENT_RECORD = "rental.payment_record"
internal const val PERMISSION_REFUND = "rental.refund"
internal const val PERMISSION_INVOICE = "rental.invoice_issue"

/** Ledger kinds that pay money back (need [PERMISSION_REFUND]). */
internal val REFUND_KINDS = setOf("refund", "deposit_refunded")

/** Ledger kinds and methods a manual payment can record (`rental.rental_orders.record_payment`). */
internal val PAYMENT_KINDS = listOf("payment", "deposit_received", "refund", "deposit_refunded")
internal val PAYMENT_METHODS = listOf("cash", "card_external", "bank_transfer", "cheque", "mobile_money", "direct_debit", "voucher", "other")

/** Lifecycle actions that end an order early; always red, grouped last and confirmed. */
internal val DESTRUCTIVE_ORDER_ACTIONS = setOf("cancel", "no-show")

private val ORDER_PRIMARY_PRIORITY = listOf("checkout", "checkin", "close", "reserve", "hold", "quote")

/**
 * The single filled next step of an order: hand-overs first, then closing, then the booking
 * ladder. Never extend / swap / off-rent (secondary) and never a destructive action.
 */
internal fun primaryOrderAction(actions: List<String>): String? =
    ORDER_PRIMARY_PRIORITY.firstOrNull { it in actions }

/**
 * Unit photos from the v2 asset attachments, keyed by assetId. Read-only and screen-scoped: every
 * [UnitThumbnail] on a screen shares one query.
 */
@HiltViewModel
class RentalPhotosViewModel @Inject constructor(assets: AssetRepository) : ViewModel() {
    val thumbnails: StateFlow<Map<String, File>> = assets.observeImages()
        .map { assetThumbnails(it) }
        .flowOn(Dispatchers.Default)
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/**
 * Best photo per asset: the lowest sort order whose bytes are on this device (v2 has no file
 * download endpoint yet, so remote-only pictures fall back to the status icon).
 */
internal fun assetThumbnails(images: List<Attachment>, fileExists: (File) -> Boolean = { it.exists() }): Map<String, File> =
    images.asSequence()
        .filter { it.assetId != null && it.file?.isImage == true }
        .groupBy { it.assetId!! }
        .mapNotNull { (assetId, list) ->
            list.sortedWith(compareBy<Attachment> { it.sortOrder }.thenBy { it.createdAt })
                .firstNotNullOfOrNull { a -> a.file?.localFile?.takeIf(fileExists) }
                ?.let { assetId to it }
        }
        .toMap()

/**
 * Unit photo (cropped, rounded) with the status icon on a tonal square underneath — shown while
 * loading, when the unit has no photo, or if the image fails. Decorative: the row text names
 * the unit, so TalkBack skips it.
 */
@Composable
internal fun UnitThumbnail(
    assetId: String?,
    icon: ImageVector,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    size: Dp = AvagoSpacing.thumbnail,
) {
    val model = if (LocalInspectionMode.current || assetId == null) null else rememberUnitPhoto(assetId)
    val c = toneColors(tone)
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(c.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(size * 0.55f))
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun rememberUnitPhoto(assetId: String): Any? {
    val vm: RentalPhotosViewModel = hiltViewModel()
    val map by vm.thumbnails.collectAsStateWithLifecycle()
    return map[assetId]
}

/** One overflow entry. [destructive] entries are grouped last, below a divider, in red. */
internal data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * "More actions" (⋮) for rare and destructive actions so the screen keeps a single primary.
 * Destructive entries are separated and red (text + icon, never colour alone); callers confirm
 * them in a dialog.
 */
@Composable
internal fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.rentals_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val (normal, destructive) = actions.partition { !it.destructive }
            normal.forEach { a -> OverflowItem(a) { open = false } }
            if (normal.isNotEmpty() && destructive.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = AvagoSpacing.xs))
            destructive.forEach { a -> OverflowItem(a) { open = false } }
        }
    }
}

@Composable
private fun OverflowItem(a: MenuAction, close: () -> Unit) {
    val danger = toneColors(StatusTone.Danger).content
    DropdownMenuItem(
        text = { Text(a.label) },
        leadingIcon = { Icon(a.icon, contentDescription = null) },
        onClick = { close(); a.onClick() },
        colors = if (a.destructive) {
            MenuDefaults.itemColors(textColor = danger, leadingIconColor = danger)
        } else {
            MenuDefaults.itemColors()
        },
        modifier = Modifier.heightIn(min = MinTouch),
    )
}

/** One swipe action (also exposed to TalkBack as a custom action). */
internal data class SwipeAction(val label: String, val icon: ImageVector, val tone: StatusTone, val onAction: () -> Unit)

/** TalkBack custom actions mirroring swipe gestures; apply to the row's focusable (clickable) node. */
internal fun Modifier.swipeA11y(actions: List<SwipeAction>): Modifier =
    if (actions.isEmpty()) this else semantics { customActions = actions.map { a -> CustomAccessibilityAction(a.label) { a.onAction(); true } } }

/**
 * Reveal-and-act swipe row: swipe right runs [start], swipe left runs [end]; the row springs
 * back (nothing is dismissed). [content] receives the modifier carrying the TalkBack actions
 * ([start], [end] and [more]) for its clickable node.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeActionsRow(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    more: List<SwipeAction> = emptyList(),
    shape: Shape = RectangleShape,
    content: @Composable (a11y: Modifier) -> Unit,
) {
    val a11y = Modifier.swipeA11y(listOfNotNull(start, end) + more)
    if (start == null && end == null) {
        Box(modifier) { content(a11y) }
        return
    }
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val action = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> start
                SwipeToDismissBoxValue.EndToStart -> end
                else -> null
            }
            if (action != null) {
                haptics.tick()
                action.onAction()
            }
            false
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        backgroundContent = {
            val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (fromStart) start else if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) end else null
            if (action != null) {
                val c = toneColors(action.tone)
                Box(
                    Modifier.fillMaxSize().clip(shape).background(c.container).padding(horizontal = AvagoSpacing.xl),
                    contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                        Icon(action.icon, contentDescription = null, tint = c.content)
                        Text(action.label, color = c.content, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { content(a11y) }
}

/** "Show all (N)" / "Show less" under a list trimmed to [PREVIEW_ROWS]. Hidden when nothing is trimmed. */
@Composable
internal fun ShowAllRow(total: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    if (total <= PREVIEW_ROWS) return
    TextButton(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .padding(horizontal = AvagoSpacing.xs),
    ) {
        Text(
            if (expanded) stringResource(R.string.rentals_show_less) else stringResource(R.string.rentals_show_all, total),
            modifier = Modifier.weight(1f),
        )
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
    }
}

/**
 * Centres content at a readable line length (≤ [AvagoSpacing.readableMaxWidth]) on tablets and
 * landscape; a no-op on phones.
 */
internal fun Modifier.readableWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = AvagoSpacing.readableMaxWidth)
    .fillMaxWidth()

/** Shown when a pull-to-refresh / Sync now did not complete: saved data stays, Retry is one tap away. */
@Composable
internal fun RefreshFailedBanner(refresh: SyncRefresh, modifier: Modifier = Modifier) {
    if (!refresh.failed) return
    ErrorBanner(
        stringResource(R.string.rentals_refresh_failed),
        modifier = modifier,
        onRetry = refresh.onRefresh,
        retryLabel = stringResource(R.string.rentals_retry),
        tone = StatusTone.Warning,
        icon = Icons.Default.CloudOff,
    )
}
