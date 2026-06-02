package com.avago.feature.log.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.ui.PhotoSourceBottomSheet
import com.avago.feature.assets.ui.AssetDetailHeader
import com.avago.feature.assets.ui.AssetStatsRow
import java.io.File

/**
 * Composable rich asset header that sits above the log list when the user navigates into
 * `LogListScreen` from a specific asset. Visual + behavioral parity with iOS
 * `AssetDetailHeaderView`:
 *
 * - 210 dp banner with photo paging carousel (or color-mode tinted background + hero icon)
 * - 64 dp info row (photo mode only) showing name + subtitle
 * - 44 dp three-column stats strip (Entries | Last Service | Since Service) on `surfaceVariant`
 * - Top-right camera button always visible (both modes)
 * - Long-press a photo to surface a delete / set-as-cover action sheet
 *
 * Camera + gallery launchers are wired here; on capture, [onAddPhotoUri] is invoked with
 * the resulting URI which the viewmodel persists + pushes through the existing
 * `SyncEngine.pushIfNeeded()` → `PhotoUploader.sweep` pipeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetLogHeader(
    asset: AssetEntity,
    photos: List<PhotoEntity>,
    entryCount: Int,
    lastServiceDate: Long?,
    onAddPhotoUri: (Uri) -> Unit,
    onDeletePhoto: (photoId: String) -> Unit,
    onSetCoverPhoto: (photoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var showPickerSheet by remember { mutableStateOf(false) }
    var longPressedIndex by remember { mutableStateOf<Int?>(null) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let(onAddPhotoUri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) pendingCaptureUri?.let(onAddPhotoUri)
        pendingCaptureUri = null
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "asset_captures").apply { mkdirs() }
        val outFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            outFile,
        )
        pendingCaptureUri = uri
        cameraLauncher.launch(uri)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        AssetDetailHeader(
            asset = asset,
            photos = photos,
            onAddPhoto = { showPickerSheet = true },
            onPhotoTap = { idx -> viewerStartIndex = idx },
            onPhotoLongPress = { idx -> longPressedIndex = idx },
        )
        AssetStatsRow(
            entryCount = entryCount,
            lastServiceDate = lastServiceDate,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }

    if (showPickerSheet) {
        PhotoSourceBottomSheet(
            onCameraClick = { launchCamera() },
            onPhotoLibraryClick = {
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            onDismiss = { showPickerSheet = false },
        )
    }

    longPressedIndex?.let { idx ->
        val photo = photos.getOrNull(idx)
        if (photo != null) {
            AlertDialog(
                onDismissRequest = { longPressedIndex = null },
                title = { Text("Photo options") },
                text = { Text("What would you like to do with this photo?") },
                confirmButton = {
                    TextButton(onClick = {
                        onSetCoverPhoto(photo.photoId)
                        longPressedIndex = null
                    }) { Text("Set as cover") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onDeletePhoto(photo.photoId)
                        longPressedIndex = null
                    }) { Text("Delete") }
                },
            )
        } else {
            longPressedIndex = null
        }
    }

    viewerStartIndex?.let { idx ->
        if (photos.isEmpty()) {
            viewerStartIndex = null
        } else {
            PhotoViewerDialog(
                photos = photos,
                startIndex = idx,
                onDismiss = { viewerStartIndex = null },
                onDeletePhoto = onDeletePhoto,
                onSetCoverPhoto = onSetCoverPhoto,
            )
        }
    }
}
