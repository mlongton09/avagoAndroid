package com.avago.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import com.avago.core.data.db.entity.ChatAccountRosterEntity

// Special pseudo-member handle tokens inserted literally into the text field.
private const val SPECIAL_ALL = "@all"
private const val SPECIAL_HERE = "@here"

/**
 * Small autocomplete popup that appears above the composer when the user types @.
 * Filters [members] by [query] and calls [onSelect] with the chosen user.
 *
 * @all  and @here are prepended as virtual entries when the query is blank or
 * matches the beginning of their handle.  Selecting them calls [onSelectSpecial]
 * with the literal string "@all" or "@here".
 */
@Composable
fun MentionAutocomplete(
    query: String,
    members: List<ChatAccountRosterEntity>,
    onSelect: (ChatAccountRosterEntity) -> Unit,
    onSelectSpecial: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Determine which special entries to show.
    val showAll = query.isBlank() || "all".startsWith(query.lowercase())
    val showHere = query.isBlank() || "here".startsWith(query.lowercase())

    val filtered = members.filter { user ->
        query.isBlank() ||
            (user.displayName?.contains(query, ignoreCase = true) == true) ||
            (user.email?.contains(query, ignoreCase = true) == true)
    }.take(6)

    val hasContent = showAll || showHere || filtered.isNotEmpty()
    if (!hasContent) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            // @all
            if (showAll) {
                item(key = "__special_all") {
                    SpecialMentionItem(
                        handle = SPECIAL_ALL,
                        description = "Everyone in the account",
                        onClick = { onSelectSpecial(SPECIAL_ALL) },
                    )
                }
            }
            // @here
            if (showHere) {
                item(key = "__special_here") {
                    SpecialMentionItem(
                        handle = SPECIAL_HERE,
                        description = "Online now",
                        onClick = { onSelectSpecial(SPECIAL_HERE) },
                    )
                }
            }
            // Regular members
            items(filtered, key = { it.userId }) { user ->
                Text(
                    text = user.displayName ?: user.email ?: user.userId,
                    // iOS MentionAutocompletePopover: bodyFont (17 reg) → bodyLarge.
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(user) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SpecialMentionItem(
    handle: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "🔔 $handle",
            // iOS MentionAutocompletePopover: bodyFont (17 reg) → bodyLarge.
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = description,
            // iOS detail uses smallFont (13 reg) → bodyMedium.
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
