package com.avago.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Final output model of the scan-to-record async pipeline.
 *
 * Produced when the user scans a physical document (receipt, invoice, service record)
 * and the OCR pipeline classifies the maintenance categories and formats the output.
 *
 * Mirrors iOS MaintenanceRecord.swift.
 */
@Serializable
data class MaintenanceRecord(
    val id: String,

    /** UTC epoch ms of when the physical document was scanned. */
    @SerialName("scan_date")
    val scanDateMs: Long,

    /** Verbatim OCR output before any cleaning or classification. */
    @SerialName("raw_text")
    val rawText: String,

    /** Classified maintenance categories extracted from [rawText]. */
    val categories: List<String>,

    /**
     * Pipeline-generated Markdown rendition of the record, suitable for display.
     */
    @SerialName("formatted_markdown")
    val formattedMarkdown: String,

    /** Optional JPEG/PNG bytes of the original scanned document. Null for manual entries. */
    @SerialName("image_data")
    val imageData: ByteArray? = null,
)
