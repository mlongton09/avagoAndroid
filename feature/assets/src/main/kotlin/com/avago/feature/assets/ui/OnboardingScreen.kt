package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avago.feature.assets.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val titleResId: Int,
    val bodyResId: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Default.DirectionsCar,
        titleResId = R.string.onboarding_page1_title,
        bodyResId = R.string.onboarding_page1_body,
    ),
    OnboardingPage(
        icon = Icons.Default.Build,
        titleResId = R.string.onboarding_page2_title,
        bodyResId = R.string.onboarding_page2_body,
    ),
    OnboardingPage(
        icon = Icons.Default.Group,
        titleResId = R.string.onboarding_page3_title,
        bodyResId = R.string.onboarding_page3_body,
    ),
)

/**
 * Full-screen paged onboarding shown once on first sign-in, before the main nav.
 *
 * When the user taps "Get Started" on the last page or "Skip" on any page,
 * [onComplete] is invoked; the caller is responsible for marking onboarding
 * complete in DataStore (via [OnboardingViewModel.completeOnboarding]) and
 * navigating to the main content.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Skip button in top-right
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(onClick = onComplete) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageData = onboardingPages[page]
                OnboardingPageContent(
                    icon = pageData.icon,
                    title = stringResource(pageData.titleResId),
                    body = stringResource(pageData.bodyResId),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Page indicator dots
            PageIndicator(
                pageCount = onboardingPages.size,
                currentPage = pagerState.currentPage,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Next / Get Started button
            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isLastPage) stringResource(R.string.onboarding_get_started)
                    else stringResource(R.string.onboarding_next)
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(if (isSelected) 10.dp else 8.dp)
                    .clip(CircleShape),
            ) {}
        }
    }
}
