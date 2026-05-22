package com.avago.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import java.util.Calendar

private data class Quote(
    val text: String,
    val attribution: String? = null,
)

private val quotes = listOf(
    Quote(
        text = "The bitterness of poor quality remains long after the sweetness of low price is forgotten.",
        attribution = "Benjamin Franklin",
    ),
    Quote(text = "Preventive maintenance is not an expense, it's an investment."),
    Quote(text = "A stitch in time saves nine."),
    Quote(
        text = "The best time to fix a roof is when the sun is shining.",
        attribution = "John F. Kennedy",
    ),
    Quote(
        text = "An ounce of prevention is worth a pound of cure.",
        attribution = "Benjamin Franklin",
    ),
    Quote(text = "Take care of your equipment and it will take care of you."),
    Quote(text = "Reliability is the foundation of trust."),
    Quote(text = "Small maintenance today prevents big repairs tomorrow."),
    Quote(text = "The chain is only as strong as its weakest link."),
    Quote(text = "Neglect makes everything worse."),
)

/**
 * Displays a random motivational/maintenance quote, selected deterministically
 * by the current date so it is consistent throughout the day.
 *
 * Animates in with [fadeIn] on first composition. Intended as a list footer
 * in screens that have assets and where onboarding is dismissed.
 */
@Composable
fun QuoteBanner(
    modifier: Modifier = Modifier,
) {
    val quote by remember {
        val today = Calendar.getInstance()
        // Build a seed from year + day-of-year so the quote changes daily.
        val seed = today.get(Calendar.YEAR) * 1000 + today.get(Calendar.DAY_OF_YEAR)
        val index = seed % quotes.size
        mutableStateOf(quotes[index])
    }

    val visible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        modifier = modifier,
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                VerticalDivider(
                    thickness = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(
                        // Approximate height: body text + optional attribution
                        if (quote.attribution != null) 52.dp else 36.dp
                    ),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "“${quote.text}”",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    quote.attribution?.let { attribution ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "— $attribution",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}
