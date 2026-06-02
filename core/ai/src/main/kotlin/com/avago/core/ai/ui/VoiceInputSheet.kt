package com.avago.core.ai.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.core.ai.R

/**
 * Voice input bottom sheet — long-press on the Scout FAB opens this.
 *
 * Launches the platform's speech recogniser via an Activity result
 * contract so no RECORD_AUDIO runtime permission is needed (the system
 * UI shows its own prompt). The captured transcript is held locally;
 * recognized speech is forwarded to [ScoutViewModel.query] via [onTranscript].
 *
 * The UX intentionally matches iOS ScoutSheetView's hold-to-record mic
 * button but adapted for Android's Intent-based speech API which is
 * simpler and more reliable across OEM firmware than the
 * SpeechRecognizer API.
 *
 * @param visible      Whether the sheet is rendered.
 * @param onDismiss    Called when the sheet is dismissed without a result.
 * @param onTranscript Called with recognized text; the caller should
 *                     forward this into [ScoutViewModel.query].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTranscript: (String) -> Unit,
) {
    if (!visible) return

    val title = stringResource(R.string.scout_hold_to_speak)
    val prompt = stringResource(R.string.scout_placeholder)
    val noMatchError = stringResource(R.string.voice_error_no_match)
    val notAvailableError = stringResource(R.string.voice_error_not_available)
    val noPermissionError = stringResource(R.string.voice_error_no_permission)
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val transcript = if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
        } else {
            ""
        }
        if (transcript.isBlank()) {
            errorMessage = noMatchError
        } else {
            onTranscript(transcript)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))

            // Large mic button — tapping launches the system speech UI.
            // On devices where the system recogniser is unavailable the
            // launcher will return an empty result; transcript stays "".
            FilledTonalButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        errorMessage = notAvailableError
                    } catch (_: SecurityException) {
                        errorMessage = noPermissionError
                    }
                },
                modifier = Modifier.size(96.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = title,
                    modifier = Modifier.size(48.dp),
                )
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
