package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * A saved asset label template (`label_templates` with target `asset`, synced read-only). Bulk QR
 * label jobs print with it; [isDefault] marks the account default the server applies when a job
 * names no template.
 */
data class AssetLabelTemplate(
    val id: String,
    val name: String,
    val widthMm: BigDecimal,
    val heightMm: BigDecimal,
    val isDefault: Boolean,
)

interface LabelTemplateRepository {
    /** Asset label templates, the account default first, then by name. */
    fun observeAssetTemplates(): Flow<List<AssetLabelTemplate>>
}
