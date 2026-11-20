package com.avago.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.FieldNotClearableException
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.StoreNotReadyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** Queues admin commands and turns failures into a message for the screen's snackbar. */
abstract class AdminViewModel : ViewModel() {
    private val _message = MutableStateFlow<Int?>(null)

    /** A string resource to show once, or null. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    protected fun submit(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StoreNotReadyException) {
                Timber.w(e, "admin change before the store opened")
                _message.value = R.string.admin_not_ready
            } catch (e: FieldNotClearableException) {
                Timber.w(e, "admin change empties a required field")
                _message.value = R.string.admin_not_clearable
            } catch (e: RecordNotFoundException) {
                Timber.w(e, "admin change on a missing record")
                _message.value = R.string.admin_record_gone
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "admin change rejected locally")
                _message.value = R.string.admin_invalid
            } catch (e: IllegalStateException) {
                Timber.e(e, "admin change could not be queued")
                _message.value = R.string.admin_save_failed
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminTopBar(@StringRes title: Int, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.admin_back))
            }
        },
    )
}

@Composable
internal fun ReadOnlyBanner(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.admin_read_only),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_delete_title, name)) },
        text = { Text(stringResource(R.string.admin_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.admin_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) } },
    )
}

/** A read-only text field that opens a menu of [options]. */
@Composable
internal fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    text: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Default.ArrowDropDown, contentDescription = label) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = {
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}
