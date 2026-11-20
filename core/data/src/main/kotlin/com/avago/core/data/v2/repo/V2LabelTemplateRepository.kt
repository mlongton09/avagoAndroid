package com.avago.core.data.v2.repo

import com.avago.core.data.domain.AssetLabelTemplate
import com.avago.core.data.domain.LabelTemplateRepository
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.decimalOrNull
import com.avago.core.data.v2.str
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Label templates are maintained on the web; the device only reads the synced rows. */
@Singleton
class V2LabelTemplateRepository @Inject constructor(
    private val store: V2StoreAccess,
) : LabelTemplateRepository {

    override fun observeAssetTemplates(): Flow<List<AssetLabelTemplate>> =
        store.observe(setOf(LABEL_TEMPLATES), emptyList()) { conn -> assetTemplates(conn) }

    companion object {
        private const val LABEL_TEMPLATES = "label_templates"

        fun assetTemplates(conn: V2Connection): List<AssetLabelTemplate> =
            conn.query("SELECT id, name, width_mm, height_mm, is_default FROM $LABEL_TEMPLATES WHERE target = 'asset'")
                .mapNotNull { r ->
                    AssetLabelTemplate(
                        id = r.str("id"),
                        name = r.str("name"),
                        widthMm = r.decimalOrNull("width_mm") ?: return@mapNotNull null,
                        heightMm = r.decimalOrNull("height_mm") ?: return@mapNotNull null,
                        isDefault = r.bool("is_default"),
                    )
                }
                .sortedWith(compareBy<AssetLabelTemplate> { !it.isDefault }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
