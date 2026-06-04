package com.avago.feature.workorders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FloatingActionButton
import com.avago.core.ai.ui.ScoutPaletteSheet
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.CategoryBadge
import com.avago.core.ui.GlobalCategoryPickerScreen
import com.avago.core.ui.buildCategoryItems
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.ui.components.priorityBarColor
import com.avago.feature.workorders.viewmodel.WorkOrderLogViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Merged Work Order + Log Entry screen — mirrors iOS AddLogItemViewController
 * with workOrderContext set.
 *
 * Layout (top to bottom):
 *  • Asset header card (avatar + name + subtitle)
 *  • Log entry form (title, category, notes, cost/meter, performed by)
 *  • Work Order context section (read-only: priority, due date, est effort)
 *  • Pinned bottom bar: [Save Draft] [Complete & Close]
 *
 * Routing: WorkOrderDetailScreen auto-navigates here when WO status is
 * in_progress, assigned-to-current-user, or completed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderLogScreen(
    woId: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderLogViewModel = hiltViewModel(),
) {
    val wo by viewModel.wo.collectAsStateWithLifecycle()
    val assetName by viewModel.assetName.collectAsStateWithLifecycle()
    val assetSubtitle by viewModel.assetSubtitle.collectAsStateWithLifecycle()
    val assetType by viewModel.assetType.collectAsStateWithLifecycle()

    val logTitle by viewModel.logTitle.collectAsStateWithLifecycle()
    val logNotes by viewModel.logNotes.collectAsStateWithLifecycle()
    val logCategory by viewModel.logCategory.collectAsStateWithLifecycle()
    val logCost by viewModel.logCost.collectAsStateWithLifecycle()
    val logMeterReading by viewModel.logMeterReading.collectAsStateWithLifecycle()
    val logPerformedBy by viewModel.logPerformedBy.collectAsStateWithLifecycle()

    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val recentCategoryKeys by viewModel.recentCategoryKeys.collectAsStateWithLifecycle()

    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isCompleting by viewModel.isCompleting.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val savedSuccessfully by viewModel.savedSuccessfully.collectAsStateWithLifecycle()
    val completedSuccessfully by viewModel.completedSuccessfully.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val auditEntries by viewModel.auditEntries.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showScoutSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }   // starts collapsed — matches iOS
    var commentsExpanded by remember { mutableStateOf(true) }   // starts expanded — matches iOS
    var commentDraft by rememberSaveable { mutableStateOf("") }

    // ── Timer tick: updates once/sec when timerStartedAt is set ──────────────
    var timerDisplay by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(wo?.timerStartedAt) {
        val startMs = wo?.timerStartedAt
        if (startMs == null) {
            timerDisplay = "00:00:00"
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            val h = elapsed / 3_600_000L
            val m = (elapsed % 3_600_000L) / 60_000L
            val s = (elapsed % 60_000L) / 1_000L
            timerDisplay = "%02d:%02d:%02d".format(h, m, s)
            delay(1_000L)
        }
    }

    LaunchedEffect(savedSuccessfully) {
        if (savedSuccessfully) {
            snackbarHostState.showSnackbar("Draft saved")
            viewModel.savedSuccessfully.value = false
        }
    }

    LaunchedEffect(completedSuccessfully) {
        if (completedSuccessfully) onCompleted()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showCategoryPicker) {
        GlobalCategoryPickerScreen(
            categories = buildCategoryItems(keys = availableCategories),
            recents = buildCategoryItems(keys = recentCategoryKeys),
            onSelect = { item ->
                viewModel.onCategorySelected(item.key)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showScoutSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Scout")
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wo_log_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wo_cancel))
                    }
                },
            )
        },
        bottomBar = {
            // Pinned action bar — mirrors iOS setupBottomActionBar()
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.saveDraft() },
                        enabled = !isSaving && !isCompleting,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.wo_log_save_draft))
                        }
                    }
                    Button(
                        onClick = { viewModel.completeAndClose(onCompleted) },
                        enabled = !isSaving && !isCompleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        if (isCompleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(
                                stringResource(R.string.wo_log_complete),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (showScoutSheet) {
            ScoutPaletteSheet(
                visible = true,
                onDismiss = { showScoutSheet = false },
                onNavigate = { _, _ -> showScoutSheet = false },
            )
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Asset header ──────────────────────────────────────────────────
            if (assetName != null) {
                WoLogAssetHeader(
                    name = assetName!!,
                    subtitle = assetSubtitle,
                    initial = assetName!!.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                )
            }

            // ── Log entry form ────────────────────────────────────────────────
            WoLogSection {
                // Title
                OutlinedTextField(
                    value = logTitle,
                    onValueChange = { viewModel.logTitle.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wo_field_title)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(4.dp))

                // Category row (tappable pill — same as iOS category badge)
                val catDisplayName = logCategory?.replace("_", " ")?.split(" ")
                    ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                ListItem(
                    leadingContent = { CategoryBadge(categoryId = logCategory) },
                    headlineContent = {
                        Text(catDisplayName ?: stringResource(R.string.wo_category_placeholder))
                    },
                    supportingContent = { Text(stringResource(R.string.wo_create_section_category)) },
                    modifier = Modifier.clickable { showCategoryPicker = true },
                )
            }

            // ── Notes ─────────────────────────────────────────────────────────
            WoLogSection(title = stringResource(R.string.wo_create_section_notes)) {
                OutlinedTextField(
                    value = logNotes,
                    onValueChange = { viewModel.logNotes.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wo_field_description)) },
                    minLines = 3,
                    maxLines = 8,
                )
            }

            // ── Cost + Meter ──────────────────────────────────────────────────
            WoLogSection(title = stringResource(R.string.wo_log_section_details)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = logCost,
                        onValueChange = { viewModel.logCost.value = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.log_entry_label_cost)) },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = logMeterReading,
                        onValueChange = { viewModel.logMeterReading.value = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.log_entry_section_odometer)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = logPerformedBy,
                    onValueChange = { viewModel.logPerformedBy.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.log_entry_label_performed_by)) },
                    placeholder = { Text(stringResource(R.string.log_entry_placeholder_performed_by)) },
                    singleLine = true,
                )
            }

            // ── Work Order context: timer + read-only fields ──────────────────
            wo?.let { currentWo ->
                WoLogSection(title = stringResource(R.string.wo_log_section_work_order)) {
                    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

                    // ── Timer — mirrors iOS WOTimerView ───────────────────────
                    val isRunning = currentWo.timerStartedAt != null
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = timerDisplay,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            ),
                            color = if (isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            isRunning -> OutlinedButton(onClick = { viewModel.pauseTimer() }) {
                                Text(stringResource(R.string.wo_log_timer_pause))
                            }
                            currentWo.status == "in_progress" -> OutlinedButton(onClick = { viewModel.resumeTimer() }) {
                                Text(stringResource(R.string.wo_log_timer_resume))
                            }
                            else -> Button(
                                onClick = { viewModel.startTimer() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) { Text(stringResource(R.string.wo_log_timer_start), color = Color.White) }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Priority
                    val priority = WoPriority.entries.firstOrNull { it.key == currentWo.priority }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.wo_field_priority), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (priority != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(priorityBarColor(priority.key), RoundedCornerShape(50)))
                                Spacer(Modifier.width(6.dp))
                                Text(priority.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    currentWo.dueDate?.let { dueMs ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.wo_field_due_date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFormatter.format(Date(dueMs)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }

                    currentWo.estimatedEffortMinutes?.takeIf { it > 0 }?.let { mins ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        val h = mins / 60; val m = mins % 60
                        val effortLabel = when { h > 0 && m > 0 -> "${h}h ${m}m"; h > 0 -> "${h}h"; else -> "${m}m" }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.wo_field_estimated_hours), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(effortLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── Audit history (collapsible, starts collapsed — matches iOS) ───
            if (auditEntries.isNotEmpty()) {
                WoLogSection {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { historyExpanded = !historyExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.wo_log_section_history), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Icon(if (historyExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    if (historyExpanded) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        auditEntries.forEachIndexed { idx, entry ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                                Text(entry.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (idx < auditEntries.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            // ── Comments: list + inline composer (matches iOS) ────────────────
            WoLogSection {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { commentsExpanded = !commentsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.wo_detail_section_comments), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Icon(if (commentsExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                }
                if (commentsExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    if (comments.isEmpty()) {
                        Text(
                            stringResource(R.string.wo_detail_comments_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        comments.forEachIndexed { idx, comment ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(comment.authorId, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (idx < comments.lastIndex) HorizontalDivider()
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // Pill-style composer — mirrors iOS workOrderCommentComposerField
                    Row(
                        modifier = Modifier.fillMaxWidth().imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            BasicTextField(
                                value = commentDraft,
                                onValueChange = { commentDraft = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (commentDraft.isEmpty()) {
                                        Text(stringResource(R.string.wo_detail_comment_placeholder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    inner()
                                },
                            )
                        }
                        IconButton(
                            onClick = { viewModel.sendComment(commentDraft); commentDraft = "" },
                            enabled = commentDraft.isNotBlank(),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.wo_detail_comment_send),
                                tint = if (commentDraft.isNotBlank()) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun WoLogAssetHeader(
    name: String,
    subtitle: String?,
    initial: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WoLogSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            title?.let {
                Text(text = it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}
