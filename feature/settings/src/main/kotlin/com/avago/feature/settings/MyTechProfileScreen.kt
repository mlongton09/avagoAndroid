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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Certification
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.WorkerDraft
import com.avago.core.data.domain.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class MyTechProfileViewModel @Inject constructor(
    private val workers: WorkerRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    val displayName = MutableStateFlow("")
    val skills = MutableStateFlow("")
    val isAvailable = MutableStateFlow(true)
    val maxActiveWos = MutableStateFlow("")
    val homeLocationId = MutableStateFlow<String?>(null)
    val homeLocationName = MutableStateFlow<String?>(null)
    val certifications = MutableStateFlow<List<Certification>>(emptyList())
    val hourlyRate = MutableStateFlow("")
    val rateCurrency = MutableStateFlow("")

    /** True when the typed rate or currency cannot be saved. */
    private val _rateInvalid = MutableStateFlow(false)
    val rateInvalid: StateFlow<Boolean> = _rateInvalid.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    /** False when the signed-in user has no worker record in this account. */
    private val _hasProfile = MutableStateFlow(true)
    val hasProfile: StateFlow<Boolean> = _hasProfile.asStateFlow()

    private var workerId: String? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val me = workers.observeMe().first()
                workerId = me?.id
                _hasProfile.value = me != null
                if (me != null) {
                    displayName.value = me.displayName
                    skills.value = me.skills.joinToString(", ")
                    isAvailable.value = me.available
                    maxActiveWos.value = me.maxActiveWorkOrders?.toString().orEmpty()
                    homeLocationId.value = me.homeLocationId
                    homeLocationName.value = me.homeLocationId?.let { locations.location(it)?.name }
                    certifications.value = me.certifications
                    hourlyRate.value = me.hourlyRate?.costRate?.stripTrailingZeros()?.toPlainString().orEmpty()
                    rateCurrency.value = me.hourlyRate?.currency.orEmpty()
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
        val id = workerId ?: return
        val rate = parseRate(hourlyRate.value)
        val currency = rateCurrency.value.trim().uppercase()
        val invalid = rate == INVALID_RATE || (currency.isNotEmpty() && !isCurrencyCode(currency))
        _rateInvalid.value = invalid
        if (invalid) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                workers.update(
                    id,
                    WorkerDraft(
                        displayName = displayName.value.trim().ifBlank { null },
                        skills = parseSkills(skills.value),
                        maxActiveWorkOrders = maxActiveWos.value.trim().toIntOrNull()?.takeIf { it > 0 },
                        available = isAvailable.value,
                        homeLocationId = homeLocationId.value,
                        certifications = certifications.value,
                        hourlyRate = rate,
                        rateCurrency = currency.ifEmpty { null },
                    ),
                )
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

    /** Adds [cert] at [index] (replacing it) or appends it when [index] is null. */
    fun putCertification(index: Int?, cert: Certification) {
        certifications.value = certifications.value.toMutableList().apply { if (index == null) add(cert) else set(index, cert) }
    }

    fun removeCertification(index: Int) {
        certifications.value = certifications.value.filterIndexed { i, _ -> i != index }
    }
}

private val INVALID_RATE = BigDecimal(-1)

/** Null when blank (unchanged); [INVALID_RATE] when not a non-negative number. */
internal fun parseRate(text: String): BigDecimal? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: INVALID_RATE
}

internal fun isCurrencyCode(text: String): Boolean = text.length == 3 && text.all { it in 'A'..'Z' }

/** "Hydraulics, welding,, hydraulics" -> ["Hydraulics", "welding"] (case-insensitive de-dupe). */
internal fun parseSkills(text: String): List<String> =
    text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

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
    val hasProfile by viewModel.hasProfile.collectAsStateWithLifecycle()
    val isAvailable by viewModel.isAvailable.collectAsStateWithLifecycle()
    val maxActiveWos by viewModel.maxActiveWos.collectAsStateWithLifecycle()
    val homeLocationName by viewModel.homeLocationName.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()
    val certifications by viewModel.certifications.collectAsStateWithLifecycle()
    val hourlyRate by viewModel.hourlyRate.collectAsStateWithLifecycle()
    val rateCurrency by viewModel.rateCurrency.collectAsStateWithLifecycle()
    val rateInvalid by viewModel.rateInvalid.collectAsStateWithLifecycle()
    // null = closed; index -1 = new certification
    var editingCert by remember { mutableStateOf<Int?>(null) }

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

            CertificationsSection(
                certifications = certifications,
                onAdd = { editingCert = -1 },
                onEdit = { editingCert = it },
                onRemove = viewModel::removeCertification,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = hourlyRate,
                    onValueChange = { viewModel.hourlyRate.value = it },
                    label = { Text(stringResource(R.string.tech_profile_hourly_rate)) },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    isError = rateInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = rateCurrency,
                    onValueChange = { viewModel.rateCurrency.value = it.take(3).uppercase() },
                    label = { Text(stringResource(R.string.tech_profile_rate_currency)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = rateInvalid,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
            Text(
                text = stringResource(if (rateInvalid) R.string.tech_profile_rate_invalid else R.string.tech_profile_rate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (rateInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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

            if (!hasProfile) {
                Text(
                    text = stringResource(R.string.tech_profile_no_worker),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !isLoading && hasProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) stringResource(R.string.loading) else stringResource(R.string.save))
            }
        }
    }

    editingCert?.let { index ->
        CertificationDialog(
            initial = certifications.getOrNull(index),
            onDismiss = { editingCert = null },
            onSave = { cert ->
                viewModel.putCertification(index.takeIf { it >= 0 }, cert)
                editingCert = null
            },
        )
    }
}

@Composable
private fun CertificationsSection(
    certifications: List<Certification>,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val today = LocalDate.now()
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.tech_profile_certifications),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) { Text(stringResource(R.string.tech_profile_cert_add)) }
        }
        if (certifications.isEmpty()) {
            Text(
                text = stringResource(R.string.tech_profile_cert_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        certifications.forEachIndexed { i, c ->
            val expiry = c.expiresOn?.let { d ->
                stringResource(
                    if (c.isExpired(today)) R.string.tech_profile_cert_expired_on else R.string.tech_profile_cert_expires_on,
                    d.format(format),
                )
            }
            ListItem(
                headlineContent = { Text(c.name) },
                supportingContent = listOfNotNull(c.issuer, c.number, expiry).takeIf { it.isNotEmpty() }?.let { parts ->
                    {
                        Text(
                            text = parts.joinToString(" \u00b7 "),
                            color = if (c.isExpired(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(i) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tech_profile_cert_remove))
                    }
                },
                modifier = Modifier.clickable { onEdit(i) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CertificationDialog(initial: Certification?, onDismiss: () -> Unit, onSave: (Certification) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var issuer by remember { mutableStateOf(initial?.issuer.orEmpty()) }
    var number by remember { mutableStateOf(initial?.number.orEmpty()) }
    var expiresOn by remember { mutableStateOf(initial?.expiresOn) }
    var pickingDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.tech_profile_cert_add else R.string.tech_profile_cert_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tech_profile_cert_name)) },
                    placeholder = { Text(stringResource(R.string.tech_profile_certifications_hint)) },
                )
                OutlinedTextField(
                    value = issuer, onValueChange = { issuer = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tech_profile_cert_issuer)) },
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tech_profile_cert_number)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) {
                        Text(
                            expiresOn?.let {
                                stringResource(R.string.tech_profile_cert_expires_on, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                            } ?: stringResource(R.string.tech_profile_cert_no_expiry),
                        )
                    }
                    if (expiresOn != null) {
                        TextButton(onClick = { expiresOn = null }) { Text(stringResource(R.string.tech_profile_cert_clear_expiry)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Certification(
                            name = name.trim(),
                            issuer = issuer.trim().ifEmpty { null },
                            number = number.trim().ifEmpty { null },
                            expiresOn = expiresOn,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (expiresOn ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { expiresOn = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}
