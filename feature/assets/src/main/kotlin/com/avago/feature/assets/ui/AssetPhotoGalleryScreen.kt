package com.avago.feature.assets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.feature.assets.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class AssetPhotoGalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val assetId: String = checkNotNull(savedStateHandle["assetId"]) {
        "assetId is required in SavedStateHandle for AssetPhotoGalleryViewModel"
    }

    private val accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = identityManager.getActiveAccountId(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = accountId
        .flatMapLatest { acctId ->
            if (acctId == null) flowOf(emptyList())
            else {
                try {
                    dbFactory.get(acctId).photoDao().observeByEntity(assetId, "asset")
                        .catch { e ->
                            Timber.e(e, "[AssetPhotoGalleryViewModel] Error loading photos for $assetId")
                            emit(emptyList())
                        }
                } catch (e: Exception) {
                    Timber.e(e, "[AssetPhotoGalleryViewModel] Could not get photoDao for $acctId")
                    flowOf(emptyList())
                }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetPhotoGalleryViewModel] Photo flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPhotoGalleryScreen(
    initialIndex: Int = 0,
    onBack: () -> Unit,
    viewModel: AssetPhotoGalleryViewModel = hiltViewModel(),
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val photoUrls = photos.mapNotNull { it.downloadUrl }

    var chromeVisible by remember { mutableStateOf(true) }

    val pageCount = photoUrls.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (photoUrls.isEmpty()) {
            // Nothing to show yet — could be loading or genuinely empty
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.gallery_no_photos),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { chromeVisible = !chromeVisible },
            ) { page ->
                AsyncImage(
                    model = photoUrls[page],
                    contentDescription = stringResource(R.string.gallery_photo_content_description, page + 1, photoUrls.size),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Page indicator dots
            if (photoUrls.size > 1) {
                PageIndicator(
                    pageCount = photoUrls.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )
            }
        }

        // TopAppBar overlay (toggleable)
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            CenterAlignedTopAppBar(
                title = {
                    if (photoUrls.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.gallery_page_counter,
                                pagerState.currentPage + 1,
                                photoUrls.size,
                            ),
                            color = Color.White,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Page indicator dots
// ---------------------------------------------------------------------------

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                    ),
            )
        }
    }
}
