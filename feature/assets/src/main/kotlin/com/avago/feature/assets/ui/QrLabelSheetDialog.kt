package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.AssetLabelTemplate
import com.avago.core.ui.openPdfFile
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetQr
import com.avago.feature.assets.model.QrLabelLayout
import com.avago.feature.assets.viewmodel.QrLabelSheetViewModel

/**
 * Prints the public QR labels of [assetIds] (the assets the list currently shows) as one PDF
 * sheet with the chosen label template (the account default unless another saved template is picked)
 * in a layout the template prints on, then opens it in the system viewer.
 */
@Composable
fun QrLabelSheetDialog(
    assetIds: List<String>,
    onDismiss: () -> Unit,
    viewModel: QrLabelSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewerMissing by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.sheets.collect { file -> viewerMissing = !openPdfFile(context, file) }
    }

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(stringResource(R.string.asset_qr_labels_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.asset_qr_labels_count, assetIds.size))
                if (assetIds.size > AssetQr.MAX_BATCH_ASSETS) {
                    Text(
                        text = stringResource(R.string.asset_qr_labels_capped, AssetQr.MAX_BATCH_ASSETS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.templates.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.asset_qr_labels_template),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Column(Modifier.selectableGroup()) {
                        val default = state.accountDefault
                        ChoiceRow(
                            selected = state.templateId == null,
                            enabled = !state.busy,
                            label = if (default != null) {
                                stringResource(R.string.asset_qr_template_default, templateLabel(default))
                            } else {
                                stringResource(R.string.asset_qr_template_builtin)
                            },
                            onClick = { viewModel.selectTemplate(null) },
                        )
                        state.templates.filterNot { it.isDefault }.forEach { template ->
                            ChoiceRow(
                                selected = state.templateId == template.id,
                                enabled = !state.busy,
                                label = templateLabel(template),
                                onClick = { viewModel.selectTemplate(template.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.asset_qr_labels_layout),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(Modifier.selectableGroup()) {
                    state.layouts.forEach { layout ->
                        ChoiceRow(
                            selected = layout == state.layout,
                            enabled = !state.busy,
                            label = layoutLabel(layout),
                            onClick = { viewModel.selectLayout(layout) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                QrLabelJobStatus(state.busy, state.job?.assetCount?.takeIf { state.file != null }, viewerMissing)
                state.job?.templateName?.takeIf { state.file != null }?.let { name ->
                    Text(
                        text = stringResource(R.string.asset_qr_labels_template_used, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error?.let { error ->
                    Text(
                        text = stringResource(R.string.asset_qr_labels_failed, error.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            val file = state.file
            if (file != null && !state.busy) {
                TextButton(onClick = { viewerMissing = !openPdfFile(context, file) }) {
                    Text(stringResource(R.string.asset_qr_labels_open))
                }
            } else {
                TextButton(
                    onClick = { viewModel.generate(assetIds) },
                    enabled = !state.busy && assetIds.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.asset_qr_labels_generate))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) {
                Text(stringResource(R.string.asset_cancel))
            }
        },
    )
}

@Composable
private fun QrLabelJobStatus(busy: Boolean, renderedCount: Int?, viewerMissing: Boolean) {
    when {
        busy -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.asset_qr_labels_rendering), style = MaterialTheme.typography.bodySmall)
        }
        renderedCount != null && viewerMissing -> Text(
            text = stringResource(R.string.asset_qr_labels_no_viewer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        renderedCount != null -> Text(
            text = stringResource(R.string.asset_qr_labels_ready, renderedCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun templateLabel(template: AssetLabelTemplate): String = stringResource(
    R.string.asset_qr_template_option,
    template.name,
    template.widthMm.stripTrailingZeros().toPlainString(),
    template.heightMm.stripTrailingZeros().toPlainString(),
)

@Composable
private fun layoutLabel(layout: QrLabelLayout): String = when (layout) {
    QrLabelLayout.LETTER -> stringResource(R.string.asset_qr_layout_letter)
    QrLabelLayout.A4 -> stringResource(R.string.asset_qr_layout_a4)
    QrLabelLayout.THERMAL -> stringResource(R.string.asset_qr_layout_thermal_roll)
    QrLabelLayout.AVERY_5160 -> stringResource(R.string.asset_qr_layout_avery, "5160")
    QrLabelLayout.AVERY_5163 -> stringResource(R.string.asset_qr_layout_avery, "5163")
    QrLabelLayout.AVERY_L7160 -> stringResource(R.string.asset_qr_layout_avery, "L7160")
    QrLabelLayout.AVERY_L7163 -> stringResource(R.string.asset_qr_layout_avery, "L7163")
    QrLabelLayout.THERMAL_2X1IN -> stringResource(R.string.asset_qr_layout_thermal, "2 × 1 in")
    QrLabelLayout.THERMAL_50X25MM -> stringResource(R.string.asset_qr_layout_thermal, "50 × 25 mm")
}
