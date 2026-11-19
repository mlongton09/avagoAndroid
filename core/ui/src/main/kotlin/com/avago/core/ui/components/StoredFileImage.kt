package com.avago.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avago.core.data.domain.StoredFile

/**
 * Renders a v2 stored file. Image bytes are shown from the device-local copy (files captured on this
 * device, or already downloaded); otherwise a typed placeholder is drawn. v2 has no file download
 * endpoint yet (see docs "Catalog requests"), so remote-only files render as placeholders.
 */
@Composable
fun StoredFileImage(
    file: StoredFile?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val local = file?.localFile
    if (local != null && file.isImage) {
        AsyncImage(
            model = local,
            contentDescription = contentDescription ?: file.name,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (file?.isImage != false) Icons.Outlined.Image else Icons.Outlined.Description,
                contentDescription = contentDescription ?: file?.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun StoredFileThumbnail(file: StoredFile?, modifier: Modifier = Modifier) {
    Box(modifier = modifier) { StoredFileImage(file = file, modifier = Modifier.fillMaxSize()) }
}
