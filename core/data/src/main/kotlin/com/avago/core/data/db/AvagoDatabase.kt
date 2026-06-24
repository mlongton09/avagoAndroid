package com.avago.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.avago.core.data.db.converters.Converters
import com.avago.core.data.db.dao.AccountRolePermissionsDao
import com.avago.core.data.db.dao.AssetLocationHistoryDao
import com.avago.core.data.db.dao.LabelTemplateDao
import com.avago.core.data.db.dao.AssetDao
import com.avago.core.data.db.dao.AssetCriticalityDao
import com.avago.core.data.db.dao.AssetCustomStatusDao
import com.avago.core.data.db.dao.AssetModelDao
import com.avago.core.data.db.dao.AssetStatusDao
import com.avago.core.data.db.dao.BinDao
import com.avago.core.data.db.dao.CategoryDao
import com.avago.core.data.db.dao.ConfigDao
import com.avago.core.data.db.dao.CustomFieldDefinitionDao
import com.avago.core.data.db.dao.CycleCountDao
import com.avago.core.data.db.dao.CycleCountLineDao
import com.avago.core.data.db.dao.DeviceDao
import com.avago.core.data.db.dao.DocDao
import com.avago.core.data.db.dao.EventDao
import com.avago.core.data.db.dao.GlAccountDao
import com.avago.core.data.db.dao.GrnDao
import com.avago.core.data.db.dao.GrnLineDao
import com.avago.core.data.db.dao.InventoryDao
import com.avago.core.data.db.dao.InventoryTransactionDao
import com.avago.core.data.db.dao.ItemDao
import com.avago.core.data.db.dao.JobDao
import com.avago.core.data.db.dao.LocationDao
import com.avago.core.data.db.dao.LogCostLineDao
import com.avago.core.data.db.dao.LogDao
import com.avago.core.data.db.dao.MeterReadingDao
import com.avago.core.data.db.dao.MeterTriggerDao
import com.avago.core.data.db.dao.OwnerAssignmentDao
import com.avago.core.data.db.dao.PartDao
import com.avago.core.data.db.dao.PartIssueDao
import com.avago.core.data.db.dao.PartIssueLineDao
import com.avago.core.data.db.dao.PartTransferRequestDao
import com.avago.core.data.db.dao.PhotoDao
import com.avago.core.data.db.dao.PmPlanDao
import com.avago.core.data.db.dao.PmPlanIntervalDao
import com.avago.core.data.db.dao.PoCommentDao
import com.avago.core.data.db.dao.PoLineDao
import com.avago.core.data.db.dao.PurchaseOrderDao
import com.avago.core.data.db.dao.RcaReportDao
import com.avago.core.data.db.dao.RequestPortalDao
import com.avago.core.data.db.dao.RolePermissionDefaultsDao
import com.avago.core.data.db.dao.RoleLabelCacheDao
import com.avago.core.data.db.dao.ScheduleDao
import com.avago.core.data.db.dao.ServiceDao
import com.avago.core.data.db.dao.ScoutHistoryDao
import com.avago.core.data.db.dao.ScoutPendingDao
import com.avago.core.data.db.dao.StockingLevelDao
import com.avago.core.data.db.dao.SyncConflictDao
import com.avago.core.data.db.dao.SyncMetadataDao
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.dao.TechLaborRateDao
import com.avago.core.data.db.dao.TechProfileDao
import com.avago.core.data.db.dao.UserDao
import com.avago.core.data.db.dao.VendorContactDao
import com.avago.core.data.db.dao.VendorDao
import com.avago.core.data.db.dao.VendorPartDao
import com.avago.core.data.db.dao.ReorderSuggestionDao
import com.avago.core.data.db.dao.WoAssignmentDao
import com.avago.core.data.db.dao.WoChecklistItemDao
import com.avago.core.data.db.dao.WoCommentDao
import com.avago.core.data.db.dao.WoTemplateDao
import com.avago.core.data.db.dao.WorkOrderAssetDao
import com.avago.core.data.db.dao.WorkOrderDao
import com.avago.core.data.db.dao.WorkPermitDao
import com.avago.core.data.db.dao.WorkPermitSignatureDao
import com.avago.core.data.db.entity.AccountRolePermissionsEntity
import com.avago.core.data.db.entity.AssetCriticalityEntity
import com.avago.core.data.db.entity.AssetCustomStatusEntity
import com.avago.core.data.db.entity.AssetLocationHistoryEntity
import com.avago.core.data.db.entity.AssetModelEntity
import com.avago.core.data.db.entity.AssetStatusEntity
import com.avago.core.data.db.entity.LabelTemplateEntity
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.BinEntity
import com.avago.core.data.db.entity.CategoryEntity
import com.avago.core.data.db.entity.ConfigEntity
import com.avago.core.data.db.entity.CustomFieldDefinitionEntity
import com.avago.core.data.db.entity.CycleCountEntity
import com.avago.core.data.db.entity.CycleCountLineEntity
import com.avago.core.data.db.entity.DeviceEntity
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.EventEntity
import com.avago.core.data.db.entity.GlAccountEntity
import com.avago.core.data.db.entity.GrnEntity
import com.avago.core.data.db.entity.GrnLineEntity
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.InventoryTransactionEntity
import com.avago.core.data.db.entity.ItemEntity
import com.avago.core.data.db.entity.JobEntity
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.MeterReadingEntity
import com.avago.core.data.db.entity.MeterTriggerEntity
import com.avago.core.data.db.entity.OwnerAssignmentEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.PartIssueEntity
import com.avago.core.data.db.entity.PartIssueLineEntity
import com.avago.core.data.db.entity.PartTransferRequestEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.db.entity.PmPlanEntity
import com.avago.core.data.db.entity.PmPlanIntervalEntity
import com.avago.core.data.db.entity.PoCommentEntity
import com.avago.core.data.db.entity.PoLineEntity
import com.avago.core.data.db.entity.PurchaseOrderEntity
import com.avago.core.data.db.entity.RcaReportEntity
import com.avago.core.data.db.entity.RequestPortalEntity
import com.avago.core.data.db.entity.RolePermissionDefaultsEntity
import com.avago.core.data.db.entity.RoleLabelCacheEntity
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.data.db.entity.ScoutHistoryEntity
import com.avago.core.data.db.entity.ScoutPendingEntity
import com.avago.core.data.db.entity.ServiceEntity
import com.avago.core.data.db.entity.StockingLevelEntity
import com.avago.core.data.db.entity.SyncConflictEntity
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.entity.TechLaborRateEntity
import com.avago.core.data.db.entity.TechProfileEntity
import com.avago.core.data.db.entity.UserEntity
import com.avago.core.data.db.entity.VendorContactEntity
import com.avago.core.data.db.entity.VendorEntity
import com.avago.core.data.db.entity.VendorPartEntity
import com.avago.core.data.db.entity.ReorderSuggestionEntity
import com.avago.core.data.db.entity.WoAssignmentEntity
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.data.db.entity.WoTemplateEntity
import com.avago.core.data.db.entity.WorkOrderAssetEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.data.db.entity.WorkPermitEntity
import com.avago.core.data.db.entity.WorkPermitSignatureEntity

@Database(
    entities = [
        AssetEntity::class,
        LogEntity::class,
        LogCostLineEntity::class,
        PhotoEntity::class,
        ConfigEntity::class,
        ScheduleEntity::class,
        WorkOrderEntity::class,
        WoAssignmentEntity::class,
        WoChecklistItemEntity::class,
        WoCommentEntity::class,
        WoTemplateEntity::class,
        TechProfileEntity::class,
        TechLaborRateEntity::class,
        InventoryEntity::class,
        InventoryTransactionEntity::class,
        PartEntity::class,
        StockingLevelEntity::class,
        DocEntity::class,
        UserEntity::class,
        LocationEntity::class,
        VendorContactEntity::class,
        VendorEntity::class,
        VendorPartEntity::class,
        ReorderSuggestionEntity::class,
        BinEntity::class,
        PurchaseOrderEntity::class,
        PoLineEntity::class,
        GrnEntity::class,
        GrnLineEntity::class,
        CycleCountEntity::class,
        CycleCountLineEntity::class,
        PartIssueEntity::class,
        PartIssueLineEntity::class,
        SyncQueueEntity::class,
        SyncMetadataEntity::class,
        DeviceEntity::class,
        RolePermissionDefaultsEntity::class,
        AccountRolePermissionsEntity::class,
        LabelTemplateEntity::class,
        ItemEntity::class,
        GlAccountEntity::class,
        JobEntity::class,
        ServiceEntity::class,
        AssetLocationHistoryEntity::class,
        RoleLabelCacheEntity::class,
        EventEntity::class,
        ScoutPendingEntity::class,
        ScoutHistoryEntity::class,
        // New entities from changes 10–150
        AssetCustomStatusEntity::class,
        AssetStatusEntity::class,
        MeterReadingEntity::class,
        WorkOrderAssetEntity::class,
        WorkPermitEntity::class,
        WorkPermitSignatureEntity::class,
        MeterTriggerEntity::class,
        PmPlanEntity::class,
        PmPlanIntervalEntity::class,
        CustomFieldDefinitionEntity::class,
        AssetCriticalityEntity::class,
        AssetModelEntity::class,
        RcaReportEntity::class,
        CategoryEntity::class,
        PoCommentEntity::class,
        SyncConflictEntity::class,
        OwnerAssignmentEntity::class,
        PartTransferRequestEntity::class,
        RequestPortalEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AvagoDatabase : RoomDatabase() {

    abstract fun assetDao(): AssetDao
    abstract fun logDao(): LogDao
    abstract fun logCostLineDao(): LogCostLineDao
    abstract fun photoDao(): PhotoDao
    abstract fun configDao(): ConfigDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun workOrderDao(): WorkOrderDao
    abstract fun woAssignmentDao(): WoAssignmentDao
    abstract fun woChecklistItemDao(): WoChecklistItemDao
    abstract fun woCommentDao(): WoCommentDao
    abstract fun woTemplateDao(): WoTemplateDao
    abstract fun techProfileDao(): TechProfileDao
    abstract fun techLaborRateDao(): TechLaborRateDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun partDao(): PartDao
    abstract fun stockingLevelDao(): StockingLevelDao
    abstract fun docDao(): DocDao
    abstract fun userDao(): UserDao
    abstract fun locationDao(): LocationDao
    abstract fun vendorContactDao(): VendorContactDao
    abstract fun vendorDao(): VendorDao
    abstract fun vendorPartDao(): VendorPartDao
    abstract fun reorderSuggestionDao(): ReorderSuggestionDao
    abstract fun binDao(): BinDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun poLineDao(): PoLineDao
    abstract fun grnDao(): GrnDao
    abstract fun grnLineDao(): GrnLineDao
    abstract fun cycleCountDao(): CycleCountDao
    abstract fun cycleCountLineDao(): CycleCountLineDao
    abstract fun partIssueDao(): PartIssueDao
    abstract fun partIssueLineDao(): PartIssueLineDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun deviceDao(): DeviceDao
    abstract fun rolePermissionDefaultsDao(): RolePermissionDefaultsDao
    abstract fun accountRolePermissionsDao(): AccountRolePermissionsDao
    abstract fun labelTemplateDao(): LabelTemplateDao
    abstract fun itemDao(): ItemDao
    abstract fun glAccountDao(): GlAccountDao
    abstract fun jobDao(): JobDao
    abstract fun serviceDao(): ServiceDao
    abstract fun assetLocationHistoryDao(): AssetLocationHistoryDao
    abstract fun roleLabelCacheDao(): RoleLabelCacheDao
    abstract fun eventDao(): EventDao
    abstract fun scoutPendingDao(): ScoutPendingDao
    abstract fun scoutHistoryDao(): ScoutHistoryDao
    // New DAOs
    abstract fun assetCustomStatusDao(): AssetCustomStatusDao
    abstract fun assetStatusDao(): AssetStatusDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun workOrderAssetDao(): WorkOrderAssetDao
    abstract fun workPermitDao(): WorkPermitDao
    abstract fun workPermitSignatureDao(): WorkPermitSignatureDao
    abstract fun meterTriggerDao(): MeterTriggerDao
    abstract fun pmPlanDao(): PmPlanDao
    abstract fun pmPlanIntervalDao(): PmPlanIntervalDao
    abstract fun customFieldDefinitionDao(): CustomFieldDefinitionDao
    abstract fun assetCriticalityDao(): AssetCriticalityDao
    abstract fun assetModelDao(): AssetModelDao
    abstract fun rcaReportDao(): RcaReportDao
    abstract fun categoryDao(): CategoryDao
    abstract fun poCommentDao(): PoCommentDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun ownerAssignmentDao(): OwnerAssignmentDao
    abstract fun partTransferRequestDao(): PartTransferRequestDao
    abstract fun requestPortalDao(): RequestPortalDao
}
