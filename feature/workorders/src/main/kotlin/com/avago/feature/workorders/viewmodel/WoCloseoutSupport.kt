package com.avago.feature.workorders.viewmodel

import com.avago.core.data.FeatureFlags
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.ReferenceRepository
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderCompletion
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.feature.workorders.model.WO_KEY_CLOSEOUT
import com.avago.feature.workorders.model.WO_KEY_HOLD
import com.avago.feature.workorders.model.WoCloseout
import com.avago.feature.workorders.model.WoCloseoutOptions
import com.avago.feature.workorders.model.WoCode
import com.avago.feature.workorders.model.WoCodeKind
import com.avago.feature.workorders.model.woCodesFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import javax.inject.Inject

/** Close-out configuration and submission shared by the detail and log screens. */
class WoCloseoutSupport @Inject constructor(
    private val repository: WorkOrderRepository,
    private val assets: AssetRepository,
    references: ReferenceRepository,
    featureFlags: FeatureFlags,
) {
    /** Failure/cause/remedy codes from the account's reference values (defaults when none are defined). */
    val codes: Flow<Map<WoCodeKind, List<WoCode>>> = combine(
        references.observeValues(ReferenceRepository.WO_FAILURE),
        references.observeValues(ReferenceRepository.WO_CAUSE),
        references.observeValues(ReferenceRepository.WO_REMEDY),
    ) { failure, cause, remedy -> woCodesFrom(failure, cause, remedy) }
        .catch { emit(DEFAULT_CODES) }

    /** Close-out opt-ins from the account configuration. */
    val options: Flow<WoCloseoutOptions> = combine(
        featureFlags.observeFlag("wo_closeout_require_signature", default = false),
        featureFlags.observeFlag("wo_closeout_require_downtime", default = false),
    ) { signature, downtime -> WoCloseoutOptions(requireSignature = signature, requireDowntime = downtime) }

    /**
     * Records the meter reading, stores the close-out in `attributes` (clearing any hold) and
     * completes the work order with its codes and notes.
     */
    suspend fun complete(wo: WorkOrder, closeout: WoCloseout) {
        val assetId = wo.primaryAssetId
        val reading = closeout.meterReading
        if (assetId != null && reading != null) {
            assets.recordReading(assetId, BigDecimal.valueOf(reading), System.currentTimeMillis(), closeout.resolutionNotes)
        }
        repository.patchAttributes(wo.id, mapOf(WO_KEY_CLOSEOUT to closeout.toJson(), WO_KEY_HOLD to null))
        repository.transition(
            wo.id,
            WorkOrderStatus.COMPLETED,
            WorkOrderCompletion(
                failureCode = closeout.failureCode,
                causeCode = closeout.causeCode,
                remedyCode = closeout.remedyCode,
                completionNotes = closeout.resolutionNotes,
            ),
        )
    }

    companion object {
        val DEFAULT_CODES: Map<WoCodeKind, List<WoCode>> = woCodesFrom(emptyList(), emptyList(), emptyList())
    }
}
