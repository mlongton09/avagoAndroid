package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.ProductStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InventoryPickerViewModel @Inject constructor(
    inventory: InventoryRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    val filtered: StateFlow<List<ProductStock>> = combine(inventory.observeProductStock(), query) { stock, q ->
        stock.filter { item ->
            q.isBlank() ||
                item.product.name.contains(q, ignoreCase = true) ||
                item.product.sku.contains(q, ignoreCase = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun setQuery(q: String) { query.value = q }
}
