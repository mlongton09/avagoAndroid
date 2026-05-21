package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.WorkOrderEntity
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class WorkOrderDao_Impl(
  __db: RoomDatabase,
) : WorkOrderDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWorkOrderEntity: EntityInsertAdapter<WorkOrderEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfWorkOrderEntity = object : EntityInsertAdapter<WorkOrderEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `work_orders` (`wo_id`,`account_id`,`asset_id`,`location_id`,`title`,`description`,`category`,`priority`,`status`,`requester_id`,`assigned_to`,`dispatcher_notes`,`required_skills`,`estimated_effort_minutes`,`actual_effort_minutes`,`failure_code`,`completion_notes`,`parts_needed`,`log_id`,`due_date`,`started_at`,`completed_at`,`timer_started_at`,`labor_cost`,`parts_cost`,`total_cost`,`currency`,`base_amount`,`exchange_rate_used`,`attributes`,`created_by`,`approval_state`,`job_id`,`wo_kind`,`rrule`,`end_type`,`end_count`,`end_date`,`meter_type`,`meter_due`,`meter_interval`,`parent_wo_id`,`occurrence_date`,`schedule_id`,`last_completed_at`,`timezone`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WorkOrderEntity) {
        statement.bindText(1, entity.woId)
        statement.bindText(2, entity.accountId)
        val _tmpAssetId: String? = entity.assetId
        if (_tmpAssetId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpAssetId)
        }
        val _tmpLocationId: String? = entity.locationId
        if (_tmpLocationId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpLocationId)
        }
        statement.bindText(5, entity.title)
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpDescription)
        }
        val _tmpCategory: String? = entity.category
        if (_tmpCategory == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpCategory)
        }
        val _tmpPriority: String? = entity.priority
        if (_tmpPriority == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPriority)
        }
        statement.bindText(9, entity.status)
        val _tmpRequesterId: String? = entity.requesterId
        if (_tmpRequesterId == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpRequesterId)
        }
        val _tmpAssignedTo: String? = entity.assignedTo
        if (_tmpAssignedTo == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpAssignedTo)
        }
        val _tmpDispatcherNotes: String? = entity.dispatcherNotes
        if (_tmpDispatcherNotes == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpDispatcherNotes)
        }
        val _tmpRequiredSkills: String? = entity.requiredSkills
        if (_tmpRequiredSkills == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpRequiredSkills)
        }
        val _tmpEstimatedEffortMinutes: Long? = entity.estimatedEffortMinutes
        if (_tmpEstimatedEffortMinutes == null) {
          statement.bindNull(14)
        } else {
          statement.bindLong(14, _tmpEstimatedEffortMinutes)
        }
        val _tmpActualEffortMinutes: Long? = entity.actualEffortMinutes
        if (_tmpActualEffortMinutes == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpActualEffortMinutes)
        }
        val _tmpFailureCode: String? = entity.failureCode
        if (_tmpFailureCode == null) {
          statement.bindNull(16)
        } else {
          statement.bindText(16, _tmpFailureCode)
        }
        val _tmpCompletionNotes: String? = entity.completionNotes
        if (_tmpCompletionNotes == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpCompletionNotes)
        }
        val _tmpPartsNeeded: String? = entity.partsNeeded
        if (_tmpPartsNeeded == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpPartsNeeded)
        }
        val _tmpLogId: String? = entity.logId
        if (_tmpLogId == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpLogId)
        }
        val _tmpDueDate: Long? = entity.dueDate
        if (_tmpDueDate == null) {
          statement.bindNull(20)
        } else {
          statement.bindLong(20, _tmpDueDate)
        }
        val _tmpStartedAt: Long? = entity.startedAt
        if (_tmpStartedAt == null) {
          statement.bindNull(21)
        } else {
          statement.bindLong(21, _tmpStartedAt)
        }
        val _tmpCompletedAt: Long? = entity.completedAt
        if (_tmpCompletedAt == null) {
          statement.bindNull(22)
        } else {
          statement.bindLong(22, _tmpCompletedAt)
        }
        val _tmpTimerStartedAt: Long? = entity.timerStartedAt
        if (_tmpTimerStartedAt == null) {
          statement.bindNull(23)
        } else {
          statement.bindLong(23, _tmpTimerStartedAt)
        }
        val _tmpLaborCost: Double? = entity.laborCost
        if (_tmpLaborCost == null) {
          statement.bindNull(24)
        } else {
          statement.bindDouble(24, _tmpLaborCost)
        }
        val _tmpPartsCost: Double? = entity.partsCost
        if (_tmpPartsCost == null) {
          statement.bindNull(25)
        } else {
          statement.bindDouble(25, _tmpPartsCost)
        }
        val _tmpTotalCost: Double? = entity.totalCost
        if (_tmpTotalCost == null) {
          statement.bindNull(26)
        } else {
          statement.bindDouble(26, _tmpTotalCost)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(27)
        } else {
          statement.bindText(27, _tmpCurrency)
        }
        val _tmpBaseAmount: Double? = entity.baseAmount
        if (_tmpBaseAmount == null) {
          statement.bindNull(28)
        } else {
          statement.bindDouble(28, _tmpBaseAmount)
        }
        val _tmpExchangeRateUsed: Double? = entity.exchangeRateUsed
        if (_tmpExchangeRateUsed == null) {
          statement.bindNull(29)
        } else {
          statement.bindDouble(29, _tmpExchangeRateUsed)
        }
        val _tmpAttributes: String? = entity.attributes
        if (_tmpAttributes == null) {
          statement.bindNull(30)
        } else {
          statement.bindText(30, _tmpAttributes)
        }
        val _tmpCreatedBy: String? = entity.createdBy
        if (_tmpCreatedBy == null) {
          statement.bindNull(31)
        } else {
          statement.bindText(31, _tmpCreatedBy)
        }
        val _tmpApprovalState: String? = entity.approvalState
        if (_tmpApprovalState == null) {
          statement.bindNull(32)
        } else {
          statement.bindText(32, _tmpApprovalState)
        }
        val _tmpJobId: String? = entity.jobId
        if (_tmpJobId == null) {
          statement.bindNull(33)
        } else {
          statement.bindText(33, _tmpJobId)
        }
        val _tmpWoKind: String? = entity.woKind
        if (_tmpWoKind == null) {
          statement.bindNull(34)
        } else {
          statement.bindText(34, _tmpWoKind)
        }
        val _tmpRrule: String? = entity.rrule
        if (_tmpRrule == null) {
          statement.bindNull(35)
        } else {
          statement.bindText(35, _tmpRrule)
        }
        val _tmpEndType: String? = entity.endType
        if (_tmpEndType == null) {
          statement.bindNull(36)
        } else {
          statement.bindText(36, _tmpEndType)
        }
        val _tmpEndCount: Long? = entity.endCount
        if (_tmpEndCount == null) {
          statement.bindNull(37)
        } else {
          statement.bindLong(37, _tmpEndCount)
        }
        val _tmpEndDate: Long? = entity.endDate
        if (_tmpEndDate == null) {
          statement.bindNull(38)
        } else {
          statement.bindLong(38, _tmpEndDate)
        }
        val _tmpMeterType: String? = entity.meterType
        if (_tmpMeterType == null) {
          statement.bindNull(39)
        } else {
          statement.bindText(39, _tmpMeterType)
        }
        val _tmpMeterDue: Double? = entity.meterDue
        if (_tmpMeterDue == null) {
          statement.bindNull(40)
        } else {
          statement.bindDouble(40, _tmpMeterDue)
        }
        val _tmpMeterInterval: Double? = entity.meterInterval
        if (_tmpMeterInterval == null) {
          statement.bindNull(41)
        } else {
          statement.bindDouble(41, _tmpMeterInterval)
        }
        val _tmpParentWoId: String? = entity.parentWoId
        if (_tmpParentWoId == null) {
          statement.bindNull(42)
        } else {
          statement.bindText(42, _tmpParentWoId)
        }
        val _tmpOccurrenceDate: String? = entity.occurrenceDate
        if (_tmpOccurrenceDate == null) {
          statement.bindNull(43)
        } else {
          statement.bindText(43, _tmpOccurrenceDate)
        }
        val _tmpScheduleId: String? = entity.scheduleId
        if (_tmpScheduleId == null) {
          statement.bindNull(44)
        } else {
          statement.bindText(44, _tmpScheduleId)
        }
        val _tmpLastCompletedAt: Long? = entity.lastCompletedAt
        if (_tmpLastCompletedAt == null) {
          statement.bindNull(45)
        } else {
          statement.bindLong(45, _tmpLastCompletedAt)
        }
        val _tmpTimezone: String? = entity.timezone
        if (_tmpTimezone == null) {
          statement.bindNull(46)
        } else {
          statement.bindText(46, _tmpTimezone)
        }
        statement.bindLong(47, entity.createdAt)
        statement.bindLong(48, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(49)
        } else {
          statement.bindLong(49, _tmpDeletedAt)
        }
        statement.bindLong(50, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(51)
        } else {
          statement.bindLong(51, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: WorkOrderEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfWorkOrderEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<WorkOrderEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWorkOrderEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<WorkOrderEntity>> {
    val _sql: String = "SELECT * FROM work_orders WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("work_orders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfRequesterId: Int = getColumnIndexOrThrow(_stmt, "requester_id")
        val _columnIndexOfAssignedTo: Int = getColumnIndexOrThrow(_stmt, "assigned_to")
        val _columnIndexOfDispatcherNotes: Int = getColumnIndexOrThrow(_stmt, "dispatcher_notes")
        val _columnIndexOfRequiredSkills: Int = getColumnIndexOrThrow(_stmt, "required_skills")
        val _columnIndexOfEstimatedEffortMinutes: Int = getColumnIndexOrThrow(_stmt,
            "estimated_effort_minutes")
        val _columnIndexOfActualEffortMinutes: Int = getColumnIndexOrThrow(_stmt,
            "actual_effort_minutes")
        val _columnIndexOfFailureCode: Int = getColumnIndexOrThrow(_stmt, "failure_code")
        val _columnIndexOfCompletionNotes: Int = getColumnIndexOrThrow(_stmt, "completion_notes")
        val _columnIndexOfPartsNeeded: Int = getColumnIndexOrThrow(_stmt, "parts_needed")
        val _columnIndexOfLogId: Int = getColumnIndexOrThrow(_stmt, "log_id")
        val _columnIndexOfDueDate: Int = getColumnIndexOrThrow(_stmt, "due_date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "started_at")
        val _columnIndexOfCompletedAt: Int = getColumnIndexOrThrow(_stmt, "completed_at")
        val _columnIndexOfTimerStartedAt: Int = getColumnIndexOrThrow(_stmt, "timer_started_at")
        val _columnIndexOfLaborCost: Int = getColumnIndexOrThrow(_stmt, "labor_cost")
        val _columnIndexOfPartsCost: Int = getColumnIndexOrThrow(_stmt, "parts_cost")
        val _columnIndexOfTotalCost: Int = getColumnIndexOrThrow(_stmt, "total_cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfBaseAmount: Int = getColumnIndexOrThrow(_stmt, "base_amount")
        val _columnIndexOfExchangeRateUsed: Int = getColumnIndexOrThrow(_stmt, "exchange_rate_used")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfCreatedBy: Int = getColumnIndexOrThrow(_stmt, "created_by")
        val _columnIndexOfApprovalState: Int = getColumnIndexOrThrow(_stmt, "approval_state")
        val _columnIndexOfJobId: Int = getColumnIndexOrThrow(_stmt, "job_id")
        val _columnIndexOfWoKind: Int = getColumnIndexOrThrow(_stmt, "wo_kind")
        val _columnIndexOfRrule: Int = getColumnIndexOrThrow(_stmt, "rrule")
        val _columnIndexOfEndType: Int = getColumnIndexOrThrow(_stmt, "end_type")
        val _columnIndexOfEndCount: Int = getColumnIndexOrThrow(_stmt, "end_count")
        val _columnIndexOfEndDate: Int = getColumnIndexOrThrow(_stmt, "end_date")
        val _columnIndexOfMeterType: Int = getColumnIndexOrThrow(_stmt, "meter_type")
        val _columnIndexOfMeterDue: Int = getColumnIndexOrThrow(_stmt, "meter_due")
        val _columnIndexOfMeterInterval: Int = getColumnIndexOrThrow(_stmt, "meter_interval")
        val _columnIndexOfParentWoId: Int = getColumnIndexOrThrow(_stmt, "parent_wo_id")
        val _columnIndexOfOccurrenceDate: Int = getColumnIndexOrThrow(_stmt, "occurrence_date")
        val _columnIndexOfScheduleId: Int = getColumnIndexOrThrow(_stmt, "schedule_id")
        val _columnIndexOfLastCompletedAt: Int = getColumnIndexOrThrow(_stmt, "last_completed_at")
        val _columnIndexOfTimezone: Int = getColumnIndexOrThrow(_stmt, "timezone")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<WorkOrderEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorkOrderEntity
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpAssetId: String?
          if (_stmt.isNull(_columnIndexOfAssetId)) {
            _tmpAssetId = null
          } else {
            _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          }
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpPriority: String?
          if (_stmt.isNull(_columnIndexOfPriority)) {
            _tmpPriority = null
          } else {
            _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpRequesterId: String?
          if (_stmt.isNull(_columnIndexOfRequesterId)) {
            _tmpRequesterId = null
          } else {
            _tmpRequesterId = _stmt.getText(_columnIndexOfRequesterId)
          }
          val _tmpAssignedTo: String?
          if (_stmt.isNull(_columnIndexOfAssignedTo)) {
            _tmpAssignedTo = null
          } else {
            _tmpAssignedTo = _stmt.getText(_columnIndexOfAssignedTo)
          }
          val _tmpDispatcherNotes: String?
          if (_stmt.isNull(_columnIndexOfDispatcherNotes)) {
            _tmpDispatcherNotes = null
          } else {
            _tmpDispatcherNotes = _stmt.getText(_columnIndexOfDispatcherNotes)
          }
          val _tmpRequiredSkills: String?
          if (_stmt.isNull(_columnIndexOfRequiredSkills)) {
            _tmpRequiredSkills = null
          } else {
            _tmpRequiredSkills = _stmt.getText(_columnIndexOfRequiredSkills)
          }
          val _tmpEstimatedEffortMinutes: Long?
          if (_stmt.isNull(_columnIndexOfEstimatedEffortMinutes)) {
            _tmpEstimatedEffortMinutes = null
          } else {
            _tmpEstimatedEffortMinutes = _stmt.getLong(_columnIndexOfEstimatedEffortMinutes)
          }
          val _tmpActualEffortMinutes: Long?
          if (_stmt.isNull(_columnIndexOfActualEffortMinutes)) {
            _tmpActualEffortMinutes = null
          } else {
            _tmpActualEffortMinutes = _stmt.getLong(_columnIndexOfActualEffortMinutes)
          }
          val _tmpFailureCode: String?
          if (_stmt.isNull(_columnIndexOfFailureCode)) {
            _tmpFailureCode = null
          } else {
            _tmpFailureCode = _stmt.getText(_columnIndexOfFailureCode)
          }
          val _tmpCompletionNotes: String?
          if (_stmt.isNull(_columnIndexOfCompletionNotes)) {
            _tmpCompletionNotes = null
          } else {
            _tmpCompletionNotes = _stmt.getText(_columnIndexOfCompletionNotes)
          }
          val _tmpPartsNeeded: String?
          if (_stmt.isNull(_columnIndexOfPartsNeeded)) {
            _tmpPartsNeeded = null
          } else {
            _tmpPartsNeeded = _stmt.getText(_columnIndexOfPartsNeeded)
          }
          val _tmpLogId: String?
          if (_stmt.isNull(_columnIndexOfLogId)) {
            _tmpLogId = null
          } else {
            _tmpLogId = _stmt.getText(_columnIndexOfLogId)
          }
          val _tmpDueDate: Long?
          if (_stmt.isNull(_columnIndexOfDueDate)) {
            _tmpDueDate = null
          } else {
            _tmpDueDate = _stmt.getLong(_columnIndexOfDueDate)
          }
          val _tmpStartedAt: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmpStartedAt = null
          } else {
            _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmpCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfCompletedAt)) {
            _tmpCompletedAt = null
          } else {
            _tmpCompletedAt = _stmt.getLong(_columnIndexOfCompletedAt)
          }
          val _tmpTimerStartedAt: Long?
          if (_stmt.isNull(_columnIndexOfTimerStartedAt)) {
            _tmpTimerStartedAt = null
          } else {
            _tmpTimerStartedAt = _stmt.getLong(_columnIndexOfTimerStartedAt)
          }
          val _tmpLaborCost: Double?
          if (_stmt.isNull(_columnIndexOfLaborCost)) {
            _tmpLaborCost = null
          } else {
            _tmpLaborCost = _stmt.getDouble(_columnIndexOfLaborCost)
          }
          val _tmpPartsCost: Double?
          if (_stmt.isNull(_columnIndexOfPartsCost)) {
            _tmpPartsCost = null
          } else {
            _tmpPartsCost = _stmt.getDouble(_columnIndexOfPartsCost)
          }
          val _tmpTotalCost: Double?
          if (_stmt.isNull(_columnIndexOfTotalCost)) {
            _tmpTotalCost = null
          } else {
            _tmpTotalCost = _stmt.getDouble(_columnIndexOfTotalCost)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpBaseAmount: Double?
          if (_stmt.isNull(_columnIndexOfBaseAmount)) {
            _tmpBaseAmount = null
          } else {
            _tmpBaseAmount = _stmt.getDouble(_columnIndexOfBaseAmount)
          }
          val _tmpExchangeRateUsed: Double?
          if (_stmt.isNull(_columnIndexOfExchangeRateUsed)) {
            _tmpExchangeRateUsed = null
          } else {
            _tmpExchangeRateUsed = _stmt.getDouble(_columnIndexOfExchangeRateUsed)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
          }
          val _tmpCreatedBy: String?
          if (_stmt.isNull(_columnIndexOfCreatedBy)) {
            _tmpCreatedBy = null
          } else {
            _tmpCreatedBy = _stmt.getText(_columnIndexOfCreatedBy)
          }
          val _tmpApprovalState: String?
          if (_stmt.isNull(_columnIndexOfApprovalState)) {
            _tmpApprovalState = null
          } else {
            _tmpApprovalState = _stmt.getText(_columnIndexOfApprovalState)
          }
          val _tmpJobId: String?
          if (_stmt.isNull(_columnIndexOfJobId)) {
            _tmpJobId = null
          } else {
            _tmpJobId = _stmt.getText(_columnIndexOfJobId)
          }
          val _tmpWoKind: String?
          if (_stmt.isNull(_columnIndexOfWoKind)) {
            _tmpWoKind = null
          } else {
            _tmpWoKind = _stmt.getText(_columnIndexOfWoKind)
          }
          val _tmpRrule: String?
          if (_stmt.isNull(_columnIndexOfRrule)) {
            _tmpRrule = null
          } else {
            _tmpRrule = _stmt.getText(_columnIndexOfRrule)
          }
          val _tmpEndType: String?
          if (_stmt.isNull(_columnIndexOfEndType)) {
            _tmpEndType = null
          } else {
            _tmpEndType = _stmt.getText(_columnIndexOfEndType)
          }
          val _tmpEndCount: Long?
          if (_stmt.isNull(_columnIndexOfEndCount)) {
            _tmpEndCount = null
          } else {
            _tmpEndCount = _stmt.getLong(_columnIndexOfEndCount)
          }
          val _tmpEndDate: Long?
          if (_stmt.isNull(_columnIndexOfEndDate)) {
            _tmpEndDate = null
          } else {
            _tmpEndDate = _stmt.getLong(_columnIndexOfEndDate)
          }
          val _tmpMeterType: String?
          if (_stmt.isNull(_columnIndexOfMeterType)) {
            _tmpMeterType = null
          } else {
            _tmpMeterType = _stmt.getText(_columnIndexOfMeterType)
          }
          val _tmpMeterDue: Double?
          if (_stmt.isNull(_columnIndexOfMeterDue)) {
            _tmpMeterDue = null
          } else {
            _tmpMeterDue = _stmt.getDouble(_columnIndexOfMeterDue)
          }
          val _tmpMeterInterval: Double?
          if (_stmt.isNull(_columnIndexOfMeterInterval)) {
            _tmpMeterInterval = null
          } else {
            _tmpMeterInterval = _stmt.getDouble(_columnIndexOfMeterInterval)
          }
          val _tmpParentWoId: String?
          if (_stmt.isNull(_columnIndexOfParentWoId)) {
            _tmpParentWoId = null
          } else {
            _tmpParentWoId = _stmt.getText(_columnIndexOfParentWoId)
          }
          val _tmpOccurrenceDate: String?
          if (_stmt.isNull(_columnIndexOfOccurrenceDate)) {
            _tmpOccurrenceDate = null
          } else {
            _tmpOccurrenceDate = _stmt.getText(_columnIndexOfOccurrenceDate)
          }
          val _tmpScheduleId: String?
          if (_stmt.isNull(_columnIndexOfScheduleId)) {
            _tmpScheduleId = null
          } else {
            _tmpScheduleId = _stmt.getText(_columnIndexOfScheduleId)
          }
          val _tmpLastCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfLastCompletedAt)) {
            _tmpLastCompletedAt = null
          } else {
            _tmpLastCompletedAt = _stmt.getLong(_columnIndexOfLastCompletedAt)
          }
          val _tmpTimezone: String?
          if (_stmt.isNull(_columnIndexOfTimezone)) {
            _tmpTimezone = null
          } else {
            _tmpTimezone = _stmt.getText(_columnIndexOfTimezone)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              WorkOrderEntity(_tmpWoId,_tmpAccountId,_tmpAssetId,_tmpLocationId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpPriority,_tmpStatus,_tmpRequesterId,_tmpAssignedTo,_tmpDispatcherNotes,_tmpRequiredSkills,_tmpEstimatedEffortMinutes,_tmpActualEffortMinutes,_tmpFailureCode,_tmpCompletionNotes,_tmpPartsNeeded,_tmpLogId,_tmpDueDate,_tmpStartedAt,_tmpCompletedAt,_tmpTimerStartedAt,_tmpLaborCost,_tmpPartsCost,_tmpTotalCost,_tmpCurrency,_tmpBaseAmount,_tmpExchangeRateUsed,_tmpAttributes,_tmpCreatedBy,_tmpApprovalState,_tmpJobId,_tmpWoKind,_tmpRrule,_tmpEndType,_tmpEndCount,_tmpEndDate,_tmpMeterType,_tmpMeterDue,_tmpMeterInterval,_tmpParentWoId,_tmpOccurrenceDate,_tmpScheduleId,_tmpLastCompletedAt,_tmpTimezone,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): WorkOrderEntity? {
    val _sql: String = "SELECT * FROM work_orders WHERE wo_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfRequesterId: Int = getColumnIndexOrThrow(_stmt, "requester_id")
        val _columnIndexOfAssignedTo: Int = getColumnIndexOrThrow(_stmt, "assigned_to")
        val _columnIndexOfDispatcherNotes: Int = getColumnIndexOrThrow(_stmt, "dispatcher_notes")
        val _columnIndexOfRequiredSkills: Int = getColumnIndexOrThrow(_stmt, "required_skills")
        val _columnIndexOfEstimatedEffortMinutes: Int = getColumnIndexOrThrow(_stmt,
            "estimated_effort_minutes")
        val _columnIndexOfActualEffortMinutes: Int = getColumnIndexOrThrow(_stmt,
            "actual_effort_minutes")
        val _columnIndexOfFailureCode: Int = getColumnIndexOrThrow(_stmt, "failure_code")
        val _columnIndexOfCompletionNotes: Int = getColumnIndexOrThrow(_stmt, "completion_notes")
        val _columnIndexOfPartsNeeded: Int = getColumnIndexOrThrow(_stmt, "parts_needed")
        val _columnIndexOfLogId: Int = getColumnIndexOrThrow(_stmt, "log_id")
        val _columnIndexOfDueDate: Int = getColumnIndexOrThrow(_stmt, "due_date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "started_at")
        val _columnIndexOfCompletedAt: Int = getColumnIndexOrThrow(_stmt, "completed_at")
        val _columnIndexOfTimerStartedAt: Int = getColumnIndexOrThrow(_stmt, "timer_started_at")
        val _columnIndexOfLaborCost: Int = getColumnIndexOrThrow(_stmt, "labor_cost")
        val _columnIndexOfPartsCost: Int = getColumnIndexOrThrow(_stmt, "parts_cost")
        val _columnIndexOfTotalCost: Int = getColumnIndexOrThrow(_stmt, "total_cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfBaseAmount: Int = getColumnIndexOrThrow(_stmt, "base_amount")
        val _columnIndexOfExchangeRateUsed: Int = getColumnIndexOrThrow(_stmt, "exchange_rate_used")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfCreatedBy: Int = getColumnIndexOrThrow(_stmt, "created_by")
        val _columnIndexOfApprovalState: Int = getColumnIndexOrThrow(_stmt, "approval_state")
        val _columnIndexOfJobId: Int = getColumnIndexOrThrow(_stmt, "job_id")
        val _columnIndexOfWoKind: Int = getColumnIndexOrThrow(_stmt, "wo_kind")
        val _columnIndexOfRrule: Int = getColumnIndexOrThrow(_stmt, "rrule")
        val _columnIndexOfEndType: Int = getColumnIndexOrThrow(_stmt, "end_type")
        val _columnIndexOfEndCount: Int = getColumnIndexOrThrow(_stmt, "end_count")
        val _columnIndexOfEndDate: Int = getColumnIndexOrThrow(_stmt, "end_date")
        val _columnIndexOfMeterType: Int = getColumnIndexOrThrow(_stmt, "meter_type")
        val _columnIndexOfMeterDue: Int = getColumnIndexOrThrow(_stmt, "meter_due")
        val _columnIndexOfMeterInterval: Int = getColumnIndexOrThrow(_stmt, "meter_interval")
        val _columnIndexOfParentWoId: Int = getColumnIndexOrThrow(_stmt, "parent_wo_id")
        val _columnIndexOfOccurrenceDate: Int = getColumnIndexOrThrow(_stmt, "occurrence_date")
        val _columnIndexOfScheduleId: Int = getColumnIndexOrThrow(_stmt, "schedule_id")
        val _columnIndexOfLastCompletedAt: Int = getColumnIndexOrThrow(_stmt, "last_completed_at")
        val _columnIndexOfTimezone: Int = getColumnIndexOrThrow(_stmt, "timezone")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: WorkOrderEntity?
        if (_stmt.step()) {
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpAssetId: String?
          if (_stmt.isNull(_columnIndexOfAssetId)) {
            _tmpAssetId = null
          } else {
            _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          }
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpPriority: String?
          if (_stmt.isNull(_columnIndexOfPriority)) {
            _tmpPriority = null
          } else {
            _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpRequesterId: String?
          if (_stmt.isNull(_columnIndexOfRequesterId)) {
            _tmpRequesterId = null
          } else {
            _tmpRequesterId = _stmt.getText(_columnIndexOfRequesterId)
          }
          val _tmpAssignedTo: String?
          if (_stmt.isNull(_columnIndexOfAssignedTo)) {
            _tmpAssignedTo = null
          } else {
            _tmpAssignedTo = _stmt.getText(_columnIndexOfAssignedTo)
          }
          val _tmpDispatcherNotes: String?
          if (_stmt.isNull(_columnIndexOfDispatcherNotes)) {
            _tmpDispatcherNotes = null
          } else {
            _tmpDispatcherNotes = _stmt.getText(_columnIndexOfDispatcherNotes)
          }
          val _tmpRequiredSkills: String?
          if (_stmt.isNull(_columnIndexOfRequiredSkills)) {
            _tmpRequiredSkills = null
          } else {
            _tmpRequiredSkills = _stmt.getText(_columnIndexOfRequiredSkills)
          }
          val _tmpEstimatedEffortMinutes: Long?
          if (_stmt.isNull(_columnIndexOfEstimatedEffortMinutes)) {
            _tmpEstimatedEffortMinutes = null
          } else {
            _tmpEstimatedEffortMinutes = _stmt.getLong(_columnIndexOfEstimatedEffortMinutes)
          }
          val _tmpActualEffortMinutes: Long?
          if (_stmt.isNull(_columnIndexOfActualEffortMinutes)) {
            _tmpActualEffortMinutes = null
          } else {
            _tmpActualEffortMinutes = _stmt.getLong(_columnIndexOfActualEffortMinutes)
          }
          val _tmpFailureCode: String?
          if (_stmt.isNull(_columnIndexOfFailureCode)) {
            _tmpFailureCode = null
          } else {
            _tmpFailureCode = _stmt.getText(_columnIndexOfFailureCode)
          }
          val _tmpCompletionNotes: String?
          if (_stmt.isNull(_columnIndexOfCompletionNotes)) {
            _tmpCompletionNotes = null
          } else {
            _tmpCompletionNotes = _stmt.getText(_columnIndexOfCompletionNotes)
          }
          val _tmpPartsNeeded: String?
          if (_stmt.isNull(_columnIndexOfPartsNeeded)) {
            _tmpPartsNeeded = null
          } else {
            _tmpPartsNeeded = _stmt.getText(_columnIndexOfPartsNeeded)
          }
          val _tmpLogId: String?
          if (_stmt.isNull(_columnIndexOfLogId)) {
            _tmpLogId = null
          } else {
            _tmpLogId = _stmt.getText(_columnIndexOfLogId)
          }
          val _tmpDueDate: Long?
          if (_stmt.isNull(_columnIndexOfDueDate)) {
            _tmpDueDate = null
          } else {
            _tmpDueDate = _stmt.getLong(_columnIndexOfDueDate)
          }
          val _tmpStartedAt: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmpStartedAt = null
          } else {
            _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmpCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfCompletedAt)) {
            _tmpCompletedAt = null
          } else {
            _tmpCompletedAt = _stmt.getLong(_columnIndexOfCompletedAt)
          }
          val _tmpTimerStartedAt: Long?
          if (_stmt.isNull(_columnIndexOfTimerStartedAt)) {
            _tmpTimerStartedAt = null
          } else {
            _tmpTimerStartedAt = _stmt.getLong(_columnIndexOfTimerStartedAt)
          }
          val _tmpLaborCost: Double?
          if (_stmt.isNull(_columnIndexOfLaborCost)) {
            _tmpLaborCost = null
          } else {
            _tmpLaborCost = _stmt.getDouble(_columnIndexOfLaborCost)
          }
          val _tmpPartsCost: Double?
          if (_stmt.isNull(_columnIndexOfPartsCost)) {
            _tmpPartsCost = null
          } else {
            _tmpPartsCost = _stmt.getDouble(_columnIndexOfPartsCost)
          }
          val _tmpTotalCost: Double?
          if (_stmt.isNull(_columnIndexOfTotalCost)) {
            _tmpTotalCost = null
          } else {
            _tmpTotalCost = _stmt.getDouble(_columnIndexOfTotalCost)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpBaseAmount: Double?
          if (_stmt.isNull(_columnIndexOfBaseAmount)) {
            _tmpBaseAmount = null
          } else {
            _tmpBaseAmount = _stmt.getDouble(_columnIndexOfBaseAmount)
          }
          val _tmpExchangeRateUsed: Double?
          if (_stmt.isNull(_columnIndexOfExchangeRateUsed)) {
            _tmpExchangeRateUsed = null
          } else {
            _tmpExchangeRateUsed = _stmt.getDouble(_columnIndexOfExchangeRateUsed)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
          }
          val _tmpCreatedBy: String?
          if (_stmt.isNull(_columnIndexOfCreatedBy)) {
            _tmpCreatedBy = null
          } else {
            _tmpCreatedBy = _stmt.getText(_columnIndexOfCreatedBy)
          }
          val _tmpApprovalState: String?
          if (_stmt.isNull(_columnIndexOfApprovalState)) {
            _tmpApprovalState = null
          } else {
            _tmpApprovalState = _stmt.getText(_columnIndexOfApprovalState)
          }
          val _tmpJobId: String?
          if (_stmt.isNull(_columnIndexOfJobId)) {
            _tmpJobId = null
          } else {
            _tmpJobId = _stmt.getText(_columnIndexOfJobId)
          }
          val _tmpWoKind: String?
          if (_stmt.isNull(_columnIndexOfWoKind)) {
            _tmpWoKind = null
          } else {
            _tmpWoKind = _stmt.getText(_columnIndexOfWoKind)
          }
          val _tmpRrule: String?
          if (_stmt.isNull(_columnIndexOfRrule)) {
            _tmpRrule = null
          } else {
            _tmpRrule = _stmt.getText(_columnIndexOfRrule)
          }
          val _tmpEndType: String?
          if (_stmt.isNull(_columnIndexOfEndType)) {
            _tmpEndType = null
          } else {
            _tmpEndType = _stmt.getText(_columnIndexOfEndType)
          }
          val _tmpEndCount: Long?
          if (_stmt.isNull(_columnIndexOfEndCount)) {
            _tmpEndCount = null
          } else {
            _tmpEndCount = _stmt.getLong(_columnIndexOfEndCount)
          }
          val _tmpEndDate: Long?
          if (_stmt.isNull(_columnIndexOfEndDate)) {
            _tmpEndDate = null
          } else {
            _tmpEndDate = _stmt.getLong(_columnIndexOfEndDate)
          }
          val _tmpMeterType: String?
          if (_stmt.isNull(_columnIndexOfMeterType)) {
            _tmpMeterType = null
          } else {
            _tmpMeterType = _stmt.getText(_columnIndexOfMeterType)
          }
          val _tmpMeterDue: Double?
          if (_stmt.isNull(_columnIndexOfMeterDue)) {
            _tmpMeterDue = null
          } else {
            _tmpMeterDue = _stmt.getDouble(_columnIndexOfMeterDue)
          }
          val _tmpMeterInterval: Double?
          if (_stmt.isNull(_columnIndexOfMeterInterval)) {
            _tmpMeterInterval = null
          } else {
            _tmpMeterInterval = _stmt.getDouble(_columnIndexOfMeterInterval)
          }
          val _tmpParentWoId: String?
          if (_stmt.isNull(_columnIndexOfParentWoId)) {
            _tmpParentWoId = null
          } else {
            _tmpParentWoId = _stmt.getText(_columnIndexOfParentWoId)
          }
          val _tmpOccurrenceDate: String?
          if (_stmt.isNull(_columnIndexOfOccurrenceDate)) {
            _tmpOccurrenceDate = null
          } else {
            _tmpOccurrenceDate = _stmt.getText(_columnIndexOfOccurrenceDate)
          }
          val _tmpScheduleId: String?
          if (_stmt.isNull(_columnIndexOfScheduleId)) {
            _tmpScheduleId = null
          } else {
            _tmpScheduleId = _stmt.getText(_columnIndexOfScheduleId)
          }
          val _tmpLastCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfLastCompletedAt)) {
            _tmpLastCompletedAt = null
          } else {
            _tmpLastCompletedAt = _stmt.getLong(_columnIndexOfLastCompletedAt)
          }
          val _tmpTimezone: String?
          if (_stmt.isNull(_columnIndexOfTimezone)) {
            _tmpTimezone = null
          } else {
            _tmpTimezone = _stmt.getText(_columnIndexOfTimezone)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              WorkOrderEntity(_tmpWoId,_tmpAccountId,_tmpAssetId,_tmpLocationId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpPriority,_tmpStatus,_tmpRequesterId,_tmpAssignedTo,_tmpDispatcherNotes,_tmpRequiredSkills,_tmpEstimatedEffortMinutes,_tmpActualEffortMinutes,_tmpFailureCode,_tmpCompletionNotes,_tmpPartsNeeded,_tmpLogId,_tmpDueDate,_tmpStartedAt,_tmpCompletedAt,_tmpTimerStartedAt,_tmpLaborCost,_tmpPartsCost,_tmpTotalCost,_tmpCurrency,_tmpBaseAmount,_tmpExchangeRateUsed,_tmpAttributes,_tmpCreatedBy,_tmpApprovalState,_tmpJobId,_tmpWoKind,_tmpRrule,_tmpEndType,_tmpEndCount,_tmpEndDate,_tmpMeterType,_tmpMeterDue,_tmpMeterInterval,_tmpParentWoId,_tmpOccurrenceDate,_tmpScheduleId,_tmpLastCompletedAt,_tmpTimezone,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDelete(id: String, now: Long) {
    val _sql: String = "UPDATE work_orders SET deleted_at = ?, updated_at = ? WHERE wo_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, now)
        _argIndex = 2
        _stmt.bindLong(_argIndex, now)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
