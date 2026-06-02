package com.avago.core.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.ai.ActionCard
import com.avago.core.ai.R
import com.avago.core.ai.ScoutResponse
import com.avago.core.ai.ScoutViewModel
import com.avago.core.network.model.AiSkillResponse

/**
 * Scout AI palette — a bottom sheet with quick-action skill chips and
 * a free-text input field.
 *
 * Single tap on the global FAB opens this sheet. On a successful
 * [ScoutViewModel.ScoutState.Result] the caller is notified via
 * [onNavigate] so it can route to the target screen with pre-filled
 * form fields; the sheet then auto-dismisses.
 *
 * When the result includes an [ActionCard], a confirmation dialog is
 * shown instead of auto-navigating. Dangerous actions show an extra
 * warning and use an error-coloured confirm button.
 *
 * Typing "/" in the input field switches the skill list to a
 * slash-command filter (mirrors iOS slash-menu).
 *
 * @param visible    Whether the sheet should be rendered.
 * @param onDismiss  Called when the user swipes away or after navigation.
 * @param onNavigate Called with (targetRoute, fieldMap) on a successful
 *                   Scout reply that includes a [targetScreen].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScoutPaletteSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (targetScreen: String, fields: Map<String, String?>) -> Unit,
    viewModel: ScoutViewModel = hiltViewModel(),
) {
    if (!visible) return

    val state by viewModel.state.collectAsState()
    val skills by viewModel.skills.collectAsState()
    var input by remember { mutableStateOf("") }
    var actionCardToConfirm by remember { mutableStateOf<ActionCard?>(null) }
    var pendingNavTarget by remember { mutableStateOf<String?>(null) }
    var pendingNavFields by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var formReadyResponse by remember { mutableStateOf<ScoutResponse?>(null) }
    var countdownNow by remember { mutableStateOf(System.currentTimeMillis()) }

    // Handle state transitions once per result.
    LaunchedEffect(state) {
        when (val s = state) {
            is ScoutViewModel.ScoutState.Result -> {
                val card = s.response.actionCard
                val target = s.response.targetScreen
                if (card != null && !card.isExpired) {
                    pendingNavTarget = target
                    // Inject skill name so executeAction can dispatch it.
                    pendingNavFields = s.response.fields + mapOf("_skill" to card.skillName)
                    actionCardToConfirm = card
                } else {
                    if (target != null) {
                        formReadyResponse = s.response
                    }
                }
            }
            is ScoutViewModel.ScoutState.Executed -> {
                // HITL-off: executor already committed, just dismiss.
                onDismiss()
                viewModel.reset()
            }
            else -> Unit
        }
    }

    LaunchedEffect(state) {
        while (state is ScoutViewModel.ScoutState.Throttled) {
            countdownNow = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }

    // Action card confirmation dialog (rendered outside the sheet so it
    // overlays correctly on all API levels).
    actionCardToConfirm?.let { card ->
        AlertDialog(
            onDismissRequest = {
                actionCardToConfirm = null
                pendingNavTarget = null
                viewModel.reset()
            },
            title = { Text(card.title) },
            text = {
                Column {
                    card.summary?.let { Text(it) }
                    if (card.dangerous) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "This action cannot be undone.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pendingNavTarget
                        if (target != null) {
                            onNavigate(target, pendingNavFields)
                        } else {
                            // Action-only skill (e.g. work-order-action): execute directly.
                            viewModel.executeAction(pendingNavFields)
                        }
                        actionCardToConfirm = null
                        pendingNavTarget = null
                        onDismiss()
                        viewModel.reset()
                    },
                    colors = if (card.dangerous) {
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    } else ButtonDefaults.textButtonColors(),
                ) {
                    Text(if (card.dangerous) "Confirm (cannot undo)" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        actionCardToConfirm = null
                        pendingNavTarget = null
                        viewModel.reset()
                    },
                ) { Text("Cancel") }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            Text(stringResource(R.string.scout_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            formReadyResponse?.let { response ->
                FormReadyBanner(
                    onOpen = {
                        val target = response.targetScreen
                        if (target != null) onNavigate(target, response.fields)
                        formReadyResponse = null
                        onDismiss()
                        viewModel.reset()
                    },
                    onDismiss = {
                        formReadyResponse = null
                        viewModel.reset()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Free-text composer. Typing "/" activates slash-menu filtering.
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.scout_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.query(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() &&
                            state !is ScoutViewModel.ScoutState.Loading &&
                            state !is ScoutViewModel.ScoutState.Throttled,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            if (input.isBlank()) {
                ScoutSuggestionChips(onSuggestion = { input = it })
                Spacer(Modifier.height(8.dp))
            }

            // Loading / error state
            when (val s = state) {
                is ScoutViewModel.ScoutState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
                is ScoutViewModel.ScoutState.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                is ScoutViewModel.ScoutState.Queued -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                is ScoutViewModel.ScoutState.Throttled -> {
                    ThrottleBanner(
                        remainingSeconds = ((s.untilEpochMillis - countdownNow).coerceAtLeast(0L) / 1000L),
                    )
                }
                else -> Unit
            }

            // Skill list: filtered by slash-menu query when input starts with "/".
            val displaySkills = remember(input, skills) {
                when {
                    skills.isEmpty() -> FALLBACK_SKILLS
                    input.startsWith("/") -> {
                        val q = input.drop(1).lowercase()
                        if (q.isEmpty()) skills
                        else skills.filter {
                            it.name?.lowercase()?.contains(q) == true ||
                                it.skill_id.lowercase().contains(q) ||
                                it.description?.lowercase()?.contains(q) == true
                        }
                    }
                    else -> skills
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(displaySkills, key = { it.skill_id }) { skill ->
                    ListItem(
                        headlineContent = { Text(skill.name ?: skill.skill_id) },
                        supportingContent = skill.description?.let { desc -> { Text(desc) } },
                        leadingContent = {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        },
                        trailingContent = if (skill.state_changing) ({
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "State-changing action",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }) else null,
                        modifier = Modifier.clickable {
                            // Populate the composer with the first example phrasing so
                            // the user can review/edit before sending — mirrors iOS chip
                            // behaviour where tapping sets draftText without auto-sending.
                            input = skill.example_phrasings.firstOrNull() ?: skill.name ?: skill.skill_id
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoutSuggestionChips(
    onSuggestion: (String) -> Unit,
) {
    val suggestions = listOf(
        stringResource(R.string.chip_oil_change),
        stringResource(R.string.chip_fuel_log),
        stringResource(R.string.chip_inspection),
        stringResource(R.string.chip_create_wo),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onSuggestion(suggestion) },
                label = { Text(suggestion) },
            )
        }
    }
}

@Composable
private fun FormReadyBanner(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Form ready", style = MaterialTheme.typography.labelLarge)
                Text("Tap to review the filled values.", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss form-ready notice")
            }
        }
    }
}

@Composable
private fun ThrottleBanner(remainingSeconds: Long) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = "Scout is cooling down. Try again in ${remainingSeconds}s.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val FALLBACK_SKILLS = listOf(
    AiSkillResponse(skill_id = "log-entry-create",      name = "Log service",       description = "Log a service entry",       example_phrasings = listOf("Oil change on F-150 today \$90")),
    AiSkillResponse(skill_id = "fuel-log",              name = "Log fuel",          description = "Record a fuel fill-up",     example_phrasings = listOf("Filled up truck 12.3 gal \$54")),
    AiSkillResponse(skill_id = "inspection-from-voice", name = "Log inspection",    description = "Record an inspection",      example_phrasings = listOf("Daily inspection on excavator, all good")),
    AiSkillResponse(skill_id = "work-order-create",     name = "Create work order", description = "Open a new work order",     example_phrasings = listOf("Create a WO to inspect rear brake pads")),
    AiSkillResponse(skill_id = "work-order-assign",     name = "Assign work order", description = "Assign a WO to someone",   example_phrasings = listOf("Assign WO-42 to Sarah"), state_changing = true),
    AiSkillResponse(skill_id = "asset-create",          name = "Add asset",         description = "Register a new asset",     example_phrasings = listOf("Add a 2019 Ford F-350 named Site Truck")),
    AiSkillResponse(skill_id = "chat-qa",               name = "Ask a question",    description = "Query your fleet data",    example_phrasings = listOf("What’s overdue this week?")),
)
