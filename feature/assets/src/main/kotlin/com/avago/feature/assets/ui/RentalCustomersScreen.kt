package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.model.RentalCustomer
import com.avago.feature.assets.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalCustomersScreen(
    onBack: () -> Unit,
    onAddCustomer: () -> Unit,
    onEditCustomer: (customerId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RentalCustomersViewModel = hiltViewModel(),
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Group alphabetically
    val sorted = customers.sortedBy { it.name }
    val grouped = sorted.groupBy { c ->
        val first = c.name.firstOrNull()?.uppercaseChar()
        if (first != null && first.isLetter()) first else '#'
    }
    val sortedLetters = grouped.keys.sortedWith { a, b ->
        when {
            a == '#' -> 1
            b == '#' -> -1
            else -> a.compareTo(b)
        }
    }

    // Compute first item index for each letter section (header + rows)
    val sectionStartIndices = mutableMapOf<Char, Int>()
    var idx = 0
    sortedLetters.forEach { letter ->
        sectionStartIndices[letter] = idx
        idx += 1 + (grouped[letter]?.size ?: 0)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                title = { Text(stringResource(R.string.rental_customers_title)) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCustomer,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rental_customer_add_title))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isLoading && customers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.rental_customers_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        sortedLetters.forEach { letter ->
                            stickyHeader(key = "header_$letter") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = letter.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(grouped[letter] ?: emptyList(), key = { it.rental_customer_id }) { customer ->
                                CustomerContactRow(
                                    customer = customer,
                                    onClick = { onEditCustomer(customer.rental_customer_id) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }

                    // Side alphabet index
                    if (sortedLetters.size > 1) {
                        AlphabetSideIndex(
                            letters = sortedLetters,
                            onLetterClick = { letter ->
                                scope.launch {
                                    sectionStartIndices[letter]?.let { listState.scrollToItem(it) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerContactRow(
    customer: RentalCustomer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(name = customer.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val secondary = listOfNotNull(customer.company, customer.email, customer.phone)
                .joinToString(" · ")
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContactAvatar(name: String, modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFF3399DC), Color(0xFF34C759), Color(0xFFFF9500),
        Color(0xFFE6443B), Color(0xFF8E5CF5), Color(0xFF00B0B5),
        Color(0xFFFA5E59), Color(0xFF4990E2),
    )
    val color = colors[Math.abs(name.hashCode()) % colors.size]
    val initials = name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun AlphabetSideIndex(
    letters: List<Char>,
    onLetterClick: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    fun letterAt(yPx: Float): Char? {
        if (totalHeightPx == 0 || letters.isEmpty()) return null
        val idx = ((yPx / totalHeightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[idx]
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { totalHeightPx = it.height }
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    letterAt(offset.y)?.let { onLetterClick(it) }
                }
            }
            .pointerInput(letters) {
                detectDragGestures { change, _ ->
                    change.consume()
                    letterAt(change.position.y)?.let { onLetterClick(it) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
