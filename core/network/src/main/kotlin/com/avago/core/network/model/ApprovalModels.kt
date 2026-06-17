package com.avago.core.network.model

import kotlinx.serialization.Serializable

// Change 95: approval delegation

@Serializable
data class DelegateApprovalRequest(
    val delegate_to_user_id: String,
    val reason: String? = null,
)

@Serializable
data class ApprovalResponse(
    val request_id: String,
    val status: String,
    val account_id: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

// Change 96+97: parallel approvals and auto-approve

@Serializable
data class ApprovalRuleResponse(
    val rule_id: String,
    val account_id: String,
    val entity_type: String? = null,
    val threshold_amount: Double? = null,
    val required_count: Int = 1,
    val auto_approve_after_hours: Int? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class ApprovalRequestResponse(
    val request_id: String,
    val account_id: String,
    val entity_type: String? = null,
    val entity_id: String? = null,
    val status: String,
    val required_count: Int? = null,
    val approved_count: Int? = null,
    val remaining_count: Int? = null,
    val auto_approve_at: String? = null,
    val approval_source: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

// Change 98: work order cost approvals

@Serializable
data class ApproveCostsRequest(
    val threshold_amount: Double,
    val notes: String? = null,
)

@Serializable
data class WoCostApproval(
    val approved_by_id: String? = null,
    val approved_at: String? = null,
    val threshold_amount: Double? = null,
    val approval_state: String,
)
