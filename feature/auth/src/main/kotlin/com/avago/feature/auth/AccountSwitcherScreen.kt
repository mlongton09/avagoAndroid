package com.avago.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.auth.AccountRecord

/**
 * Full-screen account switcher — mirrors iOS AccountSwitcherViewController
 * (an inset-grouped table titled "Accounts"). Replaces the old bottom-sheet /
 * dialog presentation so the switch-accounts + create-account flow is a
 * full-screen experience matching iOS.
 *
 *   • "YOUR ACCOUNTS" section: avatar + name + secondary line per account;
 *     the active account shows a checkmark, others a role badge.
 *   • Tap a non-active account → switch and pop. Tap the active account →
 *     sign-out confirmation.
 *   • "Create New Account" row → [onAddAccount].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherScreen(
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: AccountSwitcherViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccountId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    var signOutTarget by remember { mutableStateOf<AccountRecord?>(null) }

    // iOS sorts most-recent-first; AccountRecord tracks addedAt, the closest
    // local proxy for lastUsedAt.
    val sorted = remember(accounts) { accounts.sortedByDescending { it.addedAt } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── YOUR ACCOUNTS ──────────────────────────────────────────────
            if (sorted.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel(stringResource(R.string.account_switcher_title))
                        GroupedCard {
                            sorted.forEachIndexed { index, account ->
                                AccountRow(
                                    account = account,
                                    isActive = account.accountId == activeAccountId,
                                    onClick = {
                                        if (account.accountId == activeAccountId) {
                                            signOutTarget = account
                                        } else {
                                            viewModel.switchTo(account.accountId)
                                            onDismiss()
                                        }
                                    },
                                )
                                if (index < sorted.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Create New Account ─────────────────────────────────────────
            item {
                GroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddAccount)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.account_switcher_add_account),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    // Sign-out confirmation for the active account (iOS shows an action sheet).
    signOutTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { signOutTarget = null },
            title = { Text(target.displayLabel) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut(target.accountId)
                        signOutTarget = null
                        onDismiss()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.account_switcher_sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { signOutTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
    )
}

/** Inset-grouped card: rounded surface on the screen background (iOS bg1 on bg0). */
@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun AccountRow(
    account: AccountRecord,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(account = account)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            secondaryLine(account)?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            isActive -> Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.account_switcher_active_account),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            // Role badge for non-active named accounts (iOS roleBadge).
            !account.isAnonymous && !account.role.isNullOrBlank() -> RoleBadge(account.role!!)
        }
    }
}

@Composable
private fun AccountAvatar(account: AccountRecord) {
    val size = 36.dp
    // iOS: named accounts use a purple circle (rgb 0.34,0.34,0.84); anonymous
    // use the neutral bg3 grey.
    val bg = if (account.isAnonymous) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    } else {
        Color(0xFF5757D6)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(account),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun RoleBadge(role: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = role.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Secondary line: "Anonymous" for guests, else the personal name, else email. */
private fun secondaryLine(account: AccountRecord): String? = when {
    account.isAnonymous -> "Anonymous"
    !account.displayName.isNullOrBlank() -> account.displayName
    else -> account.email
}

private fun initials(account: AccountRecord): String {
    val label = account.displayLabel.trim()
    if (label.isEmpty()) return "?"
    val parts = label.split(" ").filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
    } else {
        label.take(2).uppercase()
    }
}
