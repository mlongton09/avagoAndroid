package com.avago.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceBottomSheet(
    onCameraClick: () -> Unit,
    onPhotoLibraryClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.photo_camera)) },
            leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            modifier = Modifier.clickable {
                onCameraClick()
                onDismiss()
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.photo_library)) },
            leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.clickable {
                onPhotoLibraryClick()
                onDismiss()
            },
        )
    }
}
