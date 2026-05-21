package com.avago.core.seed

import com.avago.core.seed.model.AppLimitsSeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLimits @Inject constructor() {
    private val _limits = MutableStateFlow(AppLimitsSeed())
    val limits: StateFlow<AppLimitsSeed> = _limits

    val maxAssets: Int get() = _limits.value.max_assets
    val maxLogEntries: Int get() = _limits.value.max_log_entries
    val maxWorkOrders: Int get() = _limits.value.max_work_orders
    val maxInventoryParts: Int get() = _limits.value.max_inventory_parts
    val maxDocs: Int get() = _limits.value.max_docs
    val maxPhotosPerEntry: Int get() = _limits.value.max_photos_per_entry
    val maxTeamMembers: Int get() = _limits.value.max_team_members

    fun update(seed: AppLimitsSeed) {
        _limits.value = seed
    }
}
