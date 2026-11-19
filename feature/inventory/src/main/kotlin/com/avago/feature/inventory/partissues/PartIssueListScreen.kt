package com.avago.feature.inventory.partissues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.formatQty
import com.avago.feature.inventory.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartIssueListScreen(
    onIssueClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PartIssueListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.part_issues_title)) },
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

        val issues = state.issues

        Column(modifier = Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.filter == IssueFilter.ALL,
                        onClick = { viewModel.setFilter(IssueFilter.ALL) },
                        label = { Text(stringResource(R.string.part_issues_filter_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.filter == IssueFilter.ISSUE,
                        onClick = { viewModel.setFilter(IssueFilter.ISSUE) },
                        label = { Text(stringResource(R.string.part_issues_filter_issue)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.filter == IssueFilter.TRANSFER,
                        onClick = { viewModel.setFilter(IssueFilter.TRANSFER) },
                        label = { Text(stringResource(R.string.part_issues_filter_transfer)) },
                    )
                }
            }

            if (issues.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.part_issues_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(issues, key = { it.id }) { issue ->
                        PartIssueCard(issue = issue, locationNames = state.locationNames, onClick = { onIssueClick(issue.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PartIssueCard(issue: StockMovement, locationNames: Map<String, String>, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(
                        containerColor = if (issue.isTransfer)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (issue.isTransfer)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            if (issue.isTransfer) stringResource(R.string.part_issues_type_transfer)
                            else stringResource(R.string.part_issues_type_issue),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    val title = issue.number ?: issue.reference
                    if (title != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val where = listOfNotNull(locationNames[issue.locationId], issue.toLocationId?.let { locationNames[it] }).joinToString(" → ")
                Text(
                    listOf(dateFmt.format(Date(issue.effectiveAt ?: issue.createdAt)), where).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val summary = issue.lines.joinToString(", ") { line ->
                    "${line.productName ?: line.productSku ?: line.productId} × ${(line.quantity ?: java.math.BigDecimal.ZERO).formatQty()}"
                }
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                issue.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
