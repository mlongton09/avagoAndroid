package com.avago.core.data.v2.repo

import com.avago.core.data.domain.AssetLabelTemplate
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2Fixtures
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Stores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class V2LabelTemplateRepositoryTest {
    private val business = V2Fixtures.business()
    private val active = V2ActiveStores().apply { publish(V2Stores(V2Fixtures.identity, business, V2Fixtures.chat())) }
    private val repo = V2LabelTemplateRepository(V2StoreAccess(active))

    @Test
    fun `asset templates are listed with the account default first`() = runTest {
        assertEquals(emptyList<AssetLabelTemplate>(), repo.observeAssetTemplates().first())
        business.writeWithoutForeignKeys { tx ->
            for ((id, name, target, size, default) in listOf(
                Row(T1, "shelf", "asset", "50" to "25", 0),
                Row(T2, "Asset tag", "asset", "63.5" to "38.1", 1),
                Row(T3, "Bin", "bin", "40" to "20", 1),
                Row(T4, "Big", "asset", "100" to "50", 0),
            )) {
                tx.execute(
                    "INSERT INTO label_templates (id, created_at, updated_at, name, target, width_mm, height_mm, layout, is_default) " +
                        "VALUES (?, 1, 1, ?, ?, ?, ?, '[]', ?)",
                    listOf(id, name, target, size.first, size.second, default),
                )
            }
        }

        assertEquals(
            listOf(
                AssetLabelTemplate(T2, "Asset tag", BigDecimal("63.5"), BigDecimal("38.1"), isDefault = true),
                AssetLabelTemplate(T4, "Big", BigDecimal("100"), BigDecimal("50"), isDefault = false),
                AssetLabelTemplate(T1, "shelf", BigDecimal("50"), BigDecimal("25"), isDefault = false),
            ),
            repo.observeAssetTemplates().first(),
        )
    }

    private data class Row(val id: String, val name: String, val target: String, val size: Pair<String, String>, val default: Int)

    private companion object {
        const val T1 = "0190a1b2-c3d4-7e5f-8a9b-000000000001"
        const val T2 = "0190a1b2-c3d4-7e5f-8a9b-000000000002"
        const val T3 = "0190a1b2-c3d4-7e5f-8a9b-000000000003"
        const val T4 = "0190a1b2-c3d4-7e5f-8a9b-000000000004"
    }
}
