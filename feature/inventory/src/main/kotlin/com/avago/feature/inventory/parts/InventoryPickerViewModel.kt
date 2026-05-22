package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.PartEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PickerPartItem(
    val part: PartEntity,
    val quantityOnHand: Double?,
)

@HiltViewModel
class InventoryPickerViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountFlow = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(Pair(emptyList<PartEntity>(), emptyList<InventoryEntity>()))
        else {
            val db = dbFactory.get(accountId)
            combine(
                db.partDao().observeAll(accountId),
                db.inventoryDao().observeAll(accountId),
            ) { parts, inventories -> Pair(parts, inventories) }
        }
    }

    val filtered: StateFlow<List<PickerPartItem>> = combine(accountFlow, query) { (parts, inventories), q ->
        val invByPart = inventories.associateBy { it.partId }
        parts
            .filter { part ->
                q.isBlank() ||
                    part.name.contains(q, ignoreCase = true) ||
                    part.sku?.contains(q, ignoreCase = true) == true
            }
            .map { part -> PickerPartItem(part, invByPart[part.partId]?.quantityOnHand) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun setQuery(q: String) { query.value = q }
}
