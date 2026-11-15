package com.avago.feature.rentals.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentals.R
import java.io.File
import java.util.UUID

/**
 * Camera capture of a document photo (ID / licence / insurance, G2): asks for the camera
 * permission, writes the JPEG under `filesDir/rental_photos` (offline-safe; the outbox uploads it)
 * and shows a thumbnail with Retake / Remove. [onPhoto] receives the absolute path.
 */
@Composable
internal fun DocumentPhotoRow(
    photoPath: String?,
    onPhoto: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.rentals_document_photo),
) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending?.let(::File)
        pending = null
        if (ok && f != null && f.length() > 0) onPhoto(f.absolutePath) else f?.delete()
    }
    fun launch() {
        val dir = File(context.filesDir, "rental_photos").apply { mkdirs() }
        val f = File(dir, "doc_${UUID.randomUUID()}.jpg")
        pending = f.absolutePath
        take.launch(FileProvider.getUriForFile(context, "${context.packageName}.provider", f))
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) launch()
    }
    val start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch()
        else ask.launch(Manifest.permission.CAMERA)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
        if (photoPath == null) {
            OutlinedButton(onClick = start, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp))
                Text(label, Modifier.padding(start = AvagoSpacing.sm))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                if (!LocalInspectionMode.current) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rentals_document_photo_added), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = start, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_retake)) }
                        TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_remove)) }
                    }
                }
            }
        }
        if (denied) {
            Text(
                stringResource(R.string.rentals_readiness_camera_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
