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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.avago.core.ai.ScoutViewModel

/**
 * Scout AI palette — a bottom sheet with quick-action skill chips and
 * a free-text input field.
 *
 * Single tap on the global FAB opens this sheet.  On a successful
 * [ScoutViewModel.ScoutState.Result] the caller is notified via
 * [onNavigate] so [MainScaffold] can route to the target screen with
 * pre-filled form fields; the sheet then auto-dismisses.
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
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            Text("Scout", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Quick-action skill chips (mirrors iOS ScoutSheetView empty-state chips)
            val skills = listOf(
                "Log service",
                "Create work order",
                "Add parts pickup",
                "Find part",
                "Show overdue",
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(skills) { skill ->
                    ListItem(
                        headlineContent = { Text(skill) },
                        leadingContent = {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        },
                        modifier = Modifier.clickable { viewModel.query(skill) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Free-text composer
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask Scout anything…") },
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

            Spacer(Modifier.height(12.dp))

            // State display
            when (val s = state) {
                is ScoutViewModel.ScoutState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
                is ScoutViewModel.ScoutState.Result -> {
                    // Auto-navigate then dismiss. LaunchedEffect key on the
                    // response object so it fires exactly once per result.
                    LaunchedEffect(s) {
                        val target = s.response.targetScreen
                        if (target != null) {
                            onNavigate(target, s.response.fields)
                        }
                        onDismiss()
                        viewModel.reset()
                    }
                }
                is ScoutViewModel.ScoutState.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                else -> { /* Idle — nothing to show */ }
            }
        }
    }
}
