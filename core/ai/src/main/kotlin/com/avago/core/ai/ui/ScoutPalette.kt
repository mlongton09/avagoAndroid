package com.avago.core.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.ai.ActionCard
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
@OptIn(ExperimentalMaterial3Api::class)
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

    // Handle state transitions once per result.
    LaunchedEffect(state) {
        val s = state as? ScoutViewModel.ScoutState.Result ?: return@LaunchedEffect
        val card = s.response.actionCard
        val target = s.response.targetScreen
        if (card != null && !card.isExpired && target != null) {
            pendingNavTarget = target
            pendingNavFields = s.response.fields
            actionCardToConfirm = card
        } else {
            if (target != null) onNavigate(target, s.response.fields)
            onDismiss()
            viewModel.reset()
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
                        pendingNavTarget?.let { target ->
                            onNavigate(target, pendingNavFields)
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

            Text("Scout", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Free-text composer. Typing "/" activates slash-menu filtering.
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask Scout anything… (/ for skills)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.query(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() && state !is ScoutViewModel.ScoutState.Loading,
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

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
                            it.name.lowercase().contains(q) ||
                                it.description?.lowercase()?.contains(q) == true
                        }
                    }
                    else -> skills
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(displaySkills, key = { it.skill_id }) { skill ->
                    ListItem(
                        headlineContent = { Text(skill.name) },
                        supportingContent = skill.description?.let { desc -> { Text(desc) } },
                        leadingContent = {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            input = ""
                            viewModel.query(skill.name)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private val FALLBACK_SKILLS = listOf(
    "Log service",
    "Create work order",
    "Add parts pickup",
    "Find part",
    "Show overdue",
).map { AiSkillResponse(skill_id = it, name = it) }
