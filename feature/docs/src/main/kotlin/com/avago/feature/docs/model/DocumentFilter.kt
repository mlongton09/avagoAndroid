package com.avago.feature.docs.model

import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentKind

/** The document list's kind chip and search text (title, issuer, notes or file name). */
data class DocumentFilter(val kind: DocumentKind? = null, val query: String = "") {
    fun apply(documents: List<Document>): List<Document> {
        val q = query.trim()
        return documents.filter { d ->
            (kind == null || d.kind == kind) &&
                (q.isEmpty() || listOfNotNull(d.title, d.issuerName, d.notes, d.file?.name).any { it.contains(q, ignoreCase = true) })
        }
    }
}
