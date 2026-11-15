package com.avago.feature.crew.data

import com.avago.core.network.json.bool
import com.avago.core.network.json.int
import com.avago.core.network.json.long
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import kotlinx.serialization.json.JsonObject

// Equipment lending (feature D §6) and co-ownership (§7).

data class Loan(
    val id: String,
    val accountName: String?,
    val assetName: String,
    val circleName: String?,
    val borrowerName: String?,
    val status: String,
    val startsAt: String?,
    val endsAt: String?,
    val terms: String?,
    val agreementText: String?,
    val agreementSha256: String?,
    val myRole: String?,
    val allowedActions: Set<String>,
    val consentVersion: String,
    val consentText: String?,
    val photos: Int,
    val defects: Int,
) {
    fun can(action: String) = action in allowedActions

    companion object {
        fun from(o: JsonObject): Loan? {
            val id = o.str("id") ?: return null
            val makeModel = listOfNotNull(o.str("asset_make"), o.str("asset_model")).joinToString(" ").ifBlank { null }
            val consent = o.obj("esign_consent")
            return Loan(
                id = id,
                accountName = o.str("account_name"),
                assetName = o.str("asset_name") ?: makeModel ?: "—",
                circleName = o.str("circle_name"),
                borrowerName = o.str("borrower_name"),
                status = o.str("status") ?: "draft",
                startsAt = o.str("starts_at"),
                endsAt = o.str("ends_at"),
                terms = o.str("terms") ?: o.obj("terms")?.toString(),
                agreementText = o.str("agreement_text"),
                agreementSha256 = o.str("agreement_sha256"),
                myRole = o.str("my_role"),
                allowedActions = o.strs("allowed_actions").toSet(),
                consentVersion = consent?.str("version") ?: "v1",
                consentText = consent?.str("text"),
                photos = o.objs("photos").size,
                defects = o.objs("defects").size,
            )
        }
    }
}

data class MyLoans(val borrowing: List<Loan>, val lending: List<Loan>) {
    companion object {
        fun from(o: JsonObject) = MyLoans(o.objs("borrowing").mapNotNull(Loan::from), o.objs("lending").mapNotNull(Loan::from))
    }
}

/** Handover-out / return checklist line. */
data class HandoverCheck(val key: String, val label: String, val ok: Boolean, val note: String? = null)

data class DamageEntry(val label: String, val note: String? = null, val severity: String? = null)

data class HandoverInput(
    val checklist: List<HandoverCheck>,
    val photoIds: List<String>,
    val meterValue: Double? = null,
    val fuelLevel: String? = null,
    val notes: String? = null,
    val damage: List<DamageEntry> = emptyList(),
)

// ---- Co-ownership ---------------------------------------------------------------------

data class CoOwnership(
    val groupId: String,
    val assetName: String,
    val role: String?,
    val shareBp: Int,
    val memberCount: Int,
    val myMemberId: String?,
) {
    companion object {
        fun from(o: JsonObject): CoOwnership? {
            val id = o.str("group_id") ?: o.str("id") ?: return null
            val makeModel = listOfNotNull(o.str("make"), o.str("model")).joinToString(" ").ifBlank { null }
            return CoOwnership(id, o.str("asset_name") ?: makeModel ?: "—", o.str("role"), o.int("share_bp") ?: 0, o.int("member_count") ?: 0, o.str("my_member_id"))
        }
    }
}

data class CoMember(val id: String, val name: String, val shareBp: Int)

data class CoGroup(val id: String, val assetName: String, val isPrimary: Boolean, val myMemberId: String?, val members: List<CoMember>) {
    companion object {
        fun from(o: JsonObject): CoGroup {
            val g = o.obj("group") ?: o
            return CoGroup(
                id = g.str("id") ?: g.str("group_id") ?: "",
                assetName = g.str("asset_name") ?: o.str("asset_name") ?: "—",
                isPrimary = (o.bool("is_primary") ?: g.bool("is_primary")) == true,
                myMemberId = o.str("my_member_id") ?: g.str("my_member_id"),
                members = (o.objs("members").ifEmpty { g.objs("members") }).mapNotNull { m ->
                    val id = m.str("id") ?: m.str("member_id") ?: return@mapNotNull null
                    CoMember(id, m.str("display_name") ?: m.str("name") ?: "—", m.int("share_bp") ?: 0)
                },
            )
        }
    }
}

data class CoBooking(
    val id: String,
    val memberId: String?,
    val memberName: String?,
    val startsAt: String,
    val endsAt: String,
    val status: String?,
    val notes: String?,
) {
    companion object {
        fun from(o: JsonObject): CoBooking? {
            val id = o.str("id") ?: return null
            return CoBooking(id, o.str("member_id"), o.str("member_name"), o.str("starts_at") ?: return null, o.str("ends_at") ?: return null, o.str("status"), o.str("notes"))
        }
    }
}

data class CoBookings(val items: List<CoBooking>, val blocked: Int) {
    companion object {
        fun from(o: JsonObject) = CoBookings(o.objs("items").mapNotNull(CoBooking::from), o.objs("blocked").size)
    }
}

data class MemberBalance(val memberId: String, val name: String, val shareBp: Int, val paidMinor: Long, val owedMinor: Long, val netMinor: Long)

data class SettleUp(val fromName: String, val toName: String, val amountMinor: Long)

data class CoBalances(
    val currency: String,
    val splitMethod: String?,
    val members: List<MemberBalance>,
    val settleUp: List<SettleUp>,
    val totalMinor: Long,
) {
    companion object {
        fun from(o: JsonObject) = CoBalances(
            currency = o.str("currency") ?: "USD",
            splitMethod = o.str("split_method"),
            members = o.objs("members").mapNotNull { m ->
                val id = m.str("member_id") ?: return@mapNotNull null
                MemberBalance(id, m.str("display_name") ?: "—", m.int("share_bp") ?: 0, m.long("paid_minor") ?: 0, m.long("owed_minor") ?: 0, m.long("net_minor") ?: 0)
            },
            settleUp = o.objs("settle_up").map { SettleUp(it.str("from_name") ?: "—", it.str("to_name") ?: "—", it.long("amount_minor") ?: 0) },
            totalMinor = o.long("total_minor") ?: 0,
        )
    }
}
