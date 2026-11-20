package com.avago.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.AccessDenial

/**
 * Shown while the server refuses the active account with a typed `403` (P4-01b). It cannot be
 * dismissed: sync is on hold until the user switches workspace, signs out or tries again. A user
 * denial (suspended or deactivated user) offers only sign-out, since every workspace refuses it.
 */
@Composable
fun AccessDeniedDialog(
    onSwitchWorkspace: () -> Unit,
    viewModel: AccessDeniedViewModel = hiltViewModel(),
) {
    val denied by viewModel.denied.collectAsStateWithLifecycle()
    val denial = denied?.denial ?: return
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Block, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (denial.requiresSignOut) R.string.access_denied_user_title else R.string.access_denied_workspace_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(denial.messageRes))
                if (!denial.requiresSignOut) Text(stringResource(R.string.access_denied_changes_kept))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::tryAgain) { Text(stringResource(R.string.access_denied_try_again)) }
                if (!denial.requiresSignOut) {
                    TextButton(onClick = onSwitchWorkspace) { Text(stringResource(R.string.access_denied_switch_workspace)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::signOut) {
                Text(stringResource(R.string.account_switcher_sign_out), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** What the user is told about [AccessDenial], here and on the sign-in screens. */
@get:StringRes
val AccessDenial.messageRes: Int
    get() = when (this) {
        AccessDenial.MEMBERSHIP_SUSPENDED -> R.string.access_denied_membership_suspended
        AccessDenial.MEMBERSHIP_INACTIVE -> R.string.access_denied_membership_inactive
        AccessDenial.USER_SUSPENDED -> R.string.access_denied_user_suspended
        AccessDenial.USER_DEACTIVATED -> R.string.access_denied_user_deactivated
    }
