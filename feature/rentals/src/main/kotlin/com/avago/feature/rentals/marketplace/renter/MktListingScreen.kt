package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.avago.feature.rentals.marketplace.domain.MktArea
import com.avago.feature.rentals.marketplace.domain.MktAvailabilityDay
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktFulfilmentFlags
import com.avago.feature.rentals.marketplace.domain.MktListing
import com.avago.feature.rentals.marketplace.domain.MktMeetupInfo
import com.avago.feature.rentals.marketplace.domain.MktOwnerRef
import com.avago.feature.rentals.marketplace.domain.MktPolicies
import com.avago.feature.rentals.marketplace.domain.MktPriceTable
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.categoryLabel
import com.avago.feature.rentals.marketplace.ui.distanceText
import com.avago.feature.rentals.marketplace.ui.money
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.unitLabel
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.LabelValue
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

@Composable
fun MktListingScreen(
    onBack: () -> Unit,
    onReserve: (String) -> Unit,
    onMeet: (String) -> Unit,
    vm: MktListingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    MktListingContent(state, onBack, onRetry = vm::load, onReserve = { onReserve(vm.id) }, onMeet = { onMeet(vm.id) })
}

@Composable
internal fun MktListingContent(
    state: MktListingState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReserve: () -> Unit,
    onMeet: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val l = state.listing
    Scaffold(
        topBar = { RentalsTopBar(l?.title(locale) ?: stringResource(R.string.mkt_brand), onBack) },
        bottomBar = {
            if (l != null && (l.canReserve || l.canMeet)) {
                BottomActionBar {
                    // One primary action: reserve / quote when possible, otherwise the meet-up request.
                    if (l.canReserve && l.canMeet) {
                        OutlinedButton(onClick = onMeet, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.mkt_tab_meetup))
                        }
                    }
                    Button(
                        onClick = if (l.canReserve) onReserve else onMeet,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    !l.canReserve -> R.string.mkt_tab_meetup
                                    l.isQuote -> R.string.mkt_tab_quote
                                    else -> R.string.mkt_tab_reserve
                                },
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                l == null && state.loading -> SkeletonList(rows = 5)
                l == null -> {
                    val e = state.error ?: MktError.Generic
                    EmptyState(
                        mktErrorText(e),
                        icon = Icons.Outlined.ErrorOutline,
                        actionLabel = if (e == MktError.NotFound) null else stringResource(R.string.mkt_retry),
                        onAction = if (e == MktError.NotFound) null else onRetry,
                    )
                }
                else -> ListingBody(l, state)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingBody(l: MktListing, state: MktListingState) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = AvagoSpacing.xxl),
    ) {
        state.error?.let { ErrorBanner(mktErrorText(it), modifier = Modifier.padding(AvagoSpacing.lg)) }
        Photos(l.photos)
        Column(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
            Text(l.title(locale), style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                l.category?.let { StatusPill(categoryLabel(it), StatusTone.Neutral) }
                StatusPill(stringResource(if (l.isQuote) R.string.mkt_mode_quote else R.string.mkt_mode_hold), StatusTone.Info)
            }
            l.summary(locale)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            val where = listOfNotNull(
                l.area?.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.mkt_area_unknown),
                distanceText(l.distanceKm, l.distanceMi, state.unit),
            )
            Text(where.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.mkt_approx_location_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        l.description(locale)?.let { d ->
            SectionCard(Modifier.readableWidth(), contentPadding = true) { Text(d, style = MaterialTheme.typography.bodyMedium) }
        }
        if (l.specs.isNotEmpty()) {
            SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_specs)) {
                l.specs.forEach { (k, v) -> LabelValue(k, v) }
            }
        }
        Pricing(l)
        Fulfilment(l.fulfilment, state.unit)
        if (!l.policies.isEmpty) Policies(l.policies)
        Availability(state)
        SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_about_owner), contentPadding = true) {
            Text(l.owner.name, style = MaterialTheme.typography.titleMedium)
            l.ownerAbout?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                if (l.reviewCount > 0) stringResource(R.string.mkt_reviews_count, l.reviewCount.toString()) else stringResource(R.string.mkt_no_reviews),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (l.isQuote) InfoNote(stringResource(R.string.mkt_quote_mode_note))
        // Online payment is only mentioned when the owner has it enabled (§10.1); no payment UI in-app.
        InfoNote(stringResource(if (l.onlinePayments) R.string.mkt_pay_online_note else R.string.mkt_pay_in_person_note))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Photos(photos: List<String>) {
    if (photos.isEmpty()) {
        ListingPhoto(null, Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg).aspectRatio(16f / 9f))
        return
    }
    val pager = rememberPagerState { photos.size }
    Box(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg)) {
        HorizontalPager(pager, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large)) { i ->
            val label = stringResource(R.string.mkt_photo_n, i + 1, photos.size)
            ListingPhoto(photos[i], Modifier.fillMaxSize().semantics { contentDescription = label })
        }
        if (photos.size > 1) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomEnd).padding(AvagoSpacing.sm),
            ) {
                Text("${pager.currentPage + 1}/${photos.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = AvagoSpacing.sm, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun Pricing(l: MktListing) {
    val p = l.price
    SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_pricing)) {
        if (p == null || p.isEmpty) {
            LabelValue(stringResource(R.string.mkt_price), stringResource(R.string.mkt_price_on_request))
        } else {
            listOf(
                R.string.mkt_per_hour to p.hourly, R.string.mkt_per_day to p.day, R.string.mkt_per_weekend to p.weekend,
                R.string.mkt_per_week to p.week, R.string.mkt_per_month to p.month, R.string.mkt_min_charge to p.minCharge,
            ).forEach { (label, v) -> if (v != null) LabelValue(stringResource(label), money(v, p.currency), emphasise = label == R.string.mkt_per_day) }
        }
    }
}

@Composable
private fun Fulfilment(f: MktFulfilmentFlags, unit: DistanceUnit) {
    SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_getting_it), contentPadding = true) {
        if (f.pickup) Text("• " + stringResource(R.string.mkt_ful_pickup_long), style = MaterialTheme.typography.bodyMedium)
        if (f.delivery) {
            Text("• " + stringResource(R.string.mkt_ful_delivery_long), style = MaterialTheme.typography.bodyMedium)
            f.deliveryRadiusKm?.let { km ->
                val v = if (unit == DistanceUnit.MI) (km / 1.609344).roundToLong() else km.roundToLong()
                Text(stringResource(R.string.mkt_ful_delivery_radius, v.toString(), unitLabel(unit)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = AvagoSpacing.lg))
            }
        }
        if (f.meetup) Text("• " + stringResource(R.string.mkt_ful_meetup_long), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Policies(p: MktPolicies) {
    SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_policies), contentPadding = true) {
        val lines = listOfNotNull(
            p.depositMinor?.let { stringResource(R.string.mkt_policy_deposit, money(it, p.depositCurrency)) },
            p.minDriverAge?.let { stringResource(R.string.mkt_policy_min_age, it.toString()) },
            if (p.requireLicence) stringResource(R.string.mkt_policy_licence) else null,
            if (p.requireCoi) stringResource(R.string.mkt_policy_coi) else null,
            if (p.requireSignature) stringResource(R.string.mkt_policy_signature) else null,
            p.requiredDocs.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.mkt_policy_docs, it.joinToString(", ")) },
        )
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Availability(state: MktListingState) {
    SectionCard(Modifier.readableWidth(), title = stringResource(R.string.mkt_availability), contentPadding = true) {
        val days = state.availability
        when {
            state.availabilityFailed -> Text(stringResource(R.string.mkt_availability_unavailable), style = MaterialTheme.typography.bodyMedium)
            days == null -> Text(stringResource(R.string.mkt_loading), style = MaterialTheme.typography.bodyMedium)
            else -> {
                Text(stringResource(R.string.mkt_availability_next_days, MktListingViewModel.AVAILABILITY_DAYS.toString()), style = MaterialTheme.typography.labelLarge)
                val fmt = DateTimeFormatter.ofPattern("EEE d", LocalConfiguration.current.locales[0])
                val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                val free = stringResource(R.string.mkt_day_available)
                val booked = stringResource(R.string.mkt_day_booked)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    days.forEach { d ->
                        val desc = "${full.format(d.day)}: ${if (d.available) free else booked}"
                        StatusPill(
                            fmt.format(d.day),
                            if (d.available) StatusTone.Success else StatusTone.Neutral,
                            modifier = Modifier.semantics { contentDescription = desc },
                        )
                    }
                }
                Text(stringResource(R.string.mkt_availability_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal val previewListing = MktListing(
    id = "l1", category = "trailers", currency = "CAD", title = "Utility trailer 5x8", titleI18n = emptyMap(),
    summaryI18n = mapOf("en" to "Single-axle trailer, 1,500 kg payload."), descriptionI18n = mapOf("en" to "Ramp gate, LED lights, 2\" ball."),
    specs = listOf("Payload" to "1500 kg"), photos = emptyList(),
    price = MktPriceTable("CAD", null, 4500, null, 22000, null, null), owner = MktOwnerRef("acme", "Cariboo Rentals"), ownerAbout = "Family-run since 1998.",
    reviewCount = 0, locationId = "loc1", area = MktArea("100 Mile House", "BC", "CA", null, null), distanceKm = 3.4, distanceMi = 2.1,
    fulfilment = MktFulfilmentFlags(true, true, true, 40.0), bookingMode = "hold", minLeadMinutes = 60, maxDays = 30,
    meetup = MktMeetupInfo(30, 24, "America/Vancouver"),
    policies = MktPolicies(20000, "CAD", emptyList(), 21, true, true, false), onlinePayments = false,
)

@MktPreviews
@Composable
private fun MktListingPreview() {
    AvagoTheme {
        val today = LocalDate.now()
        MktListingContent(
            MktListingState(
                loading = false, listing = previewListing,
                availability = (0 until 10).map { MktAvailabilityDay(today.plusDays(it.toLong()), today.plusDays(it.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant(), it % 4 != 2, 1) },
            ),
            onBack = {}, onRetry = {}, onReserve = {}, onMeet = {},
        )
    }
}
