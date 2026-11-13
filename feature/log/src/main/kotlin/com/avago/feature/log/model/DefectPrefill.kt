package com.avago.feature.log.model

/**
 * Port of web `work-orders/create/prefill.ts` `defectPrefill`: maps one or more defects of the
 * same asset to the work-order create prefill (title, description, priority) plus the
 * `attributes.source = { kind: "defect", defect_ids, inspection_log_id }` link.
 */
object DefectPrefill {

    data class Defect(
        val id: String?,
        val label: String,
        val sectionTitle: String? = null,
        val cornerLabel: String? = null,
        val severity: String? = null,
        val bucket: InspectionV2.Bucket? = null,
        val note: String? = null,
        val location: String? = null,
        val sourceLogId: String? = null,
    )

    data class Result(
        val title: String,
        val description: String,
        val priority: String,
        val defectIds: List<String>,
        val inspectionLogId: String?,
    )

    private val UUID_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val PRIORITIES = listOf("critical", "high", "medium", "low")

    fun isUuid(v: String?): Boolean = v != null && UUID_RE.matches(v)

    /** Out of service -> critical, major or NR bucket -> high, otherwise medium. */
    fun severityToPriority(severity: String?, bucket: InspectionV2.Bucket?): String = when {
        severity == InspectionV2.SEV_OUT_OF_SERVICE -> "critical"
        severity == InspectionV2.SEV_MAJOR || bucket == InspectionV2.Bucket.NR -> "high"
        else -> "medium"
    }

    fun build(
        defects: List<Defect>,
        severityLabel: (String) -> String = { it },
        locationLabel: (String) -> String = { it },
        locationWord: String = "Location",
        noteWord: String = "Note",
    ): Result? {
        if (defects.isEmpty()) return null
        val first = defects.first()
        val title = if (defects.size == 1) {
            listOfNotNull(first.label, first.cornerLabel?.takeIf { it.isNotBlank() }).joinToString(" — ")
        } else {
            "${first.label} (+${defects.size - 1})"
        }
        val lines = defects.map { d ->
            val parts = mutableListOf<String>()
            val head = listOfNotNull(d.sectionTitle, d.label, d.cornerLabel).filter { it.isNotBlank() }.joinToString(" · ")
            parts += d.severity?.takeIf { it.isNotBlank() }?.let { "$head [${severityLabel(it)}]" } ?: head
            d.location?.takeIf { it.isNotBlank() }?.let { parts += "$locationWord: ${locationLabel(it)}" }
            d.note?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "$noteWord: $it" }
            if (defects.size > 1) "- ${parts.joinToString("\n  ")}" else parts.joinToString("\n")
        }
        val priority = defects.map { severityToPriority(it.severity, it.bucket) }.minBy { PRIORITIES.indexOf(it) }
        return Result(
            title = title.take(200),
            description = lines.joinToString("\n").take(5000),
            priority = priority,
            defectIds = defects.mapNotNull { it.id }.filter(::isUuid),
            inspectionLogId = defects.map { it.sourceLogId }.firstOrNull(::isUuid),
        )
    }

    fun of(d: InspectionV2.OpenDefect) = Defect(
        id = d.id, label = d.label, sectionTitle = d.sectionTitle, cornerLabel = d.cornerLabel,
        severity = d.severity, bucket = d.bucket, note = d.note, location = d.location, sourceLogId = d.sourceLogId,
    )

    /** A defect of the inspection being edited; [logId] links the WO to it once it exists on the server. */
    fun of(d: InspectionV2.EnvelopeDefect, logId: String?) = Defect(
        id = null, label = d.label, sectionTitle = d.sectionTitle, cornerLabel = d.cornerLabel,
        severity = d.severity, bucket = d.bucket, note = d.note, location = d.location, sourceLogId = logId,
    )
}
