package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Public reusable header that mirrors iOS `AssetDetailHeaderView` — the banner that sits
 * above the log list on the Asset detail / log screen.
 *
 * Photo mode: paged horizontal carousel of asset photos with page dots, plus an info row
 *   below the photo showing the asset name + subtitle on plain bg.
 * Color mode (no photos): asset-type tinted banner with the hero icon as a 40 %-opacity
 *   watermark and the name + subtitle overlaid at the bottom.
 *
 * A 36 dp camera button sits at the top-right of the banner in both modes.
 * Long-pressing a photo invokes [onPhotoLongPress] with the page index so callers can
 * surface a per-photo action sheet (delete / share / set as cover).
 */
@OptIn(ExperimentalFoundationApi::class)
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
                .height(bannerHeight),
        ) {
            if (photos.isNotEmpty()) {
                val photoPager = rememberPagerState(pageCount = { photos.size })
                HorizontalPager(
                    state = photoPager,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val photo = photos[page]
                    val model: Any? = photo.localPath?.takeIf { it.isNotBlank() }?.let { File(it) }
                        ?: photo.downloadUrl
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .combinedClickable(
                                onClick = { onPhotoTap(page) },
                                onLongClick = { onPhotoLongPress(page) },
                            ),
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
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.30f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        photos.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
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
                val bgColor = rememberParsedColor(AssetTypes.colorHexFor(asset.assetType))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Black.copy(alpha = 0.10f),
                                    1.0f to Color.Black.copy(alpha = 0.72f),
                                ),
                            ),
                        ),
                )
                Icon(
                    painter = painterResource(AssetTypes.iconResFor(asset.assetType)),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(110.dp),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, end = 134.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = assetDisplaySubtitle(asset)
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

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
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Add photo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (photos.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(infoRowHeight)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            }
        }
    }
    HorizontalDivider()
}

/**
 * 44 dp three-column stats strip — Entries | Last Service | Since Service — mirrors the
 * iOS AssetDetailHeaderView stats strip. Use `MaterialTheme.colorScheme.surfaceVariant`
 * (≈ iOS bg1) for the background, applied by the caller.
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
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
