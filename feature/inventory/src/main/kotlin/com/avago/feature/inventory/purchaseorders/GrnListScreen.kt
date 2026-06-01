package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.GrnEntity
import com.avago.core.sync.SyncEngine
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GrnListItem(
    val grn: GrnEntity,
    val poNumber: String?,
)

data class GrnListUiState(
    val items: List<GrnListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class GrnListViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GrnListUiState> = identityManager.activeAccountId
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(GrnListUiState(items = emptyList(), isLoading = false))
            } else {
                val db = dbFactory.get(accountId)
                combine(
                    db.grnDao().observeAll(accountId),
                    db.purchaseOrderDao().observeAll(accountId),
                ) { grns, purchaseOrders ->
                    val poNumbersById = purchaseOrders.associate { it.poId to it.poNumber }
                    GrnListUiState(
                        items = grns
                            .map { grn ->
                                GrnListItem(
                                    grn = grn,
                                    poNumber = grn.poId?.let(poNumbersById::get),
                                )
                            }
                            .sortedByDescending { it.grn.receivedAt ?: it.grn.createdAt },
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GrnListUiState(),
        )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.sync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrnListScreen(
    onBack: () -> Unit,
    onGrnClick: (String) -> Unit,
    viewModel: GrnListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.grn_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState(
                    message = stringResource(R.string.grn_list_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.grn.grnId }) { item ->
                        GrnCard(
                            item = item,
                            onClick = { onGrnClick(item.grn.grnId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrnCard(
    item: GrnListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.grn.grnNumber ?: item.grn.grnId.take(8),
                style = MaterialTheme.typography.titleMedium,
            )
            item.poNumber?.let { poNumber ->
                Text(
                    text = "${stringResource(R.string.grn_po_label)}: $poNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${stringResource(R.string.grn_received_date_label2)}: ${item.grn.receivedAt.formatGrnDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Long?.formatGrnDate(): String {
    if (this == null) return "—"
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))
}
