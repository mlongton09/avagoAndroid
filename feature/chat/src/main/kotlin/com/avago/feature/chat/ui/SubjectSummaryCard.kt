package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Shown at the top of ThreadScreen when the thread has a subjectSummary JSON blob.
 * Displays the linked entity type, name, and status badge — mirroring iOS WOMarkerCell.
 */
@Composable
fun SubjectSummaryCard(
    subjectSummaryJson: String,
    modifier: Modifier = Modifier,
) {
    val summary: JsonObject = try {
        json.parseToJsonElement(subjectSummaryJson).jsonObject
    } catch (e: Exception) {
        return
    }

    val entityType = summary["entity_type"]?.jsonPrimitive?.content
        ?: summary["type"]?.jsonPrimitive?.content
        ?: "entity"
    val name = summary["name"]?.jsonPrimitive?.content
        ?: summary["title"]?.jsonPrimitive?.content
        ?: return
    val status = summary["status"]?.jsonPrimitive?.content

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entityType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (!status.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = status)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (containerColor, contentColor) = when (status.lowercase()) {
        "open", "active" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        "completed", "done", "closed" -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        "overdue", "failed" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercaseChar() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}
