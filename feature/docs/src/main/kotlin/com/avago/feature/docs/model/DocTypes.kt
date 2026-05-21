package com.avago.feature.docs.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.avago.feature.docs.R

data class DocTypeItem(
    val key: String,
    val labelResId: Int,
    val icon: ImageVector,
)

object DocTypes {
    val receipt = DocTypeItem("receipt", R.string.docs_filter_receipt, Icons.Default.Receipt)
    val invoice = DocTypeItem("invoice", R.string.docs_filter_invoice, Icons.Default.RequestQuote)
    val warranty = DocTypeItem("warranty", R.string.docs_filter_warranty, Icons.Default.Shield)
    val howto = DocTypeItem("howto", R.string.docs_filter_howto, Icons.Default.Build)
    val other = DocTypeItem("other", R.string.docs_filter_other, Icons.Default.Description)

    val all: List<DocTypeItem> = listOf(receipt, invoice, warranty, howto, other)

    fun fromKey(key: String): DocTypeItem = all.firstOrNull { it.key == key } ?: other

    fun iconFor(key: String?): ImageVector = fromKey(key ?: "other").icon

    fun labelResIdFor(key: String?): Int = fromKey(key ?: "other").labelResId
}
