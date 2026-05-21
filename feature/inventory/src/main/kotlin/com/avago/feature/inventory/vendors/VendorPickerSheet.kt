package com.avago.feature.inventory.vendors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.VendorEntity
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorPickerSheet(
    onVendorSelected: (VendorEntity?) -> Unit,
    onDismiss: () -> Unit,
    viewModel: VendorListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var localQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.vendor_picker_title),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = localQuery,
                onValueChange = {
                    localQuery = it
                    viewModel.setSearchQuery(it)
                },
                placeholder = { Text(stringResource(R.string.vendor_picker_search)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            val filtered = if (localQuery.isBlank()) state.vendors
            else state.vendors.filter { it.name.contains(localQuery, ignoreCase = true) }

            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Text(
                        text = stringResource(R.string.vendor_picker_none),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVendorSelected(null) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    HorizontalDivider()
                }
                items(filtered, key = { it.vendorId }) { vendor ->
                    Text(
                        text = vendor.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVendorSelected(vendor) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
