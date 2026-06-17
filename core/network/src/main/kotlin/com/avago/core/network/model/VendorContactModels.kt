package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class VendorContactResponse(
    val contact_id: String,
    val vendor_id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val title: String? = null,
    val is_primary: Boolean = false,
)

@Serializable
data class CreateVendorContactRequest(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val title: String? = null,
    val is_primary: Boolean = false,
)

@Serializable
data class UpdateVendorContactRequest(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val title: String? = null,
    val is_primary: Boolean? = null,
)

@Serializable
data class VendorContactsResponse(
    val contacts: List<VendorContactResponse>,
)
