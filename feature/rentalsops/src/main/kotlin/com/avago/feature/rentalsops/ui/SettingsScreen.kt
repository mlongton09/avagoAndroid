package com.avago.feature.rentalsops.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.push.RentalOpsPushPrefs
import com.avago.feature.rentalsops.push.RentalPushRouter.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RentalOpsSettingsViewModel @Inject constructor(private val prefs: RentalOpsPushPrefs) : ViewModel() {
    val enabled = prefs.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun set(c: Category, on: Boolean) = viewModelScope.launch { prefs.set(c, on) }
}

/** Device-local push preferences per rental event category (server-side prefs: TODO stage C). */
@Composable
fun RentalOpsSettingsScreen(onBack: () -> Unit, vm: RentalOpsSettingsViewModel = hiltViewModel()) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val systemOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_settings_title), onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            if (!systemOn) {
                OfflineBanner(stringResource(R.string.rentalops_push_disabled))
                OutlinedButton(
                    onClick = {
                        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                        }
                        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.padding(horizontal = AvagoSpacing.lg).heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_open_settings)) }
            }
            Text(
                stringResource(R.string.rentalops_push_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(AvagoSpacing.lg),
            )
            Category.entries.forEach { c ->
                val on = enabled[c] ?: true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouch)
                        .toggleable(value = on, role = Role.Switch, onValueChange = { vm.set(c, it) })
                        .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(categoryTitle(c)), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(categoryDesc(c)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}

private fun categoryTitle(c: Category) = when (c) {
    Category.TASKS -> R.string.rentalops_push_task_title
    Category.TRANSFERS -> R.string.rentalops_push_transfer_title
    Category.ASSIGNMENTS -> R.string.rentalops_push_assign_title
    Category.DUE -> R.string.rentalops_push_due_title
    Category.OTHER -> R.string.rentalops_push_other_title
}

private fun categoryDesc(c: Category) = when (c) {
    Category.TASKS -> R.string.rentalops_push_task_desc
    Category.TRANSFERS -> R.string.rentalops_push_transfer_desc
    Category.ASSIGNMENTS -> R.string.rentalops_push_assign_desc
    Category.DUE -> R.string.rentalops_push_due_desc
    Category.OTHER -> R.string.rentalops_push_other_desc
}
