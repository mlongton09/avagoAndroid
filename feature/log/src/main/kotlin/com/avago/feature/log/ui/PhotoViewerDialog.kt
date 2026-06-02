package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.PhotoEntity
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen photo viewer — Android equivalent of iOS
 * `PhotoSliderViewController` opened from `AssetDetailHeaderView`'s photo tap.
 *
 * Chrome (top bar with close + overflow → set as cover / delete) toggles on
 * tap. Pinch-to-zoom + drag-to-pan are supported per page via
 * `detectTransformGestures`; horizontal swipe between photos comes free with
 * [HorizontalPager]. Mounted as a fullscreen [Dialog] so the asset / log
 * screen stays underneath.
 */
@Composable
fun PhotoViewerDialog(
    photos: List<PhotoEntity>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDeletePhoto: (photoId: String) -> Unit,
    onSetCoverPhoto: (photoId: String) -> Unit,
) {
    if (photos.isEmpty()) {
        onDismiss(); return
    }
    val safeStart = startIndex.coerceIn(0, photos.lastIndex)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val pagerState = rememberPagerState(
            initialPage = safeStart,
            pageCount = { photos.size },
        )
        var chromeVisible by remember { mutableStateOf(true) }
        var menuOpen by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableAsyncImage(
                    photo = photos[page],
                    onTap = { chromeVisible = !chromeVisible },
                )
            }

            if (chromeVisible) {
                // Top bar — close + overflow.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Photo actions",
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Set as cover") },
                                onClick = {
                                    menuOpen = false
                                    onSetCoverPhoto(photos[pagerState.currentPage].photoId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuOpen = false
                                    val toDelete = photos[pagerState.currentPage].photoId
                                    onDeletePhoto(toDelete)
                                    // Closing the viewer is the friendliest
                                    // default after a destructive action;
                                    // iOS pops back to the asset screen too.
                                    onDismiss()
                                },
                            )
                        }
                    }
                }

                // Page dots — bottom center, same 7 dp/9 dp parity as the
                // header carousel.
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        photos.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.3f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableAsyncImage(
    photo: PhotoEntity,
    onTap: () -> Unit,
) {
    val model: Any? = photo.localPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: photo.downloadUrl?.takeIf { it.isNotBlank() }
    var scale by remember(photo.photoId) { mutableFloatStateOf(1f) }
    var offsetX by remember(photo.photoId) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo.photoId) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.photoId) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        // Quick toggle between 1× and 2× to match iOS pinch-zoom
                        // muscle memory; ResetOffsets when collapsing.
                        if (scale > 1f) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    },
                )
            }
            .pointerInput(photo.photoId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    scale = next
                    if (next > 1f) {
                        // Pan is only meaningful when zoomed; clamp loosely so
                        // the image stays roughly on screen. The bounds depend
                        // on layout size — keep it simple to avoid a
                        // measurement round-trip.
                        val maxOffset = (next - 1f) * 1000f
                        offsetX = min(maxOffset, max(-maxOffset, offsetX + pan.x))
                        offsetY = min(maxOffset, max(-maxOffset, offsetY + pan.y))
                    } else {
                        offsetX = 0f; offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        } else {
            // Placeholder when neither local nor remote URL is available —
            // keeps the viewer from rendering an entirely blank page.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "Photo unavailable", color = Color.White)
            }
        }
    }
}
