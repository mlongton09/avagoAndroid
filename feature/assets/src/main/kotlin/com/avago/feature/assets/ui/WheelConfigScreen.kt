package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.feature.assets.R

private val WHEEL_POSITIONS = listOf(
    "Front Left",
    "Front Right",
    "Rear Left",
    "Rear Right",
    "Spare",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelConfigScreen(
    onSave: (position: String, tireSize: String, rimSize: String, brand: String, notes: String) -> Unit,
    onBack: () -> Unit,
) {
    var position by remember { mutableStateOf(WHEEL_POSITIONS[0]) }
    var positionExpanded by remember { mutableStateOf(false) }
    var tireSize by remember { mutableStateOf("") }
    var rimSize by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wheel_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = positionExpanded,
                onExpandedChange = { positionExpanded = it },
            ) {
                OutlinedTextField(
                    value = position,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.wheel_config_position_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = positionExpanded,
                    onDismissRequest = { positionExpanded = false },
                ) {
                    WHEEL_POSITIONS.forEach { pos ->
                        DropdownMenuItem(
                            text = { Text(pos) },
                            onClick = {
                                position = pos
                                positionExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = tireSize,
                onValueChange = { tireSize = it },
                label = { Text(stringResource(R.string.wheel_config_tire_size_label)) },
                placeholder = { Text(stringResource(R.string.wheel_config_tire_size_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = rimSize,
                onValueChange = { rimSize = it },
                label = { Text(stringResource(R.string.wheel_config_rim_size_label)) },
                placeholder = { Text(stringResource(R.string.wheel_config_rim_size_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text(stringResource(R.string.wheel_config_brand_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.wheel_config_notes_label)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onSave(position, tireSize, rimSize, brand, notes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.wheel_config_save))
            }
        }
    }
}
