package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTaxonomy
import com.avago.feature.assets.model.localizedLabel
import com.avago.feature.assets.model.text
import kotlinx.serialization.json.JsonObject

/** Shared by ordinary create and the native storefront wizard. Manual selection is always available. */
@Composable
fun AssetClassificationPicker(taxonomy: AssetTaxonomy?, value: JsonObject?, onSelect: (JsonObject) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var parent by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    val german = LocalConfiguration.current.locales[0].language == "de"
    val label = taxonomy?.leaves(value?.text("category_id").orEmpty())
        ?.find { it.text("id") == value?.text("subcategory_id") }?.localizedLabel(german)
    OutlinedButton(onClick = { open = true; parent = ""; search = "" }, enabled = taxonomy != null,
        modifier = Modifier.fillMaxWidth()) {
        Text(label ?: stringResource(R.string.classification_choose))
    }
    if (open && taxonomy != null) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.classification_choose)) },
        text = {
            Column {
                OutlinedTextField(
                    search, { search = it }, label = { Text(stringResource(R.string.classification_search)) },
                )
                if (parent.isNotEmpty()) TextButton(onClick = { parent = ""; search = "" }) {
                    Text(stringResource(R.string.classification_back))
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    val choices = if (parent.isEmpty()) taxonomy.categories else taxonomy.leaves(parent)
                    val matching = choices.filter { search.isBlank() || it.toString().contains(search, true) }
                    items(matching, key = { it.text("id") }) { entry ->
                        TextButton(onClick = {
                            if (parent.isEmpty()) { parent = entry.text("id"); search = "" }
                            else { onSelect(taxonomy.selection(parent, entry.text("id"))); open = false }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(entry.localizedLabel(german))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.classification_close)) }
        },
    )
}
