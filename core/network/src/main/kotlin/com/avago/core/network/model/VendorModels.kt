package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class VendorResponse(
    val vendor_id: String,
    val account_id: String,
    val name: String,
    val contact_name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val vendor_code: String? = null,
    val website: String? = null,
    val preferred: Boolean = false,
    val active: Boolean = true,
    val server_version: Int = 1,
    val seq: Long = 0,
    val created_at: String = "",
    val updated_at: String = "",
    val deleted_at: String? = null,
)

@Serializable
data class PatchVendorRequest(
    val name: String? = null,
    val contact_name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val preferred: Boolean? = null,
    val active: Boolean? = null,
    val rating: Int? = null,
)
