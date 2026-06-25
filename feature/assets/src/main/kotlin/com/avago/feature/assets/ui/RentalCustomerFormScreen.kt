package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.assets.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalCustomerFormScreen(
    customerId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RentalCustomerFormViewModel = hiltViewModel(),
) {
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-load customer if editing
    LaunchedEffect(customerId) {
        if (customerId != null) {
            viewModel.loadCustomer(customerId)
        }
    }

    // Pre-fill form fields once customer loads
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var prefilledFromCustomer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(customer) {
        val c = customer
        if (c != null && !prefilledFromCustomer) {
            name = c.name
            email = c.email ?: ""
            phone = c.phone ?: ""
            company = c.company ?: ""
            address = c.address ?: ""
            notes = c.notes ?: ""
            prefilledFromCustomer = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val isEditing = customerId != null
    val title = if (isEditing) stringResource(R.string.rental_customer_edit_title)
    else stringResource(R.string.rental_customer_add_title)

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
                        )
                    }
                },
                title = { Text(title) },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    } else {
                        TextButton(
                            onClick = {
                                if (name.isBlank()) {
                                    nameError = true
                                    return@TextButton
                                }
                                viewModel.saveCustomer(
                                    customerId = customerId,
                                    name = name.trim(),
                                    email = email.trim().takeIf { it.isNotBlank() },
                                    phone = phone.trim().takeIf { it.isNotBlank() },
                                    company = company.trim().takeIf { it.isNotBlank() },
                                    address = address.trim().takeIf { it.isNotBlank() },
                                    notes = notes.trim().takeIf { it.isNotBlank() },
                                    onSuccess = onSaved,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.asset_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.rental_customer_name_label)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.rental_customer_name_required)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.rental_customer_email_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.rental_customer_phone_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text(stringResource(R.string.rental_customer_company_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.rental_customer_address_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.rental_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    viewModel.saveCustomer(
                        customerId = customerId,
                        name = name.trim(),
                        email = email.trim().takeIf { it.isNotBlank() },
                        phone = phone.trim().takeIf { it.isNotBlank() },
                        company = company.trim().takeIf { it.isNotBlank() },
                        address = address.trim().takeIf { it.isNotBlank() },
                        notes = notes.trim().takeIf { it.isNotBlank() },
                        onSuccess = onSaved,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.asset_save))
            }
            if (isEditing && customerId != null) {
                Spacer(Modifier.height(8.dp))
                var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text(stringResource(R.string.rental_customer_delete_title)) },
                        text = { Text(stringResource(R.string.rental_customer_delete_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteDialog = false
                                viewModel.deleteCustomer(customerId, onSuccess = onSaved)
                            }) {
                                Text(
                                    stringResource(R.string.rental_customer_delete_confirm),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text(stringResource(R.string.asset_cancel))
                            }
                        },
                    )
                }
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !isSaving,
                ) {
                    Text(stringResource(R.string.rental_customer_delete))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
