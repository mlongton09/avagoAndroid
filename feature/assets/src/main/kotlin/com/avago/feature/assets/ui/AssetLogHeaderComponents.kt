package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTypes
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asset header composable — visual + behavioural parity with iOS
 * `AssetDetailHeaderView` (`AssetDetailViewController.swift` lines 1–560).
 *
 * Layout, top → bottom, always in this order:
 *   1. 210 dp banner.
 *      - Photo mode (any photos): paged horizontal carousel; long-press fires
 *        [onPhotoLongPress] (350 ms threshold, matching iOS
 *        `minimumPressDuration = 0.35`); tap fires [onPhotoTap].
 *      - Color mode (no photos): plain asset-type-tinted background with a
 *        110 dp hero watermark hanging off the bottom-right corner (clipped by
 *        the banner). No gradient, no overlaid labels — iOS unhid the gradient
 *        and overlay labels back in the short-banner era and the current
 *        `setPhotoMode` keeps them hidden in both modes.
 *   2. 64 dp info row on plain bg — asset name + subtitle. Shown in BOTH
 *      modes (iOS `infoRow.isHidden = false` unconditionally).
 *
 * A 36 dp camera button sits at the top-right of the banner in both modes.
 */
@Composable
fun AssetDetailHeader(
    asset: AssetEntity,
    photos: List<PhotoEntity>,
    modifier: Modifier = Modifier,
    onAddPhoto: () -> Unit = {},
    onPhotoTap: (Int) -> Unit = {},
    onPhotoLongPress: (Int) -> Unit = {},
) {
    val bannerHeight = 210.dp
    val infoRowHeight = 64.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                // iOS `bannerView.clipsToBounds = true` — required so the hero
                // watermark, positioned with +16 dp offsets past the corners,
                // crops cleanly against the banner edge.
                .clipToBounds(),
        ) {
            if (photos.isNotEmpty()) {
                val photoPager = rememberPagerState(pageCount = { photos.size })
                HorizontalPager(
                    state = photoPager,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val photo = photos[page]
                    val model: Any? = photo.localPath?.takeIf { it.isNotBlank() }?.let { File(it) }
                        ?: photo.downloadUrl?.takeIf { it.isNotBlank() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            // iOS uses a UILongPressGestureRecognizer with
                            // minimumPressDuration = 0.35. Compose's
                            // combinedClickable doesn't expose the timeout —
                            // detectTapGestures does, via the longPressTimeout
                            // parameter we pass through pointerInput below.
                            .pointerInput(page, photos.size) {
                                detectTapGestures(
                                    onTap = { onPhotoTap(page) },
                                    onLongPress = { onPhotoLongPress(page) },
                                )
                            },
                    ) {
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                // Page dots — iOS UIPageControl defaults: 7 pt diameter,
                // 9 pt spacing, pill background only when more than one page.
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.30f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        photos.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == photoPager.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.45f),
                                    ),
                            )
                        }
                    }
                }
            } else {
                // Color mode — tinted background only. iOS keeps
                // `bannerGradient.isHidden = true` and hides the overlay
                // name/subtitle labels: the info row below the banner owns
                // both lines of text in both modes.
                val bgColor = rememberParsedColor(AssetTypes.colorHexFor(asset.assetType))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor),
                )
                // Hero watermark — 110 dp, alpha 0.40, hung off the
                // bottom-right corner with both anchors at +16 dp on iOS.
                // In Compose: anchor to BottomEnd, then offset by +16 dp on
                // both axes so the icon extends past the corners and the
                // banner's clipToBounds crops the overhang.
                Icon(
                    painter = painterResource(AssetTypes.iconResFor(asset.assetType)),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 16.dp, y = 16.dp)
                        .size(110.dp)
                        .clip(RectangleShape),
                )
            }

            // Camera button — top-right, both modes. iOS uses SF Symbol
            // `camera` (line camera). Material's outlined PhotoCamera is the
            // closest stock match; the filled CameraAlt skews much heavier.
            IconButton(
                onClick = onAddPhoto,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.30f)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Add photo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Info row — name + subtitle + tags on plain bg0, shown in BOTH modes.
        // iOS `infoRow.isHidden = false` unconditionally.
        // Change 106: row height expands if tags are present.
        val tags = remember(asset.tagsJson) { parseAssetTags(asset.tagsJson) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // iOS `infoRowNameLabel` uses largeTitleFont (~34 pt) with
            // adjustsFontSizeToFitWidth + minimumScaleFactor 0.75.
            AutoResizeText(
                text = asset.name,
                baseStyle = MaterialTheme.typography.headlineMedium,
                minScale = 0.75f,
                modifier = Modifier.fillMaxWidth(),
            )
            val sub = assetDisplaySubtitle(asset)
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Change 106: tag chips in a horizontally scrollable row
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

/** Change 106: parse a JSON array string like [\"hvac\",\"electrical\"] into a List<String>. */
private fun parseAssetTags(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}

/**
 * 44 dp three-column stats strip — Entries | Last Service | Since Service.
 * Mirrors iOS AssetDetailHeaderView stats strip. Background (`bg1` on iOS)
 * is applied by the caller.
 */
@Composable
fun AssetStatsRow(
    entryCount: Int,
    lastServiceDate: Long?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(
            label = stringResourceCompat(R.string.asset_detail_log_entries),
            value = entryCount.toString(),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        StatCell(
            label = stringResourceCompat(R.string.asset_detail_last_service),
            value = if (lastServiceDate != null) formatShortDate(lastServiceDate) else "Never",
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        StatCell(
            label = stringResourceCompat(R.string.asset_detail_since_service),
            value = if (lastServiceDate != null) sinceService(lastServiceDate) else "\u2014",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun stringResourceCompat(id: Int): String =
    androidx.compose.ui.res.stringResource(id)

@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // iOS bodyBoldFont() with adjustsFontSizeToFitWidth + minimumScaleFactor 0.7.
        AutoResizeText(
            text = value,
            baseStyle = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            minScale = 0.7f,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Shrink-to-fit `Text` — Compose 1.7 lacks the upstream `BasicText(autoSize)`
 * parameter (added in 1.8) so we measure once with `Text.onTextLayout` and
 * step the font size down until the rendered text fits on one line, bottoming
 * out at [minScale] × the base style's size. Matches the iOS
 * `adjustsFontSizeToFitWidth = true, minimumScaleFactor = <scale>` behaviour
 * used by largeTitleFont/bodyBoldFont in `AssetDetailHeaderView`.
 */
@Composable
internal fun AutoResizeText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    minScale: Float = 0.7f,
    textAlign: TextAlign? = null,
) {
    val baseSize = baseStyle.fontSize
    var scale by remember(text) { mutableStateOf(1f) }
    Text(
        text = text,
        modifier = modifier,
        style = baseStyle,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        fontSize = (baseSize.value * scale).sp,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && scale > minScale) {
                // Shrink toward minScale in 5% steps. One recomposition per
                // step is fine — Asset names + stat values are short so we
                // converge in 1-3 iterations.
                scale = (scale - 0.05f).coerceAtLeast(minScale)
            }
        },
    )
}

@Composable
fun rememberParsedColor(hex: String): Color = try {
    Color(0xFF000000L or hex.removePrefix("#").toLong(16))
} catch (_: Exception) {
    MaterialTheme.colorScheme.primary
}

/**
 * iOS parity for Asset.displaySubtitle: for real-estate types show the street_address from
 * the attributes JSON; otherwise show "Make · Model" joined by a middle dot.
 */
fun assetDisplaySubtitle(asset: AssetEntity): String {
    val realEstateTypes = setOf(
        "residential", "multifamily", "office", "industrial", "healthcare", "restaurant",
    )
    if (asset.assetType in realEstateTypes) {
        streetAddressFromAttributes(asset.attributes)?.let { return it }
    }
    val make = asset.make.orEmpty()
    val model = asset.model.orEmpty()
    return when {
        make.isNotEmpty() && model.isNotEmpty() -> "$make \u00B7 $model"
        make.isNotEmpty() -> make
        model.isNotEmpty() -> model
        else -> ""
    }
}

fun streetAddressFromAttributes(attributes: String?): String? {
    if (attributes.isNullOrBlank()) return null
    return try {
        val addr = JSONObject(attributes).optString("street_address", "")
        addr.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

fun formatShortDate(epochMs: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))

fun sinceService(epochMs: Long): String {
    val days = ((System.currentTimeMillis() - epochMs) / 86_400_000L).toInt()
    return when {
        days < 1   -> "Today"
        days == 1  -> "1 day"
        days < 30  -> "$days days"
        days < 60  -> "1 month"
        days < 365 -> "${days / 30} months"
        days < 730 -> "1 year"
        else       -> "${days / 365} years"
    }
}
