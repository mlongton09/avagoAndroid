package com.avago.feature.assets.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.feature.assets.data.AssetScopeRepository
import com.avago.feature.assets.model.AssetScope
import com.avago.feature.assets.model.AssetTypeCatalog
import com.avago.feature.assets.model.AssetTypeGroup
import com.avago.feature.assets.model.AssetTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Account asset-type scope for the type picker and the asset form. */
@HiltViewModel
class AssetScopeViewModel @Inject constructor(
    private val repository: AssetScopeRepository,
) : ViewModel() {
    private val _catalog = MutableStateFlow(AssetTypeCatalog.Default)
    val catalog: StateFlow<AssetTypeCatalog> = _catalog.asStateFlow()

    init {
        viewModelScope.launch { _catalog.value = repository.catalog() }
    }

    fun groups(currentType: String? = null): List<AssetTypeGroup> =
        AssetScope.visibleGroups(AssetTypes.grouped, _catalog.value, currentType)
}
