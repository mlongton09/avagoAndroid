package com.avago.feature.docs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.Document
import com.avago.core.ui.components.EmptyStateView
import com.avago.feature.docs.R
import com.avago.feature.docs.model.DocTypes
import com.avago.feature.docs.model.formatAmount
import com.avago.feature.docs.viewmodel.DocListViewModel

/** The account's maintenance documents (drawer destination; the shared top bar carries the title). */
@Composable
fun DocListScreen(onOpen: (String) -> Unit, viewModel: DocListViewModel = hiltViewModel()) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = viewModel::onQuery,
            placeholder = { Text(stringResource(R.string.doc_list_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter.kind == null,
                onClick = { viewModel.onKind(null) },
                label = { Text(stringResource(R.string.doc_list_all)) },
            )
            DocTypes.all.forEach { type ->
                FilterChip(
                    selected = filter.kind == type.kind,
                    onClick = { viewModel.onKind(type.kind) },
                    label = { Text(stringResource(type.labelResId)) },
                    leadingIcon = { Icon(type.icon, contentDescription = null) },
                )
            }
        }
        val list = documents
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyStateView(
                icon = Icons.Default.Description,
                title = stringResource(R.string.doc_list_empty),
                message = stringResource(R.string.doc_list_empty_hint),
            )
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.id }) { doc ->
                    DocumentRow(doc, onClick = { onOpen(doc.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/** One document line: kind icon, title, and its date, issuer and amount. */
@Composable
fun DocumentRow(doc: Document, onClick: () -> Unit) {
    val type = DocTypes.forKind(doc.kind)
    val details = listOfNotNull(
        doc.issuedOn?.format(dateFormatter),
        doc.issuerName,
        doc.total?.let { formatAmount(it, doc.currency) },
    ).joinToString(" \u00b7 ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(type.icon, contentDescription = stringResource(type.labelResId)) },
        headlineContent = { Text(doc.displayTitle ?: stringResource(R.string.doc_untitled)) },
        supportingContent = {
            Column {
                Text(details.ifEmpty { stringResource(type.labelResId) })
                if (doc.file?.isDownloadable == false) {
                    Text(
                        stringResource(R.string.doc_detail_file_uploading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
