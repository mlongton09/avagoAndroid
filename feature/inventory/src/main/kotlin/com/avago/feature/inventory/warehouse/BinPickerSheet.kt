package com.avago.feature.inventory.warehouse

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.BinDao
import com.avago.core.data.db.entity.BinEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import com.avago.feature.inventory.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

@HiltViewModel
class BinPickerViewModel @Inject constructor(
    private val binDao: BinDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val bins: StateFlow<List<BinEntity>> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(emptyList())
        else binDao.observeAll(accountId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinPickerSheet(
    locationId: String,
    onBinSelected: (BinEntity?) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BinPickerViewModel = hiltViewModel(),
) {
    val allBins by viewModel.bins.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val locationBins = allBins.filter { it.locationId == locationId }
    val filtered = if (query.isBlank()) locationBins
    else locationBins.filter { it.name.contains(query, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.bin_picker_title), modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.bin_picker_search)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Text(
                        text = stringResource(R.string.bin_picker_none),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBinSelected(null) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    HorizontalDivider()
                }
                items(filtered, key = { it.binId }) { bin ->
                    Text(
                        text = "${bin.name}${bin.aisle?.let { " • Aisle $it" } ?: ""}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBinSelected(bin) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
