package com.avago.core.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.avago.core.`data`.db.dao.AccountRolePermissionsDao
import com.avago.core.`data`.db.dao.AccountRolePermissionsDao_Impl
import com.avago.core.`data`.db.dao.AssetDao
import com.avago.core.`data`.db.dao.AssetDao_Impl
import com.avago.core.`data`.db.dao.BinDao
import com.avago.core.`data`.db.dao.BinDao_Impl
import com.avago.core.`data`.db.dao.ConfigDao
import com.avago.core.`data`.db.dao.ConfigDao_Impl
import com.avago.core.`data`.db.dao.CycleCountDao
import com.avago.core.`data`.db.dao.CycleCountDao_Impl
import com.avago.core.`data`.db.dao.CycleCountLineDao
import com.avago.core.`data`.db.dao.CycleCountLineDao_Impl
import com.avago.core.`data`.db.dao.DeviceDao
import com.avago.core.`data`.db.dao.DeviceDao_Impl
import com.avago.core.`data`.db.dao.DocDao
import com.avago.core.`data`.db.dao.DocDao_Impl
import com.avago.core.`data`.db.dao.GrnDao
import com.avago.core.`data`.db.dao.GrnDao_Impl
import com.avago.core.`data`.db.dao.GrnLineDao
import com.avago.core.`data`.db.dao.GrnLineDao_Impl
import com.avago.core.`data`.db.dao.InventoryDao
import com.avago.core.`data`.db.dao.InventoryDao_Impl
import com.avago.core.`data`.db.dao.InventoryTransactionDao
import com.avago.core.`data`.db.dao.InventoryTransactionDao_Impl
import com.avago.core.`data`.db.dao.LocationDao
import com.avago.core.`data`.db.dao.LocationDao_Impl
import com.avago.core.`data`.db.dao.LogCostLineDao
import com.avago.core.`data`.db.dao.LogCostLineDao_Impl
import com.avago.core.`data`.db.dao.LogDao
import com.avago.core.`data`.db.dao.LogDao_Impl
import com.avago.core.`data`.db.dao.PartDao
import com.avago.core.`data`.db.dao.PartDao_Impl
import com.avago.core.`data`.db.dao.PartIssueDao
import com.avago.core.`data`.db.dao.PartIssueDao_Impl
import com.avago.core.`data`.db.dao.PartIssueLineDao
import com.avago.core.`data`.db.dao.PartIssueLineDao_Impl
import com.avago.core.`data`.db.dao.PhotoDao
import com.avago.core.`data`.db.dao.PhotoDao_Impl
import com.avago.core.`data`.db.dao.PoLineDao
import com.avago.core.`data`.db.dao.PoLineDao_Impl
import com.avago.core.`data`.db.dao.PurchaseOrderDao
import com.avago.core.`data`.db.dao.PurchaseOrderDao_Impl
import com.avago.core.`data`.db.dao.RolePermissionDefaultsDao
import com.avago.core.`data`.db.dao.RolePermissionDefaultsDao_Impl
import com.avago.core.`data`.db.dao.ScheduleDao
import com.avago.core.`data`.db.dao.ScheduleDao_Impl
import com.avago.core.`data`.db.dao.StockingLevelDao
import com.avago.core.`data`.db.dao.StockingLevelDao_Impl
import com.avago.core.`data`.db.dao.SyncMetadataDao
import com.avago.core.`data`.db.dao.SyncMetadataDao_Impl
import com.avago.core.`data`.db.dao.SyncQueueDao
import com.avago.core.`data`.db.dao.SyncQueueDao_Impl
import com.avago.core.`data`.db.dao.TechLaborRateDao
import com.avago.core.`data`.db.dao.TechLaborRateDao_Impl
import com.avago.core.`data`.db.dao.TechProfileDao
import com.avago.core.`data`.db.dao.TechProfileDao_Impl
import com.avago.core.`data`.db.dao.UserDao
import com.avago.core.`data`.db.dao.UserDao_Impl
import com.avago.core.`data`.db.dao.VendorDao
import com.avago.core.`data`.db.dao.VendorDao_Impl
import com.avago.core.`data`.db.dao.WoAssignmentDao
import com.avago.core.`data`.db.dao.WoAssignmentDao_Impl
import com.avago.core.`data`.db.dao.WoChecklistItemDao
import com.avago.core.`data`.db.dao.WoChecklistItemDao_Impl
import com.avago.core.`data`.db.dao.WoCommentDao
import com.avago.core.`data`.db.dao.WoCommentDao_Impl
import com.avago.core.`data`.db.dao.WoTemplateDao
import com.avago.core.`data`.db.dao.WoTemplateDao_Impl
import com.avago.core.`data`.db.dao.WorkOrderDao
import com.avago.core.`data`.db.dao.WorkOrderDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AvagoDatabase_Impl : AvagoDatabase() {
  private val _assetDao: Lazy<AssetDao> = lazy {
    AssetDao_Impl(this)
  }

  private val _logDao: Lazy<LogDao> = lazy {
    LogDao_Impl(this)
  }

  private val _logCostLineDao: Lazy<LogCostLineDao> = lazy {
    LogCostLineDao_Impl(this)
  }

  private val _photoDao: Lazy<PhotoDao> = lazy {
    PhotoDao_Impl(this)
  }

  private val _configDao: Lazy<ConfigDao> = lazy {
    ConfigDao_Impl(this)
  }

  private val _scheduleDao: Lazy<ScheduleDao> = lazy {
    ScheduleDao_Impl(this)
  }

  private val _workOrderDao: Lazy<WorkOrderDao> = lazy {
    WorkOrderDao_Impl(this)
  }

  private val _woAssignmentDao: Lazy<WoAssignmentDao> = lazy {
    WoAssignmentDao_Impl(this)
  }

  private val _woChecklistItemDao: Lazy<WoChecklistItemDao> = lazy {
    WoChecklistItemDao_Impl(this)
  }

  private val _woCommentDao: Lazy<WoCommentDao> = lazy {
    WoCommentDao_Impl(this)
  }

  private val _woTemplateDao: Lazy<WoTemplateDao> = lazy {
    WoTemplateDao_Impl(this)
  }

  private val _techProfileDao: Lazy<TechProfileDao> = lazy {
    TechProfileDao_Impl(this)
  }

  private val _techLaborRateDao: Lazy<TechLaborRateDao> = lazy {
    TechLaborRateDao_Impl(this)
  }

  private val _inventoryDao: Lazy<InventoryDao> = lazy {
    InventoryDao_Impl(this)
  }

  private val _inventoryTransactionDao: Lazy<InventoryTransactionDao> = lazy {
    InventoryTransactionDao_Impl(this)
  }

  private val _partDao: Lazy<PartDao> = lazy {
    PartDao_Impl(this)
  }

  private val _stockingLevelDao: Lazy<StockingLevelDao> = lazy {
    StockingLevelDao_Impl(this)
  }

  private val _docDao: Lazy<DocDao> = lazy {
    DocDao_Impl(this)
  }

  private val _userDao: Lazy<UserDao> = lazy {
    UserDao_Impl(this)
  }

  private val _locationDao: Lazy<LocationDao> = lazy {
    LocationDao_Impl(this)
  }

  private val _vendorDao: Lazy<VendorDao> = lazy {
    VendorDao_Impl(this)
  }

  private val _binDao: Lazy<BinDao> = lazy {
    BinDao_Impl(this)
  }

  private val _purchaseOrderDao: Lazy<PurchaseOrderDao> = lazy {
    PurchaseOrderDao_Impl(this)
  }

  private val _poLineDao: Lazy<PoLineDao> = lazy {
    PoLineDao_Impl(this)
  }

  private val _grnDao: Lazy<GrnDao> = lazy {
    GrnDao_Impl(this)
  }

  private val _grnLineDao: Lazy<GrnLineDao> = lazy {
    GrnLineDao_Impl(this)
  }

  private val _cycleCountDao: Lazy<CycleCountDao> = lazy {
    CycleCountDao_Impl(this)
  }

  private val _cycleCountLineDao: Lazy<CycleCountLineDao> = lazy {
    CycleCountLineDao_Impl(this)
  }

  private val _partIssueDao: Lazy<PartIssueDao> = lazy {
    PartIssueDao_Impl(this)
  }

  private val _partIssueLineDao: Lazy<PartIssueLineDao> = lazy {
    PartIssueLineDao_Impl(this)
  }

  private val _syncQueueDao: Lazy<SyncQueueDao> = lazy {
    SyncQueueDao_Impl(this)
  }

  private val _syncMetadataDao: Lazy<SyncMetadataDao> = lazy {
    SyncMetadataDao_Impl(this)
  }

  private val _deviceDao: Lazy<DeviceDao> = lazy {
    DeviceDao_Impl(this)
  }

  private val _rolePermissionDefaultsDao: Lazy<RolePermissionDefaultsDao> = lazy {
    RolePermissionDefaultsDao_Impl(this)
  }

  private val _accountRolePermissionsDao: Lazy<AccountRolePermissionsDao> = lazy {
    AccountRolePermissionsDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "191149c2abee6d07c3f846257ded1ef9", "62db2a3cb1bfe53e44e29f31d68932ee") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`asset_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `name` TEXT NOT NULL, `make` TEXT, `model` TEXT, `year` INTEGER, `asset_type` TEXT, `meter_type` TEXT, `avatar_color` TEXT, `avatar_initial` TEXT, `address_line1` TEXT, `address_line2` TEXT, `city` TEXT, `state` TEXT, `postal_code` TEXT, `country` TEXT, `location_id` TEXT, `attributes` TEXT, `is_fre_sample` INTEGER NOT NULL, `parent_asset_id` TEXT, `path` TEXT, `depth` INTEGER NOT NULL, `child_count` INTEGER NOT NULL, `is_rental` INTEGER NOT NULL, `rental_rate` REAL, `rental_rate_unit` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`asset_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `log` (`entry_id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `title` TEXT NOT NULL, `entry_date` INTEGER NOT NULL, `odometer_value` REAL, `category` TEXT, `cost` REAL, `performed_by` TEXT, `performed_by_user_id` TEXT, `notes` TEXT, `data` TEXT, `attributes` TEXT, `cost_mode` TEXT, `cost_items` REAL, `cost_labor` REAL, `cost_tax` REAL, `currency` TEXT, `base_amount` REAL, `exchange_rate_used` REAL, `config_id` TEXT, `config_version` INTEGER, `parent_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`entry_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `log_cost_lines` (`line_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `log_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `display_order` INTEGER NOT NULL, `inventory_id` TEXT, `user_id` TEXT, `description` TEXT, `quantity` REAL NOT NULL, `unit_cost` REAL NOT NULL, `tax_amount` REAL, `gl_code` TEXT, `notes` TEXT, `wo_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`line_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`photo_id` TEXT NOT NULL, `entity_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `account_id` TEXT NOT NULL, `storage_key` TEXT, `download_url` TEXT, `sort_order` INTEGER NOT NULL, `is_primary` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`photo_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `configs` (`config_id` TEXT NOT NULL, `account_id` TEXT, `scope` TEXT NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`config_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `schedules` (`schedule_id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT, `schedule_type` TEXT NOT NULL, `rrule` TEXT, `end_type` TEXT, `end_count` INTEGER, `end_date` INTEGER, `meter_type` TEXT, `meter_due` REAL, `meter_interval` REAL, `last_completed_at` INTEGER, `next_due_at` INTEGER, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`schedule_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `work_orders` (`wo_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `asset_id` TEXT, `location_id` TEXT, `title` TEXT NOT NULL, `description` TEXT, `category` TEXT, `priority` TEXT, `status` TEXT NOT NULL, `requester_id` TEXT, `assigned_to` TEXT, `dispatcher_notes` TEXT, `required_skills` TEXT, `estimated_effort_minutes` INTEGER, `actual_effort_minutes` INTEGER, `failure_code` TEXT, `completion_notes` TEXT, `parts_needed` TEXT, `log_id` TEXT, `due_date` INTEGER, `started_at` INTEGER, `completed_at` INTEGER, `timer_started_at` INTEGER, `labor_cost` REAL, `parts_cost` REAL, `total_cost` REAL, `currency` TEXT, `base_amount` REAL, `exchange_rate_used` REAL, `attributes` TEXT, `created_by` TEXT, `approval_state` TEXT, `job_id` TEXT, `wo_kind` TEXT, `rrule` TEXT, `end_type` TEXT, `end_count` INTEGER, `end_date` INTEGER, `meter_type` TEXT, `meter_due` REAL, `meter_interval` REAL, `parent_wo_id` TEXT, `occurrence_date` TEXT, `schedule_id` TEXT, `last_completed_at` INTEGER, `timezone` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`wo_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `wo_assignments` (`assignment_id` TEXT NOT NULL, `wo_id` TEXT NOT NULL, `technician_id` TEXT NOT NULL, `assigned_at` INTEGER NOT NULL, `unassigned_at` INTEGER, `status` TEXT NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`assignment_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `wo_checklist_items` (`item_id` TEXT NOT NULL, `wo_id` TEXT NOT NULL, `title` TEXT NOT NULL, `is_completed` INTEGER NOT NULL, `completed_at` INTEGER, `display_order` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`item_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `wo_comments` (`comment_id` TEXT NOT NULL, `wo_id` TEXT NOT NULL, `author_id` TEXT NOT NULL, `body` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`comment_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `wo_templates` (`template_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `category` TEXT, `checklist_items` TEXT, `estimated_effort_minutes` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`template_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tech_profiles` (`tech_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `skills` TEXT, `certifications` TEXT, `hourly_rate` REAL, `currency` TEXT, `availability` TEXT, `speed_factor` REAL, `current_location_lat` REAL, `current_location_lng` REAL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`tech_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tech_labor_rates` (`rate_id` TEXT NOT NULL, `tech_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `role_key` TEXT, `hourly_rate` REAL NOT NULL, `currency` TEXT NOT NULL, `effective_date` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`rate_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `inventory` (`inventory_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `part_id` TEXT NOT NULL, `location_id` TEXT, `quantity_on_hand` REAL NOT NULL, `status` TEXT NOT NULL, `last_transaction_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`inventory_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `inventory_transactions` (`transaction_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `inventory_id` TEXT NOT NULL, `part_id` TEXT NOT NULL, `location_id` TEXT, `transaction_type` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit_cost` REAL, `currency` TEXT, `reference_id` TEXT, `reference_type` TEXT, `performed_by` TEXT, `notes` TEXT, `transfer_id` TEXT, `from_location_id` TEXT, `to_location_id` TEXT, `created_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`transaction_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `parts` (`part_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `sku` TEXT, `name` TEXT NOT NULL, `description` TEXT, `category` TEXT, `unit_of_measure` TEXT, `default_vendor_id` TEXT, `cost` REAL, `currency` TEXT, `attributes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`part_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `stocking_levels` (`stocking_level_id` TEXT NOT NULL, `part_id` TEXT NOT NULL, `location_id` TEXT NOT NULL, `min_qty` REAL, `max_qty` REAL, `reorder_qty` REAL, `safety_stock` REAL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`stocking_level_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `docs` (`doc_id` TEXT NOT NULL, `asset_id` TEXT, `account_id` TEXT NOT NULL, `name` TEXT NOT NULL, `doc_type` TEXT NOT NULL, `mime_type` TEXT, `storage_key` TEXT, `download_url` TEXT, `ocr_raw_text` TEXT, `ocr_extracted_json` TEXT, `vendor` TEXT, `total` REAL, `currency` TEXT, `purchase_date` INTEGER, `uploaded_by` TEXT, `uploaded_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`doc_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `users` (`user_id` TEXT NOT NULL, `account_id` TEXT, `display_name` TEXT, `email` TEXT, `photo_url` TEXT, `role` TEXT, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`user_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `locations` (`location_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `city` TEXT, `state` TEXT, `postal_code` TEXT, `country` TEXT, `latitude` REAL, `longitude` REAL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`location_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `vendors` (`vendor_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `name` TEXT NOT NULL, `email` TEXT, `phone` TEXT, `address` TEXT, `payment_terms` TEXT, `tax_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`vendor_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `bins` (`bin_id` TEXT NOT NULL, `location_id` TEXT NOT NULL, `name` TEXT NOT NULL, `aisle` TEXT, `shelf` TEXT, `slot` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`bin_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `purchase_orders` (`po_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `po_number` TEXT, `vendor_id` TEXT, `status` TEXT NOT NULL, `currency` TEXT, `subtotal` REAL, `tax_total` REAL, `shipping_cost` REAL, `discount` REAL, `grand_total` REAL, `base_grand_total` REAL, `exchange_rate_used` REAL, `expected_delivery` TEXT, `ship_to_location_id` TEXT, `work_order_id` TEXT, `asset_id` TEXT, `requested_by` TEXT, `approved_by` TEXT, `approved_at` INTEGER, `ordered_at` INTEGER, `closed_at` INTEGER, `notes` TEXT, `vendor_invoice_no` TEXT, `created_by` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`po_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `po_lines` (`po_line_id` TEXT NOT NULL, `po_id` TEXT NOT NULL, `part_id` TEXT, `description` TEXT, `quantity` REAL NOT NULL, `unit_cost` REAL, `currency` TEXT, `gl_code` TEXT, `received_qty` REAL, `display_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`po_line_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `grns` (`grn_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `po_id` TEXT, `grn_number` TEXT, `received_at` INTEGER, `received_by` TEXT, `received_at_location_id` TEXT, `carrier` TEXT, `tracking_number` TEXT, `packing_slip_no` TEXT, `notes` TEXT, `has_discrepancy` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`grn_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `grn_lines` (`grn_line_id` TEXT NOT NULL, `grn_id` TEXT NOT NULL, `po_line_id` TEXT, `part_id` TEXT, `quantity_received` REAL NOT NULL, `quantity_expected` REAL, `variance_reason` TEXT, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`grn_line_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cycle_counts` (`cycle_count_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `location_id` TEXT NOT NULL, `status` TEXT NOT NULL, `scope_type` TEXT, `scope_value` TEXT, `started_at` INTEGER, `locked_at` INTEGER, `completed_at` INTEGER, `started_by` TEXT, `locked_by` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`cycle_count_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cycle_count_lines` (`line_id` TEXT NOT NULL, `cycle_count_id` TEXT NOT NULL, `inventory_id` TEXT NOT NULL, `part_id` TEXT, `expected_qty` REAL, `counted_qty` REAL, `variance` REAL, `is_counted` INTEGER NOT NULL, `counted_at` INTEGER, `counted_by` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`line_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `part_issues` (`issue_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `location_id` TEXT, `from_location_id` TEXT, `to_location_id` TEXT, `issue_type` TEXT NOT NULL, `issued_at` INTEGER NOT NULL, `issued_by` TEXT, `reference_id` TEXT, `reference_type` TEXT, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `server_version` INTEGER NOT NULL DEFAULT 0, `seq` INTEGER, PRIMARY KEY(`issue_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `part_issue_lines` (`line_id` TEXT NOT NULL, `issue_id` TEXT NOT NULL, `part_id` TEXT NOT NULL, `inventory_id` TEXT, `quantity` REAL NOT NULL, `unit_cost` REAL, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`line_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`queue_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL, `operation` TEXT NOT NULL, `server_version` INTEGER, `payload` TEXT, `sync_status` TEXT NOT NULL DEFAULT 'pending', `attempts` INTEGER NOT NULL DEFAULT 0, `last_error` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`queue_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sync_metadata` (`entity_type` TEXT NOT NULL, `last_server_seq` INTEGER NOT NULL DEFAULT 0, `last_sync_at` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`entity_type`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `devices` (`device_id` TEXT NOT NULL, `account_id` TEXT, `platform` TEXT NOT NULL, `push_token` TEXT, `app_version` TEXT, `os_version` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`device_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `role_permission_defaults` (`role_key` TEXT NOT NULL, `permissions` TEXT NOT NULL, PRIMARY KEY(`role_key`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `account_role_permissions` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `role_key` TEXT NOT NULL, `permissions` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, `server_version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '191149c2abee6d07c3f846257ded1ef9')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `assets`")
        connection.execSQL("DROP TABLE IF EXISTS `log`")
        connection.execSQL("DROP TABLE IF EXISTS `log_cost_lines`")
        connection.execSQL("DROP TABLE IF EXISTS `photos`")
        connection.execSQL("DROP TABLE IF EXISTS `configs`")
        connection.execSQL("DROP TABLE IF EXISTS `schedules`")
        connection.execSQL("DROP TABLE IF EXISTS `work_orders`")
        connection.execSQL("DROP TABLE IF EXISTS `wo_assignments`")
        connection.execSQL("DROP TABLE IF EXISTS `wo_checklist_items`")
        connection.execSQL("DROP TABLE IF EXISTS `wo_comments`")
        connection.execSQL("DROP TABLE IF EXISTS `wo_templates`")
        connection.execSQL("DROP TABLE IF EXISTS `tech_profiles`")
        connection.execSQL("DROP TABLE IF EXISTS `tech_labor_rates`")
        connection.execSQL("DROP TABLE IF EXISTS `inventory`")
        connection.execSQL("DROP TABLE IF EXISTS `inventory_transactions`")
        connection.execSQL("DROP TABLE IF EXISTS `parts`")
        connection.execSQL("DROP TABLE IF EXISTS `stocking_levels`")
        connection.execSQL("DROP TABLE IF EXISTS `docs`")
        connection.execSQL("DROP TABLE IF EXISTS `users`")
        connection.execSQL("DROP TABLE IF EXISTS `locations`")
        connection.execSQL("DROP TABLE IF EXISTS `vendors`")
        connection.execSQL("DROP TABLE IF EXISTS `bins`")
        connection.execSQL("DROP TABLE IF EXISTS `purchase_orders`")
        connection.execSQL("DROP TABLE IF EXISTS `po_lines`")
        connection.execSQL("DROP TABLE IF EXISTS `grns`")
        connection.execSQL("DROP TABLE IF EXISTS `grn_lines`")
        connection.execSQL("DROP TABLE IF EXISTS `cycle_counts`")
        connection.execSQL("DROP TABLE IF EXISTS `cycle_count_lines`")
        connection.execSQL("DROP TABLE IF EXISTS `part_issues`")
        connection.execSQL("DROP TABLE IF EXISTS `part_issue_lines`")
        connection.execSQL("DROP TABLE IF EXISTS `sync_queue`")
        connection.execSQL("DROP TABLE IF EXISTS `sync_metadata`")
        connection.execSQL("DROP TABLE IF EXISTS `devices`")
        connection.execSQL("DROP TABLE IF EXISTS `role_permission_defaults`")
        connection.execSQL("DROP TABLE IF EXISTS `account_role_permissions`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsAssets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAssets.put("asset_id", TableInfo.Column("asset_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("make", TableInfo.Column("make", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("model", TableInfo.Column("model", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("year", TableInfo.Column("year", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("asset_type", TableInfo.Column("asset_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("meter_type", TableInfo.Column("meter_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("avatar_color", TableInfo.Column("avatar_color", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("avatar_initial", TableInfo.Column("avatar_initial", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("address_line1", TableInfo.Column("address_line1", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("address_line2", TableInfo.Column("address_line2", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("city", TableInfo.Column("city", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("state", TableInfo.Column("state", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("postal_code", TableInfo.Column("postal_code", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("country", TableInfo.Column("country", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("location_id", TableInfo.Column("location_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("attributes", TableInfo.Column("attributes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("is_fre_sample", TableInfo.Column("is_fre_sample", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("parent_asset_id", TableInfo.Column("parent_asset_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("path", TableInfo.Column("path", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("depth", TableInfo.Column("depth", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("child_count", TableInfo.Column("child_count", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("is_rental", TableInfo.Column("is_rental", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("rental_rate", TableInfo.Column("rental_rate", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("rental_rate_unit", TableInfo.Column("rental_rate_unit", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsAssets.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAssets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAssets: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAssets: TableInfo = TableInfo("assets", _columnsAssets, _foreignKeysAssets,
            _indicesAssets)
        val _existingAssets: TableInfo = read(connection, "assets")
        if (!_infoAssets.equals(_existingAssets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |assets(com.avago.core.data.db.entity.AssetEntity).
              | Expected:
              |""".trimMargin() + _infoAssets + """
              |
              | Found:
              |""".trimMargin() + _existingAssets)
        }
        val _columnsLog: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLog.put("entry_id", TableInfo.Column("entry_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("asset_id", TableInfo.Column("asset_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("entry_date", TableInfo.Column("entry_date", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("odometer_value", TableInfo.Column("odometer_value", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("category", TableInfo.Column("category", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("cost", TableInfo.Column("cost", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("performed_by", TableInfo.Column("performed_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("performed_by_user_id", TableInfo.Column("performed_by_user_id", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("data", TableInfo.Column("data", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("attributes", TableInfo.Column("attributes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("cost_mode", TableInfo.Column("cost_mode", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("cost_items", TableInfo.Column("cost_items", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("cost_labor", TableInfo.Column("cost_labor", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("cost_tax", TableInfo.Column("cost_tax", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("base_amount", TableInfo.Column("base_amount", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("exchange_rate_used", TableInfo.Column("exchange_rate_used", "REAL", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("config_id", TableInfo.Column("config_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("config_version", TableInfo.Column("config_version", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("parent_id", TableInfo.Column("parent_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsLog.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLog: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLog: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLog: TableInfo = TableInfo("log", _columnsLog, _foreignKeysLog, _indicesLog)
        val _existingLog: TableInfo = read(connection, "log")
        if (!_infoLog.equals(_existingLog)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |log(com.avago.core.data.db.entity.LogEntity).
              | Expected:
              |""".trimMargin() + _infoLog + """
              |
              | Found:
              |""".trimMargin() + _existingLog)
        }
        val _columnsLogCostLines: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLogCostLines.put("line_id", TableInfo.Column("line_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("log_id", TableInfo.Column("log_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("display_order", TableInfo.Column("display_order", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("inventory_id", TableInfo.Column("inventory_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("user_id", TableInfo.Column("user_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("description", TableInfo.Column("description", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("quantity", TableInfo.Column("quantity", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("unit_cost", TableInfo.Column("unit_cost", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("tax_amount", TableInfo.Column("tax_amount", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("gl_code", TableInfo.Column("gl_code", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("wo_id", TableInfo.Column("wo_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsLogCostLines.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLogCostLines: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLogCostLines: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLogCostLines: TableInfo = TableInfo("log_cost_lines", _columnsLogCostLines,
            _foreignKeysLogCostLines, _indicesLogCostLines)
        val _existingLogCostLines: TableInfo = read(connection, "log_cost_lines")
        if (!_infoLogCostLines.equals(_existingLogCostLines)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |log_cost_lines(com.avago.core.data.db.entity.LogCostLineEntity).
              | Expected:
              |""".trimMargin() + _infoLogCostLines + """
              |
              | Found:
              |""".trimMargin() + _existingLogCostLines)
        }
        val _columnsPhotos: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPhotos.put("photo_id", TableInfo.Column("photo_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("entity_id", TableInfo.Column("entity_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("entity_type", TableInfo.Column("entity_type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("storage_key", TableInfo.Column("storage_key", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("download_url", TableInfo.Column("download_url", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("sort_order", TableInfo.Column("sort_order", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("is_primary", TableInfo.Column("is_primary", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPhotos.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPhotos: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPhotos: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPhotos: TableInfo = TableInfo("photos", _columnsPhotos, _foreignKeysPhotos,
            _indicesPhotos)
        val _existingPhotos: TableInfo = read(connection, "photos")
        if (!_infoPhotos.equals(_existingPhotos)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |photos(com.avago.core.data.db.entity.PhotoEntity).
              | Expected:
              |""".trimMargin() + _infoPhotos + """
              |
              | Found:
              |""".trimMargin() + _existingPhotos)
        }
        val _columnsConfigs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsConfigs.put("config_id", TableInfo.Column("config_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("account_id", TableInfo.Column("account_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("scope", TableInfo.Column("scope", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("key", TableInfo.Column("key", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("value", TableInfo.Column("value", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("version", TableInfo.Column("version", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConfigs.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysConfigs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesConfigs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoConfigs: TableInfo = TableInfo("configs", _columnsConfigs, _foreignKeysConfigs,
            _indicesConfigs)
        val _existingConfigs: TableInfo = read(connection, "configs")
        if (!_infoConfigs.equals(_existingConfigs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |configs(com.avago.core.data.db.entity.ConfigEntity).
              | Expected:
              |""".trimMargin() + _infoConfigs + """
              |
              | Found:
              |""".trimMargin() + _existingConfigs)
        }
        val _columnsSchedules: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSchedules.put("schedule_id", TableInfo.Column("schedule_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("asset_id", TableInfo.Column("asset_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("category", TableInfo.Column("category", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("schedule_type", TableInfo.Column("schedule_type", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("rrule", TableInfo.Column("rrule", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("end_type", TableInfo.Column("end_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("end_count", TableInfo.Column("end_count", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("end_date", TableInfo.Column("end_date", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("meter_type", TableInfo.Column("meter_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("meter_due", TableInfo.Column("meter_due", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("meter_interval", TableInfo.Column("meter_interval", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("last_completed_at", TableInfo.Column("last_completed_at", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("next_due_at", TableInfo.Column("next_due_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("is_active", TableInfo.Column("is_active", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsSchedules.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSchedules: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSchedules: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSchedules: TableInfo = TableInfo("schedules", _columnsSchedules,
            _foreignKeysSchedules, _indicesSchedules)
        val _existingSchedules: TableInfo = read(connection, "schedules")
        if (!_infoSchedules.equals(_existingSchedules)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |schedules(com.avago.core.data.db.entity.ScheduleEntity).
              | Expected:
              |""".trimMargin() + _infoSchedules + """
              |
              | Found:
              |""".trimMargin() + _existingSchedules)
        }
        val _columnsWorkOrders: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWorkOrders.put("wo_id", TableInfo.Column("wo_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("asset_id", TableInfo.Column("asset_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("location_id", TableInfo.Column("location_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("description", TableInfo.Column("description", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("category", TableInfo.Column("category", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("priority", TableInfo.Column("priority", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("requester_id", TableInfo.Column("requester_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("assigned_to", TableInfo.Column("assigned_to", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("dispatcher_notes", TableInfo.Column("dispatcher_notes", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("required_skills", TableInfo.Column("required_skills", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("estimated_effort_minutes",
            TableInfo.Column("estimated_effort_minutes", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("actual_effort_minutes", TableInfo.Column("actual_effort_minutes",
            "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("failure_code", TableInfo.Column("failure_code", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("completion_notes", TableInfo.Column("completion_notes", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("parts_needed", TableInfo.Column("parts_needed", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("log_id", TableInfo.Column("log_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("due_date", TableInfo.Column("due_date", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("started_at", TableInfo.Column("started_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("completed_at", TableInfo.Column("completed_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("timer_started_at", TableInfo.Column("timer_started_at", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("labor_cost", TableInfo.Column("labor_cost", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("parts_cost", TableInfo.Column("parts_cost", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("total_cost", TableInfo.Column("total_cost", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("base_amount", TableInfo.Column("base_amount", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("exchange_rate_used", TableInfo.Column("exchange_rate_used", "REAL",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("attributes", TableInfo.Column("attributes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("created_by", TableInfo.Column("created_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("approval_state", TableInfo.Column("approval_state", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("job_id", TableInfo.Column("job_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("wo_kind", TableInfo.Column("wo_kind", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("rrule", TableInfo.Column("rrule", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("end_type", TableInfo.Column("end_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("end_count", TableInfo.Column("end_count", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("end_date", TableInfo.Column("end_date", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("meter_type", TableInfo.Column("meter_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("meter_due", TableInfo.Column("meter_due", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("meter_interval", TableInfo.Column("meter_interval", "REAL", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("parent_wo_id", TableInfo.Column("parent_wo_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("occurrence_date", TableInfo.Column("occurrence_date", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("schedule_id", TableInfo.Column("schedule_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("last_completed_at", TableInfo.Column("last_completed_at", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("timezone", TableInfo.Column("timezone", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkOrders.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWorkOrders: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWorkOrders: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoWorkOrders: TableInfo = TableInfo("work_orders", _columnsWorkOrders,
            _foreignKeysWorkOrders, _indicesWorkOrders)
        val _existingWorkOrders: TableInfo = read(connection, "work_orders")
        if (!_infoWorkOrders.equals(_existingWorkOrders)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |work_orders(com.avago.core.data.db.entity.WorkOrderEntity).
              | Expected:
              |""".trimMargin() + _infoWorkOrders + """
              |
              | Found:
              |""".trimMargin() + _existingWorkOrders)
        }
        val _columnsWoAssignments: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWoAssignments.put("assignment_id", TableInfo.Column("assignment_id", "TEXT", true,
            1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("wo_id", TableInfo.Column("wo_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("technician_id", TableInfo.Column("technician_id", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("assigned_at", TableInfo.Column("assigned_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("unassigned_at", TableInfo.Column("unassigned_at", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsWoAssignments.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWoAssignments: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWoAssignments: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoWoAssignments: TableInfo = TableInfo("wo_assignments", _columnsWoAssignments,
            _foreignKeysWoAssignments, _indicesWoAssignments)
        val _existingWoAssignments: TableInfo = read(connection, "wo_assignments")
        if (!_infoWoAssignments.equals(_existingWoAssignments)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |wo_assignments(com.avago.core.data.db.entity.WoAssignmentEntity).
              | Expected:
              |""".trimMargin() + _infoWoAssignments + """
              |
              | Found:
              |""".trimMargin() + _existingWoAssignments)
        }
        val _columnsWoChecklistItems: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWoChecklistItems.put("item_id", TableInfo.Column("item_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("wo_id", TableInfo.Column("wo_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("is_completed", TableInfo.Column("is_completed", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("completed_at", TableInfo.Column("completed_at", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("display_order", TableInfo.Column("display_order", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsWoChecklistItems.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWoChecklistItems: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWoChecklistItems: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoWoChecklistItems: TableInfo = TableInfo("wo_checklist_items",
            _columnsWoChecklistItems, _foreignKeysWoChecklistItems, _indicesWoChecklistItems)
        val _existingWoChecklistItems: TableInfo = read(connection, "wo_checklist_items")
        if (!_infoWoChecklistItems.equals(_existingWoChecklistItems)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |wo_checklist_items(com.avago.core.data.db.entity.WoChecklistItemEntity).
              | Expected:
              |""".trimMargin() + _infoWoChecklistItems + """
              |
              | Found:
              |""".trimMargin() + _existingWoChecklistItems)
        }
        val _columnsWoComments: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWoComments.put("comment_id", TableInfo.Column("comment_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("wo_id", TableInfo.Column("wo_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("author_id", TableInfo.Column("author_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("body", TableInfo.Column("body", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsWoComments.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWoComments: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWoComments: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoWoComments: TableInfo = TableInfo("wo_comments", _columnsWoComments,
            _foreignKeysWoComments, _indicesWoComments)
        val _existingWoComments: TableInfo = read(connection, "wo_comments")
        if (!_infoWoComments.equals(_existingWoComments)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |wo_comments(com.avago.core.data.db.entity.WoCommentEntity).
              | Expected:
              |""".trimMargin() + _infoWoComments + """
              |
              | Found:
              |""".trimMargin() + _existingWoComments)
        }
        val _columnsWoTemplates: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWoTemplates.put("template_id", TableInfo.Column("template_id", "TEXT", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("description", TableInfo.Column("description", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("category", TableInfo.Column("category", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("checklist_items", TableInfo.Column("checklist_items", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("estimated_effort_minutes",
            TableInfo.Column("estimated_effort_minutes", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWoTemplates.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWoTemplates: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWoTemplates: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoWoTemplates: TableInfo = TableInfo("wo_templates", _columnsWoTemplates,
            _foreignKeysWoTemplates, _indicesWoTemplates)
        val _existingWoTemplates: TableInfo = read(connection, "wo_templates")
        if (!_infoWoTemplates.equals(_existingWoTemplates)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |wo_templates(com.avago.core.data.db.entity.WoTemplateEntity).
              | Expected:
              |""".trimMargin() + _infoWoTemplates + """
              |
              | Found:
              |""".trimMargin() + _existingWoTemplates)
        }
        val _columnsTechProfiles: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTechProfiles.put("tech_id", TableInfo.Column("tech_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("user_id", TableInfo.Column("user_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("skills", TableInfo.Column("skills", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("certifications", TableInfo.Column("certifications", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("hourly_rate", TableInfo.Column("hourly_rate", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("availability", TableInfo.Column("availability", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("speed_factor", TableInfo.Column("speed_factor", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("current_location_lat", TableInfo.Column("current_location_lat",
            "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("current_location_lng", TableInfo.Column("current_location_lng",
            "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsTechProfiles.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTechProfiles: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTechProfiles: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTechProfiles: TableInfo = TableInfo("tech_profiles", _columnsTechProfiles,
            _foreignKeysTechProfiles, _indicesTechProfiles)
        val _existingTechProfiles: TableInfo = read(connection, "tech_profiles")
        if (!_infoTechProfiles.equals(_existingTechProfiles)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tech_profiles(com.avago.core.data.db.entity.TechProfileEntity).
              | Expected:
              |""".trimMargin() + _infoTechProfiles + """
              |
              | Found:
              |""".trimMargin() + _existingTechProfiles)
        }
        val _columnsTechLaborRates: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTechLaborRates.put("rate_id", TableInfo.Column("rate_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("tech_id", TableInfo.Column("tech_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("role_key", TableInfo.Column("role_key", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("hourly_rate", TableInfo.Column("hourly_rate", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("currency", TableInfo.Column("currency", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("effective_date", TableInfo.Column("effective_date", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTechLaborRates.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTechLaborRates: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTechLaborRates: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTechLaborRates: TableInfo = TableInfo("tech_labor_rates", _columnsTechLaborRates,
            _foreignKeysTechLaborRates, _indicesTechLaborRates)
        val _existingTechLaborRates: TableInfo = read(connection, "tech_labor_rates")
        if (!_infoTechLaborRates.equals(_existingTechLaborRates)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tech_labor_rates(com.avago.core.data.db.entity.TechLaborRateEntity).
              | Expected:
              |""".trimMargin() + _infoTechLaborRates + """
              |
              | Found:
              |""".trimMargin() + _existingTechLaborRates)
        }
        val _columnsInventory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsInventory.put("inventory_id", TableInfo.Column("inventory_id", "TEXT", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("part_id", TableInfo.Column("part_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("location_id", TableInfo.Column("location_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("quantity_on_hand", TableInfo.Column("quantity_on_hand", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("last_transaction_id", TableInfo.Column("last_transaction_id", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsInventory.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysInventory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesInventory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoInventory: TableInfo = TableInfo("inventory", _columnsInventory,
            _foreignKeysInventory, _indicesInventory)
        val _existingInventory: TableInfo = read(connection, "inventory")
        if (!_infoInventory.equals(_existingInventory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |inventory(com.avago.core.data.db.entity.InventoryEntity).
              | Expected:
              |""".trimMargin() + _infoInventory + """
              |
              | Found:
              |""".trimMargin() + _existingInventory)
        }
        val _columnsInventoryTransactions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsInventoryTransactions.put("transaction_id", TableInfo.Column("transaction_id",
            "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("account_id", TableInfo.Column("account_id", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("inventory_id", TableInfo.Column("inventory_id", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("part_id", TableInfo.Column("part_id", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("location_id", TableInfo.Column("location_id", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("transaction_type", TableInfo.Column("transaction_type",
            "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("quantity", TableInfo.Column("quantity", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("unit_cost", TableInfo.Column("unit_cost", "REAL", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("currency", TableInfo.Column("currency", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("reference_id", TableInfo.Column("reference_id", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("reference_type", TableInfo.Column("reference_type",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("performed_by", TableInfo.Column("performed_by", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("transfer_id", TableInfo.Column("transfer_id", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("from_location_id", TableInfo.Column("from_location_id",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("to_location_id", TableInfo.Column("to_location_id",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("created_at", TableInfo.Column("created_at", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("server_version", TableInfo.Column("server_version",
            "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsInventoryTransactions.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysInventoryTransactions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesInventoryTransactions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoInventoryTransactions: TableInfo = TableInfo("inventory_transactions",
            _columnsInventoryTransactions, _foreignKeysInventoryTransactions,
            _indicesInventoryTransactions)
        val _existingInventoryTransactions: TableInfo = read(connection, "inventory_transactions")
        if (!_infoInventoryTransactions.equals(_existingInventoryTransactions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |inventory_transactions(com.avago.core.data.db.entity.InventoryTransactionEntity).
              | Expected:
              |""".trimMargin() + _infoInventoryTransactions + """
              |
              | Found:
              |""".trimMargin() + _existingInventoryTransactions)
        }
        val _columnsParts: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsParts.put("part_id", TableInfo.Column("part_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("sku", TableInfo.Column("sku", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("description", TableInfo.Column("description", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("category", TableInfo.Column("category", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("unit_of_measure", TableInfo.Column("unit_of_measure", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("default_vendor_id", TableInfo.Column("default_vendor_id", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("cost", TableInfo.Column("cost", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("attributes", TableInfo.Column("attributes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsParts.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysParts: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesParts: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoParts: TableInfo = TableInfo("parts", _columnsParts, _foreignKeysParts,
            _indicesParts)
        val _existingParts: TableInfo = read(connection, "parts")
        if (!_infoParts.equals(_existingParts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |parts(com.avago.core.data.db.entity.PartEntity).
              | Expected:
              |""".trimMargin() + _infoParts + """
              |
              | Found:
              |""".trimMargin() + _existingParts)
        }
        val _columnsStockingLevels: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStockingLevels.put("stocking_level_id", TableInfo.Column("stocking_level_id",
            "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("part_id", TableInfo.Column("part_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("location_id", TableInfo.Column("location_id", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("min_qty", TableInfo.Column("min_qty", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("max_qty", TableInfo.Column("max_qty", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("reorder_qty", TableInfo.Column("reorder_qty", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("safety_stock", TableInfo.Column("safety_stock", "REAL", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStockingLevels.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStockingLevels: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesStockingLevels: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoStockingLevels: TableInfo = TableInfo("stocking_levels", _columnsStockingLevels,
            _foreignKeysStockingLevels, _indicesStockingLevels)
        val _existingStockingLevels: TableInfo = read(connection, "stocking_levels")
        if (!_infoStockingLevels.equals(_existingStockingLevels)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |stocking_levels(com.avago.core.data.db.entity.StockingLevelEntity).
              | Expected:
              |""".trimMargin() + _infoStockingLevels + """
              |
              | Found:
              |""".trimMargin() + _existingStockingLevels)
        }
        val _columnsDocs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDocs.put("doc_id", TableInfo.Column("doc_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("asset_id", TableInfo.Column("asset_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("doc_type", TableInfo.Column("doc_type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("mime_type", TableInfo.Column("mime_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("storage_key", TableInfo.Column("storage_key", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("download_url", TableInfo.Column("download_url", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("ocr_raw_text", TableInfo.Column("ocr_raw_text", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("ocr_extracted_json", TableInfo.Column("ocr_extracted_json", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("vendor", TableInfo.Column("vendor", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("total", TableInfo.Column("total", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("purchase_date", TableInfo.Column("purchase_date", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("uploaded_by", TableInfo.Column("uploaded_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("uploaded_at", TableInfo.Column("uploaded_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsDocs.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDocs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDocs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDocs: TableInfo = TableInfo("docs", _columnsDocs, _foreignKeysDocs, _indicesDocs)
        val _existingDocs: TableInfo = read(connection, "docs")
        if (!_infoDocs.equals(_existingDocs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |docs(com.avago.core.data.db.entity.DocEntity).
              | Expected:
              |""".trimMargin() + _infoDocs + """
              |
              | Found:
              |""".trimMargin() + _existingDocs)
        }
        val _columnsUsers: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsUsers.put("user_id", TableInfo.Column("user_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("account_id", TableInfo.Column("account_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("display_name", TableInfo.Column("display_name", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("email", TableInfo.Column("email", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("photo_url", TableInfo.Column("photo_url", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("role", TableInfo.Column("role", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("is_active", TableInfo.Column("is_active", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysUsers: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesUsers: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoUsers: TableInfo = TableInfo("users", _columnsUsers, _foreignKeysUsers,
            _indicesUsers)
        val _existingUsers: TableInfo = read(connection, "users")
        if (!_infoUsers.equals(_existingUsers)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |users(com.avago.core.data.db.entity.UserEntity).
              | Expected:
              |""".trimMargin() + _infoUsers + """
              |
              | Found:
              |""".trimMargin() + _existingUsers)
        }
        val _columnsLocations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLocations.put("location_id", TableInfo.Column("location_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("address", TableInfo.Column("address", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("city", TableInfo.Column("city", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("state", TableInfo.Column("state", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("postal_code", TableInfo.Column("postal_code", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("country", TableInfo.Column("country", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("latitude", TableInfo.Column("latitude", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("longitude", TableInfo.Column("longitude", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsLocations.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLocations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLocations: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLocations: TableInfo = TableInfo("locations", _columnsLocations,
            _foreignKeysLocations, _indicesLocations)
        val _existingLocations: TableInfo = read(connection, "locations")
        if (!_infoLocations.equals(_existingLocations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |locations(com.avago.core.data.db.entity.LocationEntity).
              | Expected:
              |""".trimMargin() + _infoLocations + """
              |
              | Found:
              |""".trimMargin() + _existingLocations)
        }
        val _columnsVendors: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsVendors.put("vendor_id", TableInfo.Column("vendor_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("email", TableInfo.Column("email", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("phone", TableInfo.Column("phone", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("address", TableInfo.Column("address", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("payment_terms", TableInfo.Column("payment_terms", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("tax_id", TableInfo.Column("tax_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsVendors.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysVendors: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesVendors: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoVendors: TableInfo = TableInfo("vendors", _columnsVendors, _foreignKeysVendors,
            _indicesVendors)
        val _existingVendors: TableInfo = read(connection, "vendors")
        if (!_infoVendors.equals(_existingVendors)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |vendors(com.avago.core.data.db.entity.VendorEntity).
              | Expected:
              |""".trimMargin() + _infoVendors + """
              |
              | Found:
              |""".trimMargin() + _existingVendors)
        }
        val _columnsBins: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBins.put("bin_id", TableInfo.Column("bin_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("location_id", TableInfo.Column("location_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("aisle", TableInfo.Column("aisle", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("shelf", TableInfo.Column("shelf", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("slot", TableInfo.Column("slot", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBins.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBins: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBins: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBins: TableInfo = TableInfo("bins", _columnsBins, _foreignKeysBins, _indicesBins)
        val _existingBins: TableInfo = read(connection, "bins")
        if (!_infoBins.equals(_existingBins)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |bins(com.avago.core.data.db.entity.BinEntity).
              | Expected:
              |""".trimMargin() + _infoBins + """
              |
              | Found:
              |""".trimMargin() + _existingBins)
        }
        val _columnsPurchaseOrders: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPurchaseOrders.put("po_id", TableInfo.Column("po_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("po_number", TableInfo.Column("po_number", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("vendor_id", TableInfo.Column("vendor_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("subtotal", TableInfo.Column("subtotal", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("tax_total", TableInfo.Column("tax_total", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("shipping_cost", TableInfo.Column("shipping_cost", "REAL", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("discount", TableInfo.Column("discount", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("grand_total", TableInfo.Column("grand_total", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("base_grand_total", TableInfo.Column("base_grand_total", "REAL",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("exchange_rate_used", TableInfo.Column("exchange_rate_used",
            "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("expected_delivery", TableInfo.Column("expected_delivery",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("ship_to_location_id", TableInfo.Column("ship_to_location_id",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("work_order_id", TableInfo.Column("work_order_id", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("asset_id", TableInfo.Column("asset_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("requested_by", TableInfo.Column("requested_by", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("approved_by", TableInfo.Column("approved_by", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("approved_at", TableInfo.Column("approved_at", "INTEGER", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("ordered_at", TableInfo.Column("ordered_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("closed_at", TableInfo.Column("closed_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("vendor_invoice_no", TableInfo.Column("vendor_invoice_no",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("created_by", TableInfo.Column("created_by", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsPurchaseOrders.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPurchaseOrders: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPurchaseOrders: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPurchaseOrders: TableInfo = TableInfo("purchase_orders", _columnsPurchaseOrders,
            _foreignKeysPurchaseOrders, _indicesPurchaseOrders)
        val _existingPurchaseOrders: TableInfo = read(connection, "purchase_orders")
        if (!_infoPurchaseOrders.equals(_existingPurchaseOrders)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |purchase_orders(com.avago.core.data.db.entity.PurchaseOrderEntity).
              | Expected:
              |""".trimMargin() + _infoPurchaseOrders + """
              |
              | Found:
              |""".trimMargin() + _existingPurchaseOrders)
        }
        val _columnsPoLines: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPoLines.put("po_line_id", TableInfo.Column("po_line_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("po_id", TableInfo.Column("po_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("part_id", TableInfo.Column("part_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("description", TableInfo.Column("description", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("quantity", TableInfo.Column("quantity", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("unit_cost", TableInfo.Column("unit_cost", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("currency", TableInfo.Column("currency", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("gl_code", TableInfo.Column("gl_code", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("received_qty", TableInfo.Column("received_qty", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("display_order", TableInfo.Column("display_order", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPoLines.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPoLines: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPoLines: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPoLines: TableInfo = TableInfo("po_lines", _columnsPoLines, _foreignKeysPoLines,
            _indicesPoLines)
        val _existingPoLines: TableInfo = read(connection, "po_lines")
        if (!_infoPoLines.equals(_existingPoLines)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |po_lines(com.avago.core.data.db.entity.PoLineEntity).
              | Expected:
              |""".trimMargin() + _infoPoLines + """
              |
              | Found:
              |""".trimMargin() + _existingPoLines)
        }
        val _columnsGrns: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsGrns.put("grn_id", TableInfo.Column("grn_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("po_id", TableInfo.Column("po_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("grn_number", TableInfo.Column("grn_number", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("received_at", TableInfo.Column("received_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("received_by", TableInfo.Column("received_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("received_at_location_id", TableInfo.Column("received_at_location_id",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("carrier", TableInfo.Column("carrier", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("tracking_number", TableInfo.Column("tracking_number", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("packing_slip_no", TableInfo.Column("packing_slip_no", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("has_discrepancy", TableInfo.Column("has_discrepancy", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("server_version", TableInfo.Column("server_version", "INTEGER", true, 0,
            "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsGrns.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysGrns: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesGrns: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoGrns: TableInfo = TableInfo("grns", _columnsGrns, _foreignKeysGrns, _indicesGrns)
        val _existingGrns: TableInfo = read(connection, "grns")
        if (!_infoGrns.equals(_existingGrns)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |grns(com.avago.core.data.db.entity.GrnEntity).
              | Expected:
              |""".trimMargin() + _infoGrns + """
              |
              | Found:
              |""".trimMargin() + _existingGrns)
        }
        val _columnsGrnLines: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsGrnLines.put("grn_line_id", TableInfo.Column("grn_line_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("grn_id", TableInfo.Column("grn_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("po_line_id", TableInfo.Column("po_line_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("part_id", TableInfo.Column("part_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("quantity_received", TableInfo.Column("quantity_received", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("quantity_expected", TableInfo.Column("quantity_expected", "REAL",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("variance_reason", TableInfo.Column("variance_reason", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGrnLines.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysGrnLines: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesGrnLines: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoGrnLines: TableInfo = TableInfo("grn_lines", _columnsGrnLines,
            _foreignKeysGrnLines, _indicesGrnLines)
        val _existingGrnLines: TableInfo = read(connection, "grn_lines")
        if (!_infoGrnLines.equals(_existingGrnLines)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |grn_lines(com.avago.core.data.db.entity.GrnLineEntity).
              | Expected:
              |""".trimMargin() + _infoGrnLines + """
              |
              | Found:
              |""".trimMargin() + _existingGrnLines)
        }
        val _columnsCycleCounts: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCycleCounts.put("cycle_count_id", TableInfo.Column("cycle_count_id", "TEXT", true,
            1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("location_id", TableInfo.Column("location_id", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("scope_type", TableInfo.Column("scope_type", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("scope_value", TableInfo.Column("scope_value", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("started_at", TableInfo.Column("started_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("locked_at", TableInfo.Column("locked_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("completed_at", TableInfo.Column("completed_at", "INTEGER", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("started_by", TableInfo.Column("started_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("locked_by", TableInfo.Column("locked_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCounts.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCycleCounts: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCycleCounts: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCycleCounts: TableInfo = TableInfo("cycle_counts", _columnsCycleCounts,
            _foreignKeysCycleCounts, _indicesCycleCounts)
        val _existingCycleCounts: TableInfo = read(connection, "cycle_counts")
        if (!_infoCycleCounts.equals(_existingCycleCounts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |cycle_counts(com.avago.core.data.db.entity.CycleCountEntity).
              | Expected:
              |""".trimMargin() + _infoCycleCounts + """
              |
              | Found:
              |""".trimMargin() + _existingCycleCounts)
        }
        val _columnsCycleCountLines: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCycleCountLines.put("line_id", TableInfo.Column("line_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("cycle_count_id", TableInfo.Column("cycle_count_id", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("inventory_id", TableInfo.Column("inventory_id", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("part_id", TableInfo.Column("part_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("expected_qty", TableInfo.Column("expected_qty", "REAL", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("counted_qty", TableInfo.Column("counted_qty", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("variance", TableInfo.Column("variance", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("is_counted", TableInfo.Column("is_counted", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("counted_at", TableInfo.Column("counted_at", "INTEGER", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("counted_by", TableInfo.Column("counted_by", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCycleCountLines.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCycleCountLines: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCycleCountLines: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCycleCountLines: TableInfo = TableInfo("cycle_count_lines",
            _columnsCycleCountLines, _foreignKeysCycleCountLines, _indicesCycleCountLines)
        val _existingCycleCountLines: TableInfo = read(connection, "cycle_count_lines")
        if (!_infoCycleCountLines.equals(_existingCycleCountLines)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |cycle_count_lines(com.avago.core.data.db.entity.CycleCountLineEntity).
              | Expected:
              |""".trimMargin() + _infoCycleCountLines + """
              |
              | Found:
              |""".trimMargin() + _existingCycleCountLines)
        }
        val _columnsPartIssues: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPartIssues.put("issue_id", TableInfo.Column("issue_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("location_id", TableInfo.Column("location_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("from_location_id", TableInfo.Column("from_location_id", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("to_location_id", TableInfo.Column("to_location_id", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("issue_type", TableInfo.Column("issue_type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("issued_at", TableInfo.Column("issued_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("issued_by", TableInfo.Column("issued_by", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("reference_id", TableInfo.Column("reference_id", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("reference_type", TableInfo.Column("reference_type", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("server_version", TableInfo.Column("server_version", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssues.put("seq", TableInfo.Column("seq", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPartIssues: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPartIssues: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPartIssues: TableInfo = TableInfo("part_issues", _columnsPartIssues,
            _foreignKeysPartIssues, _indicesPartIssues)
        val _existingPartIssues: TableInfo = read(connection, "part_issues")
        if (!_infoPartIssues.equals(_existingPartIssues)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |part_issues(com.avago.core.data.db.entity.PartIssueEntity).
              | Expected:
              |""".trimMargin() + _infoPartIssues + """
              |
              | Found:
              |""".trimMargin() + _existingPartIssues)
        }
        val _columnsPartIssueLines: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPartIssueLines.put("line_id", TableInfo.Column("line_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("issue_id", TableInfo.Column("issue_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("part_id", TableInfo.Column("part_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("inventory_id", TableInfo.Column("inventory_id", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("quantity", TableInfo.Column("quantity", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("unit_cost", TableInfo.Column("unit_cost", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPartIssueLines.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPartIssueLines: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPartIssueLines: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPartIssueLines: TableInfo = TableInfo("part_issue_lines", _columnsPartIssueLines,
            _foreignKeysPartIssueLines, _indicesPartIssueLines)
        val _existingPartIssueLines: TableInfo = read(connection, "part_issue_lines")
        if (!_infoPartIssueLines.equals(_existingPartIssueLines)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |part_issue_lines(com.avago.core.data.db.entity.PartIssueLineEntity).
              | Expected:
              |""".trimMargin() + _infoPartIssueLines + """
              |
              | Found:
              |""".trimMargin() + _existingPartIssueLines)
        }
        val _columnsSyncQueue: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSyncQueue.put("queue_id", TableInfo.Column("queue_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("entity_type", TableInfo.Column("entity_type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("entity_id", TableInfo.Column("entity_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("operation", TableInfo.Column("operation", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("server_version", TableInfo.Column("server_version", "INTEGER", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("payload", TableInfo.Column("payload", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("sync_status", TableInfo.Column("sync_status", "TEXT", true, 0,
            "'pending'", TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("attempts", TableInfo.Column("attempts", "INTEGER", true, 0, "0",
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("last_error", TableInfo.Column("last_error", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncQueue.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSyncQueue: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSyncQueue: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSyncQueue: TableInfo = TableInfo("sync_queue", _columnsSyncQueue,
            _foreignKeysSyncQueue, _indicesSyncQueue)
        val _existingSyncQueue: TableInfo = read(connection, "sync_queue")
        if (!_infoSyncQueue.equals(_existingSyncQueue)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |sync_queue(com.avago.core.data.db.entity.SyncQueueEntity).
              | Expected:
              |""".trimMargin() + _infoSyncQueue + """
              |
              | Found:
              |""".trimMargin() + _existingSyncQueue)
        }
        val _columnsSyncMetadata: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSyncMetadata.put("entity_type", TableInfo.Column("entity_type", "TEXT", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncMetadata.put("last_server_seq", TableInfo.Column("last_server_seq", "INTEGER",
            true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        _columnsSyncMetadata.put("last_sync_at", TableInfo.Column("last_sync_at", "INTEGER", true,
            0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSyncMetadata: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSyncMetadata: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSyncMetadata: TableInfo = TableInfo("sync_metadata", _columnsSyncMetadata,
            _foreignKeysSyncMetadata, _indicesSyncMetadata)
        val _existingSyncMetadata: TableInfo = read(connection, "sync_metadata")
        if (!_infoSyncMetadata.equals(_existingSyncMetadata)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |sync_metadata(com.avago.core.data.db.entity.SyncMetadataEntity).
              | Expected:
              |""".trimMargin() + _infoSyncMetadata + """
              |
              | Found:
              |""".trimMargin() + _existingSyncMetadata)
        }
        val _columnsDevices: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDevices.put("device_id", TableInfo.Column("device_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("account_id", TableInfo.Column("account_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("platform", TableInfo.Column("platform", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("push_token", TableInfo.Column("push_token", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("app_version", TableInfo.Column("app_version", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("os_version", TableInfo.Column("os_version", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsDevices.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDevices: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDevices: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDevices: TableInfo = TableInfo("devices", _columnsDevices, _foreignKeysDevices,
            _indicesDevices)
        val _existingDevices: TableInfo = read(connection, "devices")
        if (!_infoDevices.equals(_existingDevices)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |devices(com.avago.core.data.db.entity.DeviceEntity).
              | Expected:
              |""".trimMargin() + _infoDevices + """
              |
              | Found:
              |""".trimMargin() + _existingDevices)
        }
        val _columnsRolePermissionDefaults: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsRolePermissionDefaults.put("role_key", TableInfo.Column("role_key", "TEXT", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRolePermissionDefaults.put("permissions", TableInfo.Column("permissions", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysRolePermissionDefaults: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesRolePermissionDefaults: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoRolePermissionDefaults: TableInfo = TableInfo("role_permission_defaults",
            _columnsRolePermissionDefaults, _foreignKeysRolePermissionDefaults,
            _indicesRolePermissionDefaults)
        val _existingRolePermissionDefaults: TableInfo = read(connection,
            "role_permission_defaults")
        if (!_infoRolePermissionDefaults.equals(_existingRolePermissionDefaults)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |role_permission_defaults(com.avago.core.data.db.entity.RolePermissionDefaultsEntity).
              | Expected:
              |""".trimMargin() + _infoRolePermissionDefaults + """
              |
              | Found:
              |""".trimMargin() + _existingRolePermissionDefaults)
        }
        val _columnsAccountRolePermissions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAccountRolePermissions.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsAccountRolePermissions.put("account_id", TableInfo.Column("account_id", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccountRolePermissions.put("role_key", TableInfo.Column("role_key", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccountRolePermissions.put("permissions", TableInfo.Column("permissions", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccountRolePermissions.put("updated_at", TableInfo.Column("updated_at", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccountRolePermissions.put("server_version", TableInfo.Column("server_version",
            "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAccountRolePermissions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAccountRolePermissions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAccountRolePermissions: TableInfo = TableInfo("account_role_permissions",
            _columnsAccountRolePermissions, _foreignKeysAccountRolePermissions,
            _indicesAccountRolePermissions)
        val _existingAccountRolePermissions: TableInfo = read(connection,
            "account_role_permissions")
        if (!_infoAccountRolePermissions.equals(_existingAccountRolePermissions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |account_role_permissions(com.avago.core.data.db.entity.AccountRolePermissionsEntity).
              | Expected:
              |""".trimMargin() + _infoAccountRolePermissions + """
              |
              | Found:
              |""".trimMargin() + _existingAccountRolePermissions)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "assets", "log",
        "log_cost_lines", "photos", "configs", "schedules", "work_orders", "wo_assignments",
        "wo_checklist_items", "wo_comments", "wo_templates", "tech_profiles", "tech_labor_rates",
        "inventory", "inventory_transactions", "parts", "stocking_levels", "docs", "users",
        "locations", "vendors", "bins", "purchase_orders", "po_lines", "grns", "grn_lines",
        "cycle_counts", "cycle_count_lines", "part_issues", "part_issue_lines", "sync_queue",
        "sync_metadata", "devices", "role_permission_defaults", "account_role_permissions")
  }

  public override fun clearAllTables() {
    super.performClear(false, "assets", "log", "log_cost_lines", "photos", "configs", "schedules",
        "work_orders", "wo_assignments", "wo_checklist_items", "wo_comments", "wo_templates",
        "tech_profiles", "tech_labor_rates", "inventory", "inventory_transactions", "parts",
        "stocking_levels", "docs", "users", "locations", "vendors", "bins", "purchase_orders",
        "po_lines", "grns", "grn_lines", "cycle_counts", "cycle_count_lines", "part_issues",
        "part_issue_lines", "sync_queue", "sync_metadata", "devices", "role_permission_defaults",
        "account_role_permissions")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(AssetDao::class, AssetDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LogDao::class, LogDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LogCostLineDao::class, LogCostLineDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PhotoDao::class, PhotoDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ConfigDao::class, ConfigDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ScheduleDao::class, ScheduleDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WorkOrderDao::class, WorkOrderDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WoAssignmentDao::class, WoAssignmentDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WoChecklistItemDao::class,
        WoChecklistItemDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WoCommentDao::class, WoCommentDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WoTemplateDao::class, WoTemplateDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TechProfileDao::class, TechProfileDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TechLaborRateDao::class, TechLaborRateDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(InventoryDao::class, InventoryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(InventoryTransactionDao::class,
        InventoryTransactionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PartDao::class, PartDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(StockingLevelDao::class, StockingLevelDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DocDao::class, DocDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(UserDao::class, UserDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LocationDao::class, LocationDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(VendorDao::class, VendorDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BinDao::class, BinDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PurchaseOrderDao::class, PurchaseOrderDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PoLineDao::class, PoLineDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(GrnDao::class, GrnDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(GrnLineDao::class, GrnLineDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CycleCountDao::class, CycleCountDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CycleCountLineDao::class, CycleCountLineDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PartIssueDao::class, PartIssueDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PartIssueLineDao::class, PartIssueLineDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SyncQueueDao::class, SyncQueueDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SyncMetadataDao::class, SyncMetadataDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DeviceDao::class, DeviceDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(RolePermissionDefaultsDao::class,
        RolePermissionDefaultsDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(AccountRolePermissionsDao::class,
        AccountRolePermissionsDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun assetDao(): AssetDao = _assetDao.value

  public override fun logDao(): LogDao = _logDao.value

  public override fun logCostLineDao(): LogCostLineDao = _logCostLineDao.value

  public override fun photoDao(): PhotoDao = _photoDao.value

  public override fun configDao(): ConfigDao = _configDao.value

  public override fun scheduleDao(): ScheduleDao = _scheduleDao.value

  public override fun workOrderDao(): WorkOrderDao = _workOrderDao.value

  public override fun woAssignmentDao(): WoAssignmentDao = _woAssignmentDao.value

  public override fun woChecklistItemDao(): WoChecklistItemDao = _woChecklistItemDao.value

  public override fun woCommentDao(): WoCommentDao = _woCommentDao.value

  public override fun woTemplateDao(): WoTemplateDao = _woTemplateDao.value

  public override fun techProfileDao(): TechProfileDao = _techProfileDao.value

  public override fun techLaborRateDao(): TechLaborRateDao = _techLaborRateDao.value

  public override fun inventoryDao(): InventoryDao = _inventoryDao.value

  public override fun inventoryTransactionDao(): InventoryTransactionDao =
      _inventoryTransactionDao.value

  public override fun partDao(): PartDao = _partDao.value

  public override fun stockingLevelDao(): StockingLevelDao = _stockingLevelDao.value

  public override fun docDao(): DocDao = _docDao.value

  public override fun userDao(): UserDao = _userDao.value

  public override fun locationDao(): LocationDao = _locationDao.value

  public override fun vendorDao(): VendorDao = _vendorDao.value

  public override fun binDao(): BinDao = _binDao.value

  public override fun purchaseOrderDao(): PurchaseOrderDao = _purchaseOrderDao.value

  public override fun poLineDao(): PoLineDao = _poLineDao.value

  public override fun grnDao(): GrnDao = _grnDao.value

  public override fun grnLineDao(): GrnLineDao = _grnLineDao.value

  public override fun cycleCountDao(): CycleCountDao = _cycleCountDao.value

  public override fun cycleCountLineDao(): CycleCountLineDao = _cycleCountLineDao.value

  public override fun partIssueDao(): PartIssueDao = _partIssueDao.value

  public override fun partIssueLineDao(): PartIssueLineDao = _partIssueLineDao.value

  public override fun syncQueueDao(): SyncQueueDao = _syncQueueDao.value

  public override fun syncMetadataDao(): SyncMetadataDao = _syncMetadataDao.value

  public override fun deviceDao(): DeviceDao = _deviceDao.value

  public override fun rolePermissionDefaultsDao(): RolePermissionDefaultsDao =
      _rolePermissionDefaultsDao.value

  public override fun accountRolePermissionsDao(): AccountRolePermissionsDao =
      _accountRolePermissionsDao.value
}
