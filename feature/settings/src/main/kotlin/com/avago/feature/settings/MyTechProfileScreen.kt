package com.avago.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.TechProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class MyTechProfileViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val databaseFactory: DatabaseFactory,
) : ViewModel() {

    val displayName = MutableStateFlow("")
    val skills = MutableStateFlow("")
    val certifications = MutableStateFlow("")
    val hourlyRate = MutableStateFlow("")
    val isAvailable = MutableStateFlow(true)
    val maxActiveWos = MutableStateFlow("")
    val homeLocationId = MutableStateFlow<String?>(null)
    val homeLocationName = MutableStateFlow<String?>(null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    private var existingEntity: TechProfileEntity? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val accountId = identityManager.getActiveAccountId() ?: return
        val userId = identityManager.getActiveUserId() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dao = databaseFactory.get(accountId).techProfileDao()
                val entity = dao.getByUserId(accountId, userId)
                existingEntity = entity
                if (entity != null) {
                    displayName.value = entity.displayName ?: ""
                    skills.value = entity.skills ?: ""
                    certifications.value = entity.certifications ?: ""
                    hourlyRate.value = entity.hourlyRate?.toString() ?: ""
                    isAvailable.value = entity.isAvailable
                    maxActiveWos.value = entity.maxActiveWos?.toString() ?: ""
                    homeLocationId.value = entity.homeLocationId
                    if (entity.homeLocationId != null) {
                        val loc = databaseFactory.get(accountId).locationDao().getById(entity.homeLocationId)
                        homeLocationName.value = loc?.name
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "MyTechProfileViewModel: loadProfile failed")
                _saveResult.value = "Error loading profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun save() {
        val accountId = identityManager.getActiveAccountId() ?: return
        val userId = identityManager.getActiveUserId() ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dao = databaseFactory.get(accountId).techProfileDao()
                val base = existingEntity
                val updated = if (base != null) {
                    base.copy(
                        displayName = displayName.value.trim().takeIf { it.isNotBlank() },
                        skills = skills.value.trim().takeIf { it.isNotBlank() },
                        certifications = certifications.value.trim().takeIf { it.isNotBlank() },
                        hourlyRate = hourlyRate.value.trim().toDoubleOrNull(),
                        isAvailable = isAvailable.value,
                        maxActiveWos = maxActiveWos.value.trim().toIntOrNull(),
                        homeLocationId = homeLocationId.value,
                        updatedAt = now,
                    )
                } else {
                    TechProfileEntity(
                        techId = java.util.UUID.randomUUID().toString(),
                        accountId = accountId,
                        userId = userId,
                        displayName = displayName.value.trim().takeIf { it.isNotBlank() },
                        skills = skills.value.trim().takeIf { it.isNotBlank() },
                        certifications = certifications.value.trim().takeIf { it.isNotBlank() },
                        hourlyRate = hourlyRate.value.trim().toDoubleOrNull(),
                        currency = null,
                        availability = null,
                        speedFactor = null,
                        isAvailable = isAvailable.value,
                        maxActiveWos = maxActiveWos.value.trim().toIntOrNull(),
                        homeLocationId = homeLocationId.value,
                        currentLocationLat = null,
                        currentLocationLng = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0,
                        seq = null,
                    )
                }
                dao.upsert(updated)
                existingEntity = updated
                _saveResult.value = "Profile saved"
            } catch (e: Exception) {
                Timber.e(e, "MyTechProfileViewModel: save failed")
                _saveResult.value = "Error saving profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onLocationSelected(locationId: String, name: String) {
        homeLocationId.value = locationId
        homeLocationName.value = name
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTechProfileScreen(
    onBack: () -> Unit,
    /** Called when the user taps the Home Location row; receives the current locationId (if any) to pre-select it. */
    onPickLocation: (currentLocationId: String?) -> Unit = {},
    selectedLocationId: String? = null,
    selectedLocationName: String? = null,
    viewModel: MyTechProfileViewModel = hiltViewModel(),
) {
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val certifications by viewModel.certifications.collectAsStateWithLifecycle()
    val hourlyRate by viewModel.hourlyRate.collectAsStateWithLifecycle()
    val isAvailable by viewModel.isAvailable.collectAsStateWithLifecycle()
    val maxActiveWos by viewModel.maxActiveWos.collectAsStateWithLifecycle()
    val homeLocationName by viewModel.homeLocationName.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedLocationId, selectedLocationName) {
        if (selectedLocationId != null) {
            viewModel.onLocationSelected(selectedLocationId, selectedLocationName ?: selectedLocationId)
        }
    }

    LaunchedEffect(saveResult) {
        saveResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tech_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { viewModel.displayName.value = it },
                label = { Text(stringResource(R.string.tech_profile_display_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )

            OutlinedTextField(
                value = skills,
                onValueChange = { viewModel.skills.value = it },
                label = { Text(stringResource(R.string.tech_profile_skills)) },
                placeholder = { Text(stringResource(R.string.tech_profile_skills_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )

            OutlinedTextField(
                value = certifications,
                onValueChange = { viewModel.certifications.value = it },
                label = { Text(stringResource(R.string.tech_profile_certifications)) },
                placeholder = { Text(stringResource(R.string.tech_profile_certifications_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )

            // Home Location — side-by-side layout matching iOS TechProfileViewController (section 0, row 3):
            // label left (onSurfaceVariant) · value right (onSurface if set, onSurfaceVariant if not) · chevron
            val homeLocationId by viewModel.homeLocationId.collectAsStateWithLifecycle()
            Surface(
                onClick = { onPickLocation(homeLocationId) },
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tech_profile_home_location),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = homeLocationName ?: stringResource(R.string.tech_profile_no_home_location),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (homeLocationName != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            OutlinedTextField(
                value = hourlyRate,
                onValueChange = { viewModel.hourlyRate.value = it },
                label = { Text(stringResource(R.string.tech_profile_hourly_rate)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            OutlinedTextField(
                value = maxActiveWos,
                onValueChange = { viewModel.maxActiveWos.value = it },
                label = { Text(stringResource(R.string.tech_profile_max_active_wos)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tech_profile_available_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = isAvailable,
                    onCheckedChange = { viewModel.isAvailable.value = it },
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) stringResource(R.string.loading) else stringResource(R.string.save))
            }
        }
    }
}
