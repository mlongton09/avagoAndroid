package com.avago.feature.log.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.avago.feature.log.R
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.VoiceFill
import java.util.Locale

/**
 * "Speak inspection" (web VoiceFillDialog): dictate or type a walk-through, review the
 * matched answers / readings / notes, and apply only the accepted ones in one step.
 * Uses the in-app [SpeechRecognizer] when RECORD_AUDIO is granted; otherwise falls back
 * to the system [RecognizerIntent] UI, which needs no permission.
 */
@Composable
fun VoiceFillDialog(
    checklist: InspectionChecklist,
    values: Map<String, String>,
    onDismiss: () -> Unit,
    onApply: (writes: List<Pair<String, String>>, count: Int) -> Unit,
) {
    val context = LocalContext.current
    val cornerNames = mapOf(
        "fl" to stringResource(R.string.log_inspection_loc_fl),
        "fr" to stringResource(R.string.log_inspection_loc_fr),
        "rl" to stringResource(R.string.log_inspection_loc_rl),
        "rr" to stringResource(R.string.log_inspection_loc_rr),
    )
    val targets = remember(checklist, cornerNames) {
        VoiceFill.buildTargets(checklist) { cornerNames[it] ?: it }
    }
    val answerLabels = remember(checklist) {
        checklist.allItems().flatMap { it.options }.associate { it.rawValue to it.value }
    }
    val units = remember(checklist) { checklist.allItems().associate { it.id to it.unit.orEmpty() } }

    var text by rememberSaveable { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val result = remember(text, targets) { VoiceFill.parse(text, targets) }
    // Keys of changes the user unticked; everything else is accepted (default all on).
    val rejected = remember { mutableStateListOf<String>() }
    fun changeId(c: VoiceFill.Change) = "${c.kind}|${c.key}|${c.corner.orEmpty()}"
    val accepted = result.changes.filter { changeId(it) !in rejected }

    val permissionMsg = stringResource(R.string.log_insp_voice_permission)
    val unavailableMsg = stringResource(R.string.log_insp_voice_unavailable)
    val currentText by rememberUpdatedState(text)
    fun append(spoken: String) {
        val s = spoken.trim()
        if (s.isEmpty()) return
        text = if (currentText.isBlank()) s else "${currentText.trimEnd().trimEnd(',')}, $s"
    }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { listening = false; partial = "" }
            override fun onResults(results: Bundle?) {
                listening = false
                partial = ""
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::append)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }

    fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    val systemLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::append)
        }
    }
    fun launchSystem() {
        try {
            systemLauncher.launch(speechIntent())
        } catch (_: ActivityNotFoundException) {
            message = unavailableMsg
        }
    }
    fun startInApp() {
        val r = recognizer ?: return launchSystem()
        message = null
        listening = true
        r.startListening(speechIntent())
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startInApp() else { message = permissionMsg; launchSystem() }
    }
    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun listen() {
        when {
            listening -> { recognizer?.stopListening(); listening = false }
            recognizer == null -> launchSystem()
            hasMic() -> startInApp()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Web autoStart: begin listening right away when the mic is already allowed.
    LaunchedEffect(Unit) { if (recognizer != null && hasMic()) startInApp() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_insp_voice_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.log_insp_voice_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.log_insp_voice_transcript)) },
                    placeholder = { Text(stringResource(R.string.log_insp_voice_placeholder)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = ::listen, modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()) {
                    Icon(if (listening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(if (listening) R.string.log_insp_voice_listening else R.string.log_insp_voice_listen),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (listening && partial.isNotBlank()) {
                    Text("“$partial”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (text.isNotBlank()) {
                        if (result.changes.isEmpty()) {
                            Text(stringResource(R.string.log_insp_voice_no_changes), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                stringResource(R.string.log_insp_voice_changes, result.changes.size),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            result.changes.forEach { c ->
                                val id = changeId(c)
                                VoiceChangeRow(
                                    change = c,
                                    previous = VoiceFill.previous(c, values),
                                    unit = units[c.itemId].orEmpty(),
                                    answerLabel = { answerLabels[it] ?: it },
                                    checked = id !in rejected,
                                    onToggle = { on -> if (on) rejected.remove(id) else rejected.add(id) },
                                )
                            }
                        }
                        if (result.unmatched.isNotEmpty()) {
                            Text(
                                stringResource(R.string.log_insp_voice_didnt_understand),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            result.unmatched.forEach {
                                Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(VoiceFill.writes(accepted, values), accepted.size) },
                enabled = accepted.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.log_insp_voice_apply, accepted.size)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(com.avago.core.ui.R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun VoiceChangeRow(
    change: VoiceFill.Change,
    previous: String,
    unit: String,
    answerLabel: (String) -> String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val noteLabel = stringResource(R.string.log_insp_voice_note)
    val becomes = stringResource(R.string.log_insp_voice_becomes)
    val title = if (change.kind == VoiceFill.ChangeKind.NOTE) "${change.display} · $noteLabel" else change.display
    val newText = when (change.kind) {
        VoiceFill.ChangeKind.ANSWER -> answerLabel(change.value)
        VoiceFill.ChangeKind.NUMBER -> if (unit.isNotEmpty()) "${change.value} $unit" else change.value
        VoiceFill.ChangeKind.NOTE -> "“${change.value}”"
    }
    val oldText = when (change.kind) {
        VoiceFill.ChangeKind.ANSWER -> previous.takeIf { it.isNotEmpty() && it != change.value }?.let(answerLabel)
        VoiceFill.ChangeKind.NUMBER -> previous.takeIf { it.isNotEmpty() && it != change.value }
        VoiceFill.ChangeKind.NOTE -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (oldText != null) {
                    Text(oldText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = becomes,
                        modifier = Modifier.padding(horizontal = 4.dp).size(14.dp),
                    )
                }
                Text(newText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
