package com.avago.feature.schedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.avago.core.data.domain.PmPlan
import com.avago.core.ui.EmptyState
import com.avago.feature.schedule.R
import com.avago.feature.schedule.ui.components.pmTriggerText
import com.avago.feature.schedule.viewmodel.PmPlanListViewModel

/** The PM plans (of one asset when opened with `assetId`); opens the plan editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmPlanListScreen(
    onBack: () -> Unit,
    onOpenPlan: (planId: String) -> Unit,
    onNewPlan: (assetId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PmPlanListViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.schedule_detail_back))
                    }
                },
                title = { Text(stringResource(R.string.pm_plans_title)) },
            )
        },
        floatingActionButton = {
            if (canManage) {
                ExtendedFloatingActionButton(
                    onClick = { onNewPlan(viewModel.assetId) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.pm_plan_new)) },
                )
            }
        },
    ) { innerPadding ->
        val list = plans
        when {
            list == null -> Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(stringResource(R.string.pm_plans_empty), Modifier.padding(innerPadding).fillMaxSize())
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(list, key = { it.id }) { plan ->
                    PlanRow(plan, onClick = { onOpenPlan(plan.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: PmPlan, onClick: () -> Unit) {
    val triggers = plan.triggers.map { pmTriggerText(it) }.joinToString(" \u00b7 ")
    ListItem(
        headlineContent = { Text(plan.title) },
        overlineContent = plan.assetName?.let { name -> { Text(name) } },
        supportingContent = {
            Text(
                text = if (plan.active) triggers else stringResource(R.string.pm_plan_paused_summary, triggers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
