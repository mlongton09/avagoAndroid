package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.feature.assets.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class GalleryPhotoUi(
    val id: String,
    val assetName: String,
    val createdAt: Long,
    val imageModel: Any?,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val dbFactory: DatabaseFactory,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val photos = identityManager.activeAccountId
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(emptyList())
            } else {
                val db = dbFactory.get(accountId)
                combine(
                    db.photoDao().observeAll(accountId),
                    db.assetDao().observeAll(accountId),
                ) { photos, assets ->
                    val assetNames = assets.associate { it.assetId to it.name }
                    photos
                        .filter { it.entityType.equals("asset", ignoreCase = true) }
                        .sortedByDescending { it.createdAt }
                        .mapNotNull { photo -> photo.toGalleryUi(assetNames[photo.entityId]) }
                }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun GalleryScreen(
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val photos by viewModel.photos.collectAsState()
    var selectedIndex by remember { mutableIntStateOf(-1) }

    if (photos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.gallery_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                GalleryPhotoCard(
                    photo = photo,
                    onClick = { selectedIndex = index },
                )
            }
        }
    }

    if (selectedIndex in photos.indices) {
        GalleryPhotoDialog(
            photos = photos,
            selectedIndex = selectedIndex,
            onIndexChange = { selectedIndex = it },
            onDismiss = { selectedIndex = -1 },
        )
    }
}

@Composable
private fun GalleryPhotoCard(
    photo: GalleryPhotoUi,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = photo.imageModel,
            contentDescription = photo.assetName,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(8.dp)) {
            Text(
                text = photo.assetName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatGalleryDate(photo.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GalleryPhotoDialog(
    photos: List<GalleryPhotoUi>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val photo = photos[selectedIndex]
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            shape = RoundedCornerShape(0.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = photo.assetName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatGalleryDate(photo.createdAt),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(R.string.gallery_item_count, selectedIndex + 1, photos.size),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = photo.imageModel,
                        contentDescription = photo.assetName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = selectedIndex > 0,
                        onClick = { onIndexChange(selectedIndex - 1) },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.size(48.dp))
                    IconButton(
                        enabled = selectedIndex < photos.lastIndex,
                        onClick = { onIndexChange(selectedIndex + 1) },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun PhotoEntity.toGalleryUi(assetName: String?): GalleryPhotoUi? {
    val model = when {
        !localPath.isNullOrBlank() && File(localPath).exists() -> File(localPath)
        !localPath.isNullOrBlank() -> localPath
        !downloadUrl.isNullOrBlank() -> downloadUrl
        else -> null
    } ?: return null
    return GalleryPhotoUi(
        id = photoId,
        assetName = assetName.orEmpty().ifBlank { "Untitled" },
        createdAt = createdAt,
        imageModel = model,
    )
}

private fun formatGalleryDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
