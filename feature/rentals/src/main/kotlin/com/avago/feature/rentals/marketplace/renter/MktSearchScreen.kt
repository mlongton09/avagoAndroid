package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.avago.feature.rentals.marketplace.domain.MktArea
import com.avago.feature.rentals.marketplace.domain.MktCardPrice
import com.avago.feature.rentals.marketplace.domain.MktCategories
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktFulfilment
import com.avago.feature.rentals.marketplace.domain.MktFulfilmentFlags
import com.avago.feature.rentals.marketplace.domain.MktOwnerRef
import com.avago.feature.rentals.marketplace.domain.MktRadius
import com.avago.feature.rentals.marketplace.domain.MktSearchItem
import com.avago.feature.rentals.marketplace.domain.MktSearchQuery
import com.avago.feature.rentals.marketplace.domain.MktSort
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.cardPriceText
import com.avago.feature.rentals.marketplace.ui.categoryLabel
import com.avago.feature.rentals.marketplace.ui.distanceText
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.rememberLocationRequest
import com.avago.feature.rentals.marketplace.ui.unitLabel
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.SectionHeader
import com.avago.feature.rentals.ui.readableWidth
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MktSearchScreen(
    onBack: (() -> Unit)?,
    onOpenListing: (id: String, lat: Double?, lng: Double?) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenAccount: () -> Unit,
    vm: MktSearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val requestLocation = rememberLocationRequest(precise = false) { vm.useDeviceLocation() }
    MktSearchContent(
        state = state,
        onBack = onBack,
        actions = MktSearchActions(
            onPlaceText = vm::onPlaceText,
            onSelectPlace = vm::selectPlace,
            onSubmitPlace = vm::submitPlace,
            onUseLocation = requestLocation,
            onKeyword = vm::setKeyword,
            onRadius = vm::setRadius,
            onApplyFilters = vm::applyFilters,
            onClearFilters = vm::clearFilters,
            onWiden = vm::widen,
            onRetry = vm::search,
            onLoadMore = vm::loadMore,
            onOpen = { onOpenListing(it, state.query.lat, state.query.lng) },
            onOpenRequests = onOpenRequests,
            onOpenAccount = onOpenAccount,
        ),
    )
}

internal class MktSearchActions(
    val onPlaceText: (String) -> Unit = {},
    val onSelectPlace: (com.avago.feature.rentals.marketplace.domain.MktPlace) -> Unit = {},
    val onSubmitPlace: () -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onKeyword: (String) -> Unit = {},
    val onRadius: (Int) -> Unit = {},
    val onApplyFilters: (String?, LocalDate?, LocalDate?, Long?, Long?, String?, String) -> Unit = { _, _, _, _, _, _, _ -> },
    val onClearFilters: () -> Unit = {},
    val onWiden: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onOpenRequests: () -> Unit = {},
    val onOpenAccount: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MktSearchContent(state: MktSearchState, onBack: (() -> Unit)?, actions: MktSearchActions) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.nextCursor) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.nextCursor != null && last >= listState.layoutInfo.totalItemsCount - 3) actions.onLoadMore() }
    }
    Scaffold(
        topBar = {
            RentalsTopBar(stringResource(R.string.mkt_find_rentals), onBack) {
                IconButton(onClick = actions.onOpenRequests) {
                    Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = stringResource(R.string.mkt_my_requests))
                }
                IconButton(onClick = actions.onOpenAccount) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.mkt_account))
                }
            }
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize()) {
            item(key = "where") { WhereField(state, actions) }
            item(key = "keyword") { KeywordField(state.query.keyword, actions.onKeyword) }
            item(key = "radius") { RadiusChips(state.query.radius, state.unit, actions.onRadius) }
            item(key = "filters") {
                Row(
                    Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                ) {
                    val n = state.query.activeFilters - (if (state.query.keyword.isNotBlank()) 1 else 0)
                    OutlinedButton(onClick = { showFilters = true }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AvagoSpacing.xs))
                        Text(if (n > 0) stringResource(R.string.mkt_filters_n, n) else stringResource(R.string.mkt_filters))
                    }
                    Text(
                        stringResource(R.string.mkt_sort_by_n, sortLabel(MktSort.effective(state.query.sort, state.query.keyword))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.loading) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = AvagoSpacing.sm)) }
            state.error?.let { e ->
                item(key = "error") { SearchError(e, actions.onRetry) }
            }
            if (state.searched && state.items.isNotEmpty()) {
                item(key = "count") {
                    val place = state.centreLabel ?: if (state.usingDeviceLocation) stringResource(R.string.mkt_your_location) else state.query.place.orEmpty()
                    Column(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm)) {
                        Text(
                            stringResource(R.string.mkt_results_count, state.total.toString(), state.query.radius.toString(), unitLabel(state.unit), place),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (state.truncated) {
                            Text(stringResource(R.string.mkt_truncated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            when {
                state.loading && state.items.isEmpty() -> item(key = "skeleton") { SkeletonList(rows = 4) }
                !state.searched && state.error == null -> item(key = "intro") {
                    EmptyState(
                        stringResource(R.string.mkt_err_location_required_title),
                        icon = Icons.Outlined.LocationOn,
                        message = stringResource(R.string.mkt_err_location_required_body),
                        actionLabel = stringResource(R.string.mkt_use_my_location),
                        onAction = actions.onUseLocation,
                    )
                }
                state.searched && state.items.isEmpty() && state.error == null && !state.loading -> item(key = "empty") {
                    val wider = MktRadius.wider(state.query.radius)
                    EmptyState(
                        stringResource(R.string.mkt_empty_title),
                        icon = Icons.Outlined.SearchOff,
                        message = stringResource(R.string.mkt_empty_body),
                        actionLabel = wider?.let { stringResource(R.string.mkt_widen_radius, it.toString(), unitLabel(state.unit)) },
                        onAction = wider?.let { { actions.onWiden() } },
                    )
                }
            }
            items(state.items, key = { "i:" + it.id }) { item ->
                ResultCard(item, state.unit, onClick = { actions.onOpen(item.id) })
            }
            if (state.nextCursor != null || state.loadingMore || state.moreError != null) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(AvagoSpacing.lg), contentAlignment = Alignment.Center) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                state.moreError?.let { Text(mktErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                OutlinedButton(onClick = actions.onLoadMore, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Text(stringResource(R.string.mkt_inbox_load_more))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "attribution") {
                state.attribution?.let {
                    Text(
                        stringResource(R.string.mkt_geo_attribution_prefix) + " " + it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.readableWidth().padding(AvagoSpacing.lg),
                    )
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(state, onDismiss = { showFilters = false }, onApply = { c, a, b, min, max, f, s ->
            showFilters = false
            actions.onApplyFilters(c, a, b, min, max, f, s)
        }, onClear = {
            showFilters = false
            actions.onClearFilters()
        })
    }
}

@Composable
private fun WhereField(state: MktSearchState, actions: MktSearchActions) {
    val focus = LocalFocusManager.current
    Column(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm)) {
        OutlinedTextField(
            value = if (state.usingDeviceLocation) "" else state.placeText,
            onValueChange = actions.onPlaceText,
            label = { Text(stringResource(R.string.mkt_where)) },
            placeholder = {
                Text(stringResource(if (state.usingDeviceLocation) R.string.mkt_using_your_location else R.string.mkt_where_placeholder))
            },
            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
            trailingIcon = {
                if (state.locating) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = actions.onUseLocation, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.mkt_use_my_location))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); actions.onSubmitPlace() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.usingDeviceLocation) {
            Text(
                stringResource(R.string.mkt_using_your_location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AvagoSpacing.xs),
            )
        }
        if (state.locating) Text(stringResource(R.string.mkt_locating), style = MaterialTheme.typography.bodySmall)
        if (state.locationFailed) {
            Text(stringResource(R.string.mkt_geo_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.suggestions.isNotEmpty()) {
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = AvagoSpacing.xs)) {
                Column {
                    state.suggestions.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                            modifier = Modifier
                                .heightIn(min = MinTouch)
                                .clickable(role = Role.Button) { focus.clearFocus(); actions.onSelectPlace(p) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordField(keyword: String, onSubmit: (String) -> Unit) {
    var text by rememberSaveable(keyword) { mutableStateOf(keyword) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(100) },
        label = { Text(stringResource(R.string.mkt_keyword)) },
        placeholder = { Text(stringResource(R.string.mkt_keyword_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSubmit(text) }),
        modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadiusChips(radius: Int, unit: DistanceUnit, onRadius: (Int) -> Unit) {
    Column(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)) {
        Text(stringResource(R.string.mkt_radius), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            MktRadius.CHOICES.forEach { r ->
                FilterChip(
                    selected = r == radius,
                    onClick = { onRadius(r) },
                    label = { Text(stringResource(R.string.mkt_within, r.toString(), unitLabel(unit))) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
            }
        }
    }
}

@Composable
private fun SearchError(e: MktError, onRetry: () -> Unit) {
    val (title, body) = when (e) {
        MktError.RateLimited -> R.string.mkt_err_busy_title to R.string.mkt_err_busy_body
        MktError.PlaceNotFound -> R.string.mkt_err_place_not_found_title to R.string.mkt_err_place_not_found_body
        MktError.LocationRequired -> R.string.mkt_err_location_required_title to R.string.mkt_err_location_required_body
        MktError.InvalidValue, MktError.InvalidRange -> R.string.mkt_err_invalid_search_title to R.string.mkt_err_invalid_search_body
        MktError.Network -> R.string.mkt_err_network to null
        else -> R.string.mkt_err_unavailable_title to R.string.mkt_err_unavailable_body
    }
    val text = stringResource(title) + (body?.let { "\n" + stringResource(it) } ?: "")
    val retry = e == MktError.Network || e == MktError.RateLimited || e == MktError.Generic || e == MktError.NotFound
    ErrorBanner(text, onRetry = if (retry) onRetry else null, retryLabel = stringResource(R.string.mkt_retry))
}

@Composable
internal fun sortLabel(sort: String): String = stringResource(
    when (sort) {
        MktSort.PRICE -> R.string.mkt_sort_price
        MktSort.SOONEST -> R.string.mkt_sort_soonest
        MktSort.RELEVANCE -> R.string.mkt_sort_relevance
        else -> R.string.mkt_sort_distance
    },
)

@Composable
private fun ResultCard(item: MktSearchItem, unit: DistanceUnit, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val title = item.title(locale)
    Card(
        onClick = onClick,
        modifier = Modifier
            .readableWidth()
            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    ) {
        Row(Modifier.padding(AvagoSpacing.md), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
            ListingPhoto(item.photo, Modifier.size(88.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(cardPriceText(item.price), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                val where = listOfNotNull(distanceText(item.distanceKm, item.distanceMi, unit), item.area?.label?.takeIf { it.isNotBlank() })
                if (where.isNotEmpty()) Text(where.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.owner.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AvailabilityPill(item)
            }
        }
    }
}

@Composable
private fun AvailabilityPill(item: MktSearchItem) {
    val zone = java.time.ZoneId.systemDefault()
    val next = item.nextAvailable
    when {
        item.available == true -> StatusPill(stringResource(R.string.mkt_available_dates), StatusTone.Success)
        item.available == false -> StatusPill(stringResource(R.string.mkt_not_available_dates), StatusTone.Warning)
        next == null -> StatusPill(stringResource(R.string.mkt_no_availability_30), StatusTone.Neutral)
        !next.isAfter(Instant.now()) -> StatusPill(stringResource(R.string.mkt_available_now), StatusTone.Success)
        else -> StatusPill(
            stringResource(R.string.mkt_next_available, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(next)),
            StatusTone.Info,
        )
    }
}

/** Listing photo (decorative: the card text names the item) with an icon placeholder. */
@Composable
internal fun ListingPhoto(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        Icon(Icons.Filled.Construction, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null && !LocalInspectionMode.current) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: MktSearchState,
    onDismiss: () -> Unit,
    onApply: (String?, LocalDate?, LocalDate?, Long?, Long?, String?, String) -> Unit,
    onClear: () -> Unit,
) {
    val q = state.query
    var category by rememberSaveable { mutableStateOf(q.category) }
    var first by rememberSaveable { mutableStateOf(q.firstDay?.toString()) }
    var last by rememberSaveable { mutableStateOf(q.lastDay?.toString()) }
    val fraction = remember(state.currency) {
        val d = runCatching { java.util.Currency.getInstance(state.currency).defaultFractionDigits }.getOrNull()?.coerceAtLeast(0) ?: 2
        (0 until d).fold(1L) { acc, _ -> acc * 10 }
    }
    var min by rememberSaveable { mutableStateOf(q.minPriceMinor?.div(fraction)?.toString().orEmpty()) }
    var max by rememberSaveable { mutableStateOf(q.maxPriceMinor?.div(fraction)?.toString().orEmpty()) }
    var fulfilment by rememberSaveable { mutableStateOf(q.fulfilment) }
    var sort by rememberSaveable { mutableStateOf(q.sort) }
    var pickDates by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        ) {
            Text(stringResource(R.string.mkt_filters), style = MaterialTheme.typography.titleLarge)
            SectionHeader(stringResource(R.string.mkt_category), Modifier.padding(start = 0.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.mkt_all_categories)) }, modifier = Modifier.heightIn(min = MinTouch))
                MktCategories.ALL.forEach { c ->
                    val count = state.facets.firstOrNull { it.first == c }?.second
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(categoryLabel(c) + (count?.let { " ($it)" } ?: "")) },
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
            }
            SectionHeader(stringResource(R.string.mkt_dates))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.heightIn(min = MinTouch).weight(1f)) {
                    Text(
                        first?.let { a -> dayRange(LocalDate.parse(a), last?.let { LocalDate.parse(it) }) } ?: stringResource(R.string.mkt_any_dates),
                    )
                }
                if (first != null) {
                    TextButton(onClick = { first = null; last = null }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.mkt_any))
                    }
                }
            }
            SectionHeader(stringResource(R.string.mkt_price))
            Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                OutlinedTextField(
                    value = min, onValueChange = { v -> min = v.filter { it.isDigit() }.take(7) },
                    label = { Text(stringResource(R.string.mkt_min_price_cur, state.currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = max, onValueChange = { v -> max = v.filter { it.isDigit() }.take(7) },
                    label = { Text(stringResource(R.string.mkt_max_price_cur, state.currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            SectionHeader(stringResource(R.string.mkt_fulfilment))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                FilterChip(selected = fulfilment == null, onClick = { fulfilment = null }, label = { Text(stringResource(R.string.mkt_any)) }, modifier = Modifier.heightIn(min = MinTouch))
                listOf(MktFulfilment.PICKUP to R.string.mkt_ful_pickup, MktFulfilment.DELIVERY to R.string.mkt_ful_delivery, MktFulfilment.MEETUP to R.string.mkt_ful_meetup).forEach { (f, label) ->
                    FilterChip(selected = fulfilment == f, onClick = { fulfilment = f }, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = MinTouch))
                }
            }
            SectionHeader(stringResource(R.string.mkt_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                MktSort.ALL.filter { it != MktSort.RELEVANCE || q.keyword.isNotBlank() }.forEach { s ->
                    FilterChip(selected = MktSort.effective(sort, q.keyword) == s, onClick = { sort = s }, label = { Text(sortLabel(s)) }, modifier = Modifier.heightIn(min = MinTouch))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = AvagoSpacing.md), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                TextButton(onClick = onClear, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_clear_filters)) }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        onApply(
                            category, first?.let(LocalDate::parse), last?.let(LocalDate::parse),
                            min.toLongOrNull(), max.toLongOrNull(), fulfilment, sort,
                        )
                    },
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.mkt_apply)) }
            }
        }
    }
    if (pickDates) {
        DateRangeDialog(
            first?.let(LocalDate::parse), last?.let(LocalDate::parse),
            onDismiss = { pickDates = false },
            onPick = { a, b -> first = a.toString(); last = (b ?: a).toString(); pickDates = false },
        )
    }
}

internal fun dayRange(a: LocalDate, b: LocalDate?): String {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return if (b == null || b == a) f.format(a) else "${f.format(a)} – ${f.format(b)}"
}

/** Material date-range picker limited to today onwards (dates in UTC millis, as the picker uses). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangeDialog(
    first: LocalDate?,
    last: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalDate?) -> Unit,
    maxDays: Long = 365,
) {
    val today = LocalDate.now()
    fun ms(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = first?.let(::ms),
        initialSelectedEndDateMillis = last?.let(::ms),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = day(utcTimeMillis)
                return !d.isBefore(today) && !d.isAfter(today.plusDays(maxDays))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedStartDateMillis?.let { a -> onPick(day(a), state.selectedEndDateMillis?.let(::day)) } ?: onDismiss() },
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.rentals_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f, fill = false))
    }
}

/** Single-day Material picker (today onwards) used by the meet-up counter / reserve sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) } },
    ) { androidx.compose.material3.DatePicker(state = state) }
}

internal val previewItems = listOf(
    MktSearchItem(
        "l1", "Utility trailer 5x8", emptyMap(), emptyMap(), "trailers", null, MktOwnerRef("acme", "Cariboo Rentals"),
        MktCardPrice("CAD", 4500, "day", 4500), 3.4, 2.1, MktArea("100 Mile House", "BC", "CA", null, null),
        MktFulfilmentFlags(true, false, true), "hold", null, Instant.now(),
    ),
    MktSearchItem(
        "l2", "Mini excavator with a very long name for wrapping", emptyMap(), emptyMap(), "construction", null, MktOwnerRef(null, "Lone Butte Equipment"),
        null, 22.0, 13.7, MktArea("Lone Butte", "BC", "CA", null, null), MktFulfilmentFlags(true, true, false), "quote", null, null,
    ),
)

@MktPreviews
@Composable
private fun MktSearchPreview() {
    AvagoTheme {
        MktSearchContent(
            state = MktSearchState(
                MktSearchQuery(place = "100 Mile House", radius = 50),
                placeText = "100 Mile House", searched = true, items = previewItems, total = 2, centreLabel = "100 Mile House, BC", nextCursor = "c",
            ),
            onBack = {},
            actions = MktSearchActions(),
        )
    }
}

@MktPreviews
@Composable
private fun MktSearchEmptyPreview() {
    AvagoTheme {
        MktSearchContent(
            state = MktSearchState(MktSearchQuery(place = "Lac la Hache", radius = 25), placeText = "Lac la Hache", searched = true),
            onBack = null,
            actions = MktSearchActions(),
        )
    }
}
