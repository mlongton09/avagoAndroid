package com.avago.feature.inventory.partissues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartIssueDetailScreen(
    issueId: String,
    onBack: () -> Unit,
    viewModel: PartIssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(issueId) { viewModel.load(issueId) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.part_issue_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val issue = state.issue ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.part_issues_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Header
            Text(
                if (issue.issueType == "transfer") stringResource(R.string.part_issues_type_transfer)
                else stringResource(R.string.part_issues_type_issue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(dateFmt.format(Date(issue.issuedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (issue.referenceId != null) {
                LabelValue(stringResource(R.string.part_issue_field_reference), "${issue.referenceType ?: "ref"}: ${issue.referenceId}")
            }
            if (issue.issueType == "transfer") {
                if (issue.fromLocationId != null) LabelValue(stringResource(R.string.part_issue_field_from_location), issue.fromLocationId)
                if (issue.toLocationId != null) LabelValue(stringResource(R.string.part_issue_field_to_location), issue.toLocationId)
            } else {
                if (issue.locationId != null) LabelValue(stringResource(R.string.part_issue_field_location), issue.locationId)
            }
            if (issue.notes != null) LabelValue(stringResource(R.string.part_issue_field_notes), issue.notes)

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.part_issue_lines_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            if (state.lines.isEmpty()) {
                Text(stringResource(R.string.part_issue_no_lines), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                state.lines.forEach { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(viewModel.partName(line.partId), style = MaterialTheme.typography.bodyMedium)
                            if (line.notes != null) {
                                Text(line.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.common_qty_format, line.quantity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (line.unitCost != null) {
                                Text(stringResource(R.string.common_currency_format, "", line.unitCost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
