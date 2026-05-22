package com.avago.core.network.model

import kotlinx.serialization.Serializable

/** Wire models for the Doc OCR endpoint: POST /accounts/{accountId}/docs/ocr-extract */

@Serializable
data class DocOcrResponse(
    val vendor: String? = null,
    val total: Double? = null,
    val date: String? = null,
    val end_date: String? = null,
    val terms: String? = null,
    val line_items: List<DocOcrLineItem> = emptyList(),
)

@Serializable
data class DocOcrLineItem(
    val description: String? = null,
    val amount: Double? = null,
    val quantity: Double? = null,
)
