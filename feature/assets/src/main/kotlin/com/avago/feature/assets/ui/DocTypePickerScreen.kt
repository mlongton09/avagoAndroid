package com.avago.feature.assets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.avago.feature.assets.R

private val DOC_TYPES = listOf(
    "Invoice",
    "Manual",
    "Warranty",
    "Certificate",
    "Photo",
    "Report",
    "Other",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocTypePickerScreen(
    onDocTypeSelected: (type: String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_type_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.doc_type_picker_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(DOC_TYPES) { docType ->
                ListItem(
                    headlineContent = { Text(docType) },
                    modifier = Modifier.clickable(role = Role.Button) {
                        onDocTypeSelected(docType)
                    },
                )
            }
        }
    }
}
