package com.avago.feature.log.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.UserEntity
import com.avago.feature.log.viewmodel.PerformedByPickerViewModel
import javax.inject.Inject

/**
 * Screen for selecting a "Performed By" user.
 *
 * - Pinned "Me" row at top
 * - Active members sorted by display name
 * - Free-text fallback row at bottom for outside vendors
 *
 * Callers receive the result via [onSelected]:
 * - userId = non-null → internal user
 * - userId = null, name = non-null → free-text name (outside vendor)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformedByPickerScreen(
    currentUserId: String?,
    currentUserName: String?,
    onSelected: (userId: String?, name: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerformedByPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val freeText by viewModel.freeText.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performed By") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Search field
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                label = { Text("Search members") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )

            if (state.isLoading && state.members.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                // Pinned "Me" row
                if (currentUserId != null || currentUserName != null) {
                    item(key = "me") {
                        MemberRow(
                            displayName = "Me${if (currentUserName != null) " ($currentUserName)" else ""}",
                            subtitle = "Select yourself",
                            onClick = { onSelected(currentUserId, currentUserName) },
                            isHighlighted = true,
                        )
                        HorizontalDivider()
                    }
                } else {
                    item(key = "me_fallback") {
                        MemberRow(
                            displayName = "Me",
                            subtitle = "Select yourself",
                            onClick = { onSelected(null, "Me") },
                            isHighlighted = true,
                        )
                        HorizontalDivider()
                    }
                }

                // Active members
                items(state.members, key = { it.userId }) { user ->
                    MemberRow(
                        displayName = user.displayName ?: user.email ?: user.userId,
                        subtitle = user.email ?: user.role,
                        onClick = { onSelected(user.userId, user.displayName) },
                    )
                }

                // Free-text vendor row at bottom
                item(key = "free_text_section") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Outside vendor / other",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = freeText,
                            onValueChange = { viewModel.onFreeTextChanged(it) },
                            label = { Text("Type a name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (freeText.isNotBlank()) {
                                    IconButton(onClick = { onSelected(null, freeText.trim()) }) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = "Select")
                                    }
                                }
                            },
                        )
                        if (freeText.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { onSelected(null, freeText.trim()) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Use \"${freeText.trim()}\"") }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }

            // Error banner
            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    displayName: String,
    subtitle: String?,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (isHighlighted)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
