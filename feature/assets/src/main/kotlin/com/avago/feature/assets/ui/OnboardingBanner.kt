package com.avago.feature.assets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.feature.assets.R

/**
 * Dismissible first-run experience banner shown at the top of [AssetListScreen]
 * only when the user has no assets and has not previously dismissed it.
 *
 * The banner slides in from the top via [AnimatedVisibility] and persists the
 * dismissed state to DataStore via [OnboardingViewModel].
 */
@Composable
fun OnboardingBanner(
    onAddAsset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }),
        modifier = modifier,
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    // Gradient background: primary → primaryContainer left-to-right
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryColor, primaryContainerColor),
                        ),
                    )
                    drawContent()
                },
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.onboarding_welcome_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.87f),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onAddAsset) {
                        Text(stringResource(R.string.onboarding_add_asset))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.onboarding_dismiss),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}
