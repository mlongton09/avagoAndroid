package com.avago.core.seed

import android.content.Context
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.ConfigEntity
import com.avago.core.seed.model.AppLimitsSeed
import com.avago.core.seed.model.AssetTypeSeed
import com.avago.core.seed.model.DocTypeSeed
import com.avago.core.seed.model.InspectionTypeSeed
import com.avago.core.seed.model.InventoryCategorySeed
import com.avago.core.seed.model.LogCategoryGroupSeed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbFactory: DatabaseFactory,
    private val appLimits: AppLimits,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Seeds the local Room database with bundled JSON config data on first launch.
     * Safe to call on every app start — it checks for an existing "asset_types" config
     * before inserting anything.
     */
    suspend fun seedIfNeeded(accountId: String) {
        val db = dbFactory.get(accountId)
        val existing = db.configDao().getByKey("system", "asset_types")
        if (existing != null) {
            Timber.d("ConfigSeeder: already seeded, skipping")
            return
        }

        Timber.d("ConfigSeeder: seeding config for account $accountId")
        seedAssetTypes(accountId, db)
        seedLogCategories(accountId, db)
        seedInspectionTypes(accountId, db)
        seedDocTypes(accountId, db)
        seedInventoryCategories(accountId, db)
        seedLimits(accountId, db)
        Timber.d("ConfigSeeder: seeding complete")
    }

    // -------------------------------------------------------------------------

    private suspend fun seedAssetTypes(
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        val jsonString = readAsset("seed/asset_types.json")
        val items = json.decodeFromString<List<AssetTypeSeed>>(jsonString)
        val now = System.currentTimeMillis()
        db.configDao().upsert(
            ConfigEntity(
                configId = UUID.randomUUID().toString(),
                accountId = accountId,
                scope = "system",
                key = "asset_types",
                value = json.encodeToString(items),
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        Timber.d("ConfigSeeder: seeded ${items.size} asset types")
    }

    private suspend fun seedLogCategories(
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        val jsonString = readAsset("seed/log_categories.json")
        val groups = json.decodeFromString<List<LogCategoryGroupSeed>>(jsonString)
        val now = System.currentTimeMillis()
        db.configDao().upsert(
            ConfigEntity(
                configId = UUID.randomUUID().toString(),
                accountId = accountId,
                scope = "system",
                key = "log_categories",
                value = json.encodeToString(groups),
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        Timber.d("ConfigSeeder: seeded log category groups for ${groups.size} asset types")
    }

    private suspend fun seedInspectionTypes(
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        val jsonString = readAsset("seed/inspection_types.json")
        val items = json.decodeFromString<List<InspectionTypeSeed>>(jsonString)
        val now = System.currentTimeMillis()
        db.configDao().upsert(
            ConfigEntity(
                configId = UUID.randomUUID().toString(),
                accountId = accountId,
                scope = "system",
                key = "inspection_types",
                value = json.encodeToString(items),
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        Timber.d("ConfigSeeder: seeded ${items.size} inspection types")
    }

    private suspend fun seedDocTypes(
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        val jsonString = readAsset("seed/doc_types.json")
        val items = json.decodeFromString<List<DocTypeSeed>>(jsonString)
        val now = System.currentTimeMillis()
        db.configDao().upsert(
            ConfigEntity(
                configId = UUID.randomUUID().toString(),
                accountId = accountId,
                scope = "system",
                key = "doc_types",
                value = json.encodeToString(items),
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        Timber.d("ConfigSeeder: seeded ${items.size} doc types")
    }

    private suspend fun seedInventoryCategories(
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        val jsonString = readAsset("seed/inventory_categories.json")
        val items = json.decodeFromString<List<InventoryCategorySeed>>(jsonString)
        val now = System.currentTimeMillis()
        db.configDao().upsert(
            ConfigEntity(
                configId = UUID.randomUUID().toString(),
                accountId = accountId,
                scope = "system",
                key = "inventory_categories",
                value = json.encodeToString(items),
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        Timber.d("ConfigSeeder: seeded ${items.size} inventory categories")
    }

    private suspend fun seedLimits(
        accountId: String,
        db: com.avago.core.data.db.AvagoDatabase,
    ) {
        val jsonString = readAsset("seed/limits.json")
        val limits = json.decodeFromString<AppLimitsSeed>(jsonString)
        val now = System.currentTimeMillis()
        db.configDao().upsert(
            ConfigEntity(
                configId = UUID.randomUUID().toString(),
                accountId = accountId,
                scope = "system",
                key = "limits",
                value = json.encodeToString(limits),
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        appLimits.update(limits)
        Timber.d("ConfigSeeder: seeded limits (max_assets=${limits.max_assets})")
    }

    // -------------------------------------------------------------------------

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
