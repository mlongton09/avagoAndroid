package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentals.R
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.readableWidth
import java.time.LocalTime

/** Signed-out state of a renter action: one primary "Sign in" that returns here afterwards. */
@Composable
internal fun SignInPrompt(message: String, onSignIn: () -> Unit) {
    EmptyState(
        stringResource(R.string.mkt_not_signed_in),
        icon = Icons.AutoMirrored.Outlined.Login,
        message = message,
        actionLabel = stringResource(R.string.mkt_sign_in),
        onAction = onSignIn,
    )
}

/** Success view after a reservation / meet-up request. */
@Composable
internal fun DoneView(title: String, body: String, onRequests: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.readableWidth().padding(AvagoSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRequests, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_view_my_requests)) }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_back_to_search)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.rentals_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) } },
        text = { TimePicker(state = state) },
    )
}
