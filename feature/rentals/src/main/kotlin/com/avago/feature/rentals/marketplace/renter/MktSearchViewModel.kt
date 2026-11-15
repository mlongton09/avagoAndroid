package com.avago.feature.rentals.marketplace.renter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MktLocator
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.data.MktUnits
import com.avago.feature.rentals.marketplace.data.RenterSession
import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktPlace
import com.avago.feature.rentals.marketplace.domain.MktRadius
import com.avago.feature.rentals.marketplace.domain.MktSearchItem
import com.avago.feature.rentals.marketplace.domain.MktSearchQuery
import com.avago.feature.rentals.marketplace.domain.MktSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class MktSearchState(
    val query: MktSearchQuery,
    /** Text in the "Where" field. */
    val placeText: String = "",
    val suggestions: List<MktPlace> = emptyList(),
    val attribution: String? = null,
    val usingDeviceLocation: Boolean = false,
    val locating: Boolean = false,
    val locationFailed: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val searched: Boolean = false,
    val items: List<MktSearchItem> = emptyList(),
    val total: Int = 0,
    val truncated: Boolean = false,
    val centreLabel: String? = null,
    val nextCursor: String? = null,
    val facets: List<Pair<String, Int>> = emptyList(),
    val error: MktError? = null,
    /** Paging failed: results stay, "Load more" retries. */
    val moreError: MktError? = null,
) {
    val unit: DistanceUnit get() = query.unit
    /** Currency of the price filter: the results' currency, else the device's. */
    val currency: String get() = items.firstNotNullOfOrNull { it.price?.currency } ?: query.currency ?: deviceCurrency()

    companion object {
        fun deviceCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "USD"
    }
}

/**
 * Renter search (§4, §10.1): a typed place (server lookup, debounced 250 ms from 2 characters)
 * or the device location, a radius in the locale's unit, filters, sort and cursor paging.
 */
@HiltViewModel
class MktSearchViewModel @Inject constructor(
    private val repo: MarketplaceRepository,
    private val locator: MktLocator,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MktSearchState(MktSearchQuery(unit = MktUnits.device(), country = Locale.getDefault().country.takeIf { it.length == 2 })),
    )
    val state: StateFlow<MktSearchState> = _state.asStateFlow()
    val session: StateFlow<RenterSession?> = repo.session

    private var placesJob: Job? = null
    private var searchJob: Job? = null
    private var generation = 0
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun onPlaceText(text: String) {
        _state.update { it.copy(placeText = text, usingDeviceLocation = false, locationFailed = false) }
        placesJob?.cancel()
        val q = text.trim()
        if (q.length < 2) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        placesJob = viewModelScope.launch {
            delay(250)
            when (val r = repo.places(q, _state.value.query.country)) {
                is MktResult.Ok -> _state.update { it.copy(suggestions = r.value.items, attribution = r.value.attribution) }
                is MktResult.Err -> _state.update { it.copy(suggestions = emptyList()) }
            }
        }
    }

    fun selectPlace(p: MktPlace) {
        placesJob?.cancel()
        _state.update {
            it.copy(
                placeText = p.label, suggestions = emptyList(), usingDeviceLocation = false,
                query = it.query.copy(lat = p.lat, lng = p.lng, place = p.label),
            )
        }
        search()
    }

    /** Searches the typed text as a place (the server geocodes it) when no suggestion was picked. */
    fun submitPlace() {
        val text = _state.value.placeText.trim()
        if (text.isEmpty()) return
        placesJob?.cancel()
        _state.update { s ->
            val keep = s.query.place == text && s.query.hasCoordinates
            s.copy(suggestions = emptyList(), query = if (keep) s.query else s.query.copy(lat = null, lng = null, place = text))
        }
        search()
    }

    /** Called once location permission is granted (coarse, balanced power). */
    fun useDeviceLocation() {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true, locationFailed = false) }
        viewModelScope.launch {
            val pos = locator.current(precise = false)
            if (pos == null) {
                _state.update { it.copy(locating = false, locationFailed = true) }
            } else {
                _state.update {
                    it.copy(
                        locating = false, usingDeviceLocation = true, placeText = "", suggestions = emptyList(),
                        query = it.query.copy(lat = pos.first, lng = pos.second, place = null),
                    )
                }
                search()
            }
        }
    }

    fun setRadius(r: Int) = updateAndSearch { it.copy(radius = MktRadius.clamp(r)) }

    fun setKeyword(k: String) = updateAndSearch { it.copy(keyword = k.take(100)) }

    fun setCategory(c: String?) = updateAndSearch { it.copy(category = c) }

    fun setSort(s: String) = updateAndSearch { it.copy(sort = s) }

    /** Applies the filter sheet in one search. */
    fun applyFilters(
        category: String?,
        firstDay: LocalDate?,
        lastDay: LocalDate?,
        minMajor: Long?,
        maxMajor: Long?,
        fulfilment: String?,
        sort: String,
    ) = updateAndSearch {
        val cur = _state.value.currency
        val factor = runCatching { Currency.getInstance(cur).defaultFractionDigits }.getOrNull()?.coerceAtLeast(0)?.let { d -> pow10(d) } ?: 100L
        it.copy(
            category = category, firstDay = firstDay, lastDay = if (firstDay == null) null else lastDay,
            minPriceMinor = minMajor?.times(factor), maxPriceMinor = maxMajor?.times(factor),
            currency = if (minMajor != null || maxMajor != null) cur else null,
            fulfilment = fulfilment, sort = sort,
        )
    }

    fun clearFilters() = updateAndSearch {
        it.copy(keyword = "", category = null, firstDay = null, lastDay = null, minPriceMinor = null, maxPriceMinor = null, currency = null, fulfilment = null, sort = MktSort.DISTANCE)
    }

    fun widen() {
        MktRadius.wider(_state.value.query.radius)?.let { setRadius(it) }
    }

    private fun updateAndSearch(f: (MktSearchQuery) -> MktSearchQuery) {
        _state.update { it.copy(query = f(it.query)) }
        if (_state.value.query.hasLocation) search()
    }

    fun search() {
        val q = _state.value.query
        if (!q.hasLocation) {
            _state.update { it.copy(error = MktError.LocationRequired, searched = true) }
            return
        }
        searchJob?.cancel()
        val gen = ++generation
        _state.update { it.copy(loading = true, error = null, moreError = null) }
        searchJob = viewModelScope.launch {
            val r = repo.search(q.params(zone))
            if (gen != generation) return@launch
            when (r) {
                is MktResult.Ok -> _state.update {
                    it.copy(
                        loading = false, searched = true, items = r.value.items, total = r.value.total, truncated = r.value.truncated,
                        centreLabel = r.value.centreLabel, nextCursor = r.value.nextCursor, facets = r.value.facets,
                    )
                }
                // A failed search keeps the previous results visible under the error.
                is MktResult.Err -> _state.update { it.copy(loading = false, searched = true, error = r.error) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        val gen = generation
        _state.update { it.copy(loadingMore = true, moreError = null) }
        viewModelScope.launch {
            val r = repo.search(s.query.params(zone, cursor))
            if (gen != generation) return@launch
            when (r) {
                is MktResult.Ok -> _state.update { st ->
                    val seen = st.items.map { it.id }.toSet()
                    st.copy(loadingMore = false, items = st.items + r.value.items.filter { it.id !in seen }, nextCursor = r.value.nextCursor)
                }
                is MktResult.Err -> _state.update { it.copy(loadingMore = false, moreError = r.error) }
            }
        }
    }

    private fun pow10(d: Int): Long = (0 until d).fold(1L) { acc, _ -> acc * 10 }
}
