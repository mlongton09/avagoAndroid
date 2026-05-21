package com.avago.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.core.network.model.UserResponse

/**
 * Small autocomplete popup that appears above the composer when the user types @.
 * Filters [members] by [query] and calls [onSelect] with the chosen user.
 */
@Composable
fun MentionAutocomplete(
    query: String,
    members: List<UserResponse>,
    onSelect: (UserResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = members.filter { user ->
        query.isBlank() ||
            (user.display_name?.contains(query, ignoreCase = true) == true) ||
            (user.email?.contains(query, ignoreCase = true) == true)
    }.take(6)

    if (filtered.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(filtered, key = { it.user_id }) { user ->
                Text(
                    text = user.display_name ?: user.email ?: user.user_id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(user) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
