package com.avago.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
                        homeLocationId = null,
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
    viewModel: MyTechProfileViewModel = hiltViewModel(),
) {
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val certifications by viewModel.certifications.collectAsStateWithLifecycle()
    val hourlyRate by viewModel.hourlyRate.collectAsStateWithLifecycle()
    val isAvailable by viewModel.isAvailable.collectAsStateWithLifecycle()
    val maxActiveWos by viewModel.maxActiveWos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveResult) {
        saveResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
