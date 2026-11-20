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
import coil3.compose.SubcomposeAsyncImage
import com.avago.core.data.domain.FileVariant
import com.avago.core.data.domain.StoredFile

/**
 * Renders a v2 stored file. Images captured on this device are drawn from the local copy; verified
 * remote images are fetched through [StoredFileRequest] (`files/{id}/download`, cached on device).
 * Non-images, and images still loading or unavailable, show a typed placeholder.
 */
@Composable
fun StoredFileImage(
    file: StoredFile?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    variant: FileVariant = FileVariant.ORIGINAL,
) {
    val model = file?.imageModel(variant)
    if (model == null) {
        FilePlaceholder(file, modifier, contentDescription)
    } else {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription ?: file?.name,
            contentScale = contentScale,
            modifier = modifier,
            loading = { FilePlaceholder(file, Modifier.fillMaxSize(), null) },
            error = { FilePlaceholder(file, Modifier.fillMaxSize(), null) },
        )
    }
}

@Composable
private fun FilePlaceholder(file: StoredFile?, modifier: Modifier, contentDescription: String?) {
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

@Composable
fun StoredFileThumbnail(file: StoredFile?, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        StoredFileImage(file = file, modifier = Modifier.fillMaxSize(), variant = FileVariant.THUMBNAIL)
    }
}
