package com.avago.core.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.PartIdentity
import com.avago.core.ui.R

/** Editable part identity of a cost line; invalid fields are flagged with the server's rule. */
@Composable
fun PartIdentityFields(
    identity: PartIdentity,
    onChange: (PartIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val invalid = identity.invalidFields()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PartIdentity.Field.entries.forEach { field ->
            val error = field in invalid
            OutlinedTextField(
                value = field.of(identity).orEmpty(),
                onValueChange = { onChange(identity.with(field, it)) },
                label = { Text(stringResource(field.label())) },
                isError = error,
                supportingText = if (error) {
                    { Text(field.errorText()) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = when (field) {
                    PartIdentity.Field.GTIN -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    PartIdentity.Field.COUNTRY -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    else -> KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun PartIdentity.Field.label(): Int = when (this) {
    PartIdentity.Field.PART_NUMBER -> R.string.part_identity_part_number
    PartIdentity.Field.SERIAL_NUMBER -> R.string.part_identity_serial_number
    PartIdentity.Field.LOT_NUMBER -> R.string.part_identity_lot_number
    PartIdentity.Field.GTIN -> R.string.part_identity_gtin
    PartIdentity.Field.PRODUCTION_DATE -> R.string.part_identity_production_date
    PartIdentity.Field.MANUFACTURER_ID -> R.string.part_identity_manufacturer_id
    PartIdentity.Field.REVISION -> R.string.part_identity_revision
    PartIdentity.Field.MODEL_NUMBER -> R.string.part_identity_model_number
    PartIdentity.Field.COUNTRY -> R.string.part_identity_country
}

@Composable
private fun PartIdentity.Field.errorText(): String = when (this) {
    PartIdentity.Field.GTIN -> stringResource(R.string.part_identity_error_gtin)
    PartIdentity.Field.COUNTRY -> stringResource(R.string.part_identity_error_country)
    else -> stringResource(R.string.part_identity_error_length, maxLength)
}

/**
 * Photos linked to one subject (e.g. a fitted part): thumbnails with a remove button, plus an
 * "Add photo" chip that opens the system photo picker.
 */
@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    onAdd: (uri: String) -> Unit,
    onRemove: (attachmentId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onAdd(it.toString()) }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) {
                StoredFileThumbnail(attachment.file, Modifier.size(64.dp))
                IconButton(
                    onClick = { onRemove(attachment.id) },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.attachment_remove),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        item {
            AssistChip(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text(stringResource(R.string.attachment_add_photo)) },
                leadingIcon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
            )
        }
    }
}
