package com.avago.feature.workorders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.feature.workorders.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.UserEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.repository.WorkOrderRepository
import com.avago.feature.workorders.ui.components.WoCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class TechProfileViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val techId: String = requireNotNull(savedStateHandle["techId"])

    private val _tech = MutableStateFlow<UserEntity?>(null)
    val tech: StateFlow<UserEntity?> = _tech.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val assignedWos: StateFlow<List<WorkOrderEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId).map { wos ->
                    wos.filter { it.assignedTo == techId }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _tech.value = dbFactory.get(accountId).userDao().getById(techId)
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechProfileScreen(
    techId: String,
    onBack: () -> Unit,
    onWoClick: (woId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TechProfileViewModel = hiltViewModel(),
) {
    val tech by viewModel.tech.collectAsStateWithLifecycle()
    val assignedWos by viewModel.assignedWos.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tech_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.tech_profile_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val currentTech = tech
        if (currentTech == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val openWos = assignedWos.count { it.status == WoStatus.OPEN.key || it.status == WoStatus.ASSIGNED.key }
        val inProgressWos = assignedWos.count { it.status == WoStatus.IN_PROGRESS.key }
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val completedWos = assignedWos.count {
            it.status == WoStatus.COMPLETE.key &&
                (it.completedAt ?: 0L) >= thirtyDaysAgo
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Profile header card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Avatar circle with initials
                        val initials = currentTech.displayName
                            ?.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "?"
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display name
                        Text(
                            text = currentTech.displayName ?: stringResource(R.string.tech_profile_unknown_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )

                        // Email
                        currentTech.email?.takeIf { it.isNotBlank() }?.let { email ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Role badge
                        currentTech.role?.takeIf { it.isNotBlank() }?.let { role ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = role.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Stats row ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.tech_profile_stat_open_wos),
                        value = "$openWos",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.tech_profile_stat_in_progress),
                        value = "$inProgressWos",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.tech_profile_stat_completed_30d),
                        value = "$completedWos",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Assigned Work Orders header ──
            item {
                Text(
                    text = stringResource(R.string.tech_profile_assigned_wos_header),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ── WO list or empty state ──
            if (assignedWos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.tech_profile_no_wos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(assignedWos, key = { it.woId }) { wo ->
                    WoCard(
                        wo = wo,
                        onClick = { onWoClick(wo.woId) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
