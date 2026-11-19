package com.avago.feature.rentals.domain

import com.avago.core.data.domain.RentalSignature
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.Base64

/**
 * Wave 2 / G1 e-signature (docs/rentals-api-wave2-esign-scan-meter.md §1): the order's signature
 * requests and the in-person signers a device may capture.
 */
object Esign {

    /** Largest PNG the server accepts (`signature_image_too_large` above). */
    const val MAX_PNG_BYTES = 512 * 1024

    const val PERMISSION_REQUEST = "rental.signature_request"
    const val PERMISSION_OVERRIDE = "rental.override_signature"

    data class Signer(
        val id: String,
        val order: Int,
        val name: String,
        val role: String,
        val method: String,
        val status: String,
        val signedAt: Instant?,
    ) {
        val isDone: Boolean get() = status == "signed" || status == "declined" || status == STATUS_SIGNED_LOCAL
        val isInPerson: Boolean get() = method == "in_person"
    }

    data class Request(
        val id: String,
        val status: String,
        val purpose: String,
        val documentNumber: String?,
        val consentText: String?,
        val consentVersion: String?,
        val expiresAt: Instant?,
        val signers: List<Signer>,
    ) {
        val isOpen: Boolean get() = status == "pending"

        /** Lowest unsigned signer order (signers of the same order may sign in parallel). */
        val activeOrder: Int? get() = signers.filter { !it.isDone }.minOfOrNull { it.order }

        /** In-person signers the device can capture now. */
        val capturable: List<Signer>
            get() = if (!isOpen) emptyList() else signers.filter { it.isInPerson && !it.isDone && it.order == activeOrder }
    }

    data class Status(
        val agreementId: String?,
        val agreementSigned: Boolean,
        val requireBeforeCheckout: Boolean,
        val requests: List<Request>,
    ) {
        /** The newest open request with an in-person signer to capture, if any. */
        val signable: Request? get() = requests.firstOrNull { it.capturable.isNotEmpty() }

        /** Check-out will be refused (409 `signature_required`) unless signed or overridden. */
        val blocksCheckout: Boolean get() = requireBeforeCheckout && !agreementSigned
    }

    fun parse(o: JsonObject?): Status? {
        o ?: return null
        val requests = o.objs("items").mapNotNull { r ->
            val id = r.str("id") ?: return@mapNotNull null
            Request(
                id = id,
                status = r.str("status") ?: "pending",
                purpose = r.str("purpose") ?: "agreement",
                documentNumber = r.str("document_number"),
                consentText = r.str("consent_text"),
                consentVersion = r.str("consent_text_version"),
                expiresAt = r.instant("expires_at"),
                signers = r.objs("signers").mapNotNull { s ->
                    Signer(
                        id = s.str("id") ?: return@mapNotNull null,
                        order = s.long("signer_order")?.toInt() ?: 1,
                        name = s.str("signer_name") ?: "",
                        role = s.str("signer_role") ?: "customer",
                        method = s.str("method") ?: "in_person",
                        status = s.str("status") ?: "pending",
                        signedAt = s.instant("signed_at"),
                    )
                }.sortedBy { it.order },
            )
        }
        return Status(
            agreementId = o.str("agreement_id"),
            agreementSigned = o.bool("agreement_signed") == true,
            requireBeforeCheckout = o.bool("require_signed_agreement_before_checkout") == true,
            requests = requests,
        )
    }

    /** Bytes of a PNG given as plain base64 or a `data:image/png;base64,` URL (null when unreadable). */
    fun pngBytes(dataUrlOrBase64: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(dataUrlOrBase64.substringAfter("base64,", dataUrlOrBase64).trim()) }.getOrNull()

    sealed interface Capture {
        data object Ok : Capture
        data object ConsentRequired : Capture
        data object SignatureRequired : Capture
        data object ImageTooLarge : Capture
    }

    /** Client-side checks mirroring the server's 400 codes, so a bad capture is never sent. */
    fun validate(pngDataUrl: String?, typedName: String?, consent: Boolean): Capture = when {
        !consent -> Capture.ConsentRequired
        pngDataUrl.isNullOrBlank() && typedName.isNullOrBlank() -> Capture.SignatureRequired
        pngDataUrl != null && (pngBytes(pngDataUrl)?.size ?: Int.MAX_VALUE) > MAX_PNG_BYTES -> Capture.ImageTooLarge
        else -> Capture.Ok
    }

    /**
     * Signatures captured on this device that the server answer does not show yet: their signers
     * count as signed, and an agreement request opened and signed here signs the agreement.
     */
    fun withLocal(status: Status, signatures: List<RentalSignature>): Status {
        val signed = signatures.mapNotNull { it.requestId }.toSet()
        if (signed.isEmpty()) return status
        val known = status.requests.map { it.id }.toSet()
        return status.copy(
            agreementSigned = status.agreementSigned || signed.any { it !in known },
            requests = status.requests.map { r ->
                if (r.id !in signed) r
                else r.copy(signers = r.signers.map { s -> if (s.isInPerson && !s.isDone && s.order == r.activeOrder) s.copy(status = STATUS_SIGNED_LOCAL) else s })
            },
        )
    }

    /** Signer status while a capture made on this device is not confirmed. */
    const val STATUS_SIGNED_LOCAL = "signed_local"
}
