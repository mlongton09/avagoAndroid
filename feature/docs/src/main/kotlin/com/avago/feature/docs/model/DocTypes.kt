package com.avago.feature.docs.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.avago.core.data.domain.DocumentKind
import com.avago.feature.docs.R

data class DocTypeItem(
    val kind: DocumentKind,
    val labelResId: Int,
    val icon: ImageVector,
)

object DocTypes {
    val all: List<DocTypeItem> = listOf(
        DocTypeItem(DocumentKind.RECEIPT, R.string.docs_kind_receipt, Icons.Default.Receipt),
        DocTypeItem(DocumentKind.INVOICE, R.string.docs_kind_invoice, Icons.Default.RequestQuote),
        DocTypeItem(DocumentKind.WARRANTY, R.string.docs_kind_warranty, Icons.Default.Shield),
        DocTypeItem(DocumentKind.HOWTO, R.string.docs_kind_howto, Icons.Default.Build),
        DocTypeItem(DocumentKind.OTHER, R.string.docs_kind_other, Icons.Default.Description),
    )

    fun forKind(kind: DocumentKind): DocTypeItem = all.first { it.kind == kind }
}
