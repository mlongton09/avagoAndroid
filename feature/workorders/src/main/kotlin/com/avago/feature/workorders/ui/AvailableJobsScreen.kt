package com.avago.feature.workorders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.workorders.R
import com.avago.feature.workorders.ui.components.DueDateBadge
import com.avago.feature.workorders.ui.components.PriorityBadge
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.viewmodel.AvailableJobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableJobsScreen(
    onBack: () -> Unit,
    onWoClick: (woId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AvailableJobsViewModel = hiltViewModel(),
) {
    val jobs by viewModel.availableJobs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val claimingIds by viewModel.claimingIds.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(stringResource(R.string.available_jobs_title)) },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (jobs.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.available_jobs_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(jobs, key = { it.woId }) { wo ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = wo.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                wo.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    DueDateBadge(
                                        dueDateMs = wo.dueDate,
                                        status = WoStatus.fromKey(wo.status),
                                    )
                                    wo.priority?.let { PriorityBadge(priority = it) }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.claimJob(wo) },
                                    enabled = wo.woId !in claimingIds,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (wo.woId in claimingIds) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(end = 8.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                    Text(
                                        if (wo.woId in claimingIds)
                                            stringResource(R.string.available_jobs_claiming)
                                        else
                                            stringResource(R.string.available_jobs_self_assign)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
