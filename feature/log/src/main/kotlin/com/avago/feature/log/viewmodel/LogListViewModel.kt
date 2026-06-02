package com.avago.feature.log.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.repository.AssetRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.sync.PhotoDownloader
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogListViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val syncEngine: SyncEngine,
    private val photoDownloader: PhotoDownloader,
    private val userPrefsRepository: UserPreferencesRepository,
    private val assetRepository: AssetRepository,
) : ViewModel() {

    val assetId = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val currencyCode: StateFlow<String> = userPrefsRepository.currencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    private val accountId: StateFlow<String?> = identity.activeAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = identity.getActiveAccountId(),
        )

    val logs: StateFlow<List<LogEntity>> = combine(
        identity.activeAccountId.filterNotNull(),
        assetId,
        _categoryFilter,
    ) { accountId, assetIdVal, filter ->
        Triple(accountId, assetIdVal, filter)
    }.flatMapLatest { (accountId, assetIdVal, filter) ->
        try {
            val db = dbFactory.get(accountId)
            db.logDao().observeAll(accountId).map { list ->
                list.filter { log ->
                    (assetIdVal == null || log.assetId == assetIdVal) &&
                        (filter == null || log.category == filter)
                }.sortedByDescending { it.entryDate }
            }
        } catch (e: Exception) {
            Timber.e(e, "[LogListViewModel] Failed to observe logs")
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * All distinct categories observed in the current log set, for building filter pills.
     */
    val availableCategories: StateFlow<List<String>> = logs.map { list ->
        list.mapNotNull { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryFilter: StateFlow<String?> = _categoryFilter

    // ─── Asset header state ─────────────────────────────────────────────────
    // Mirrors iOS AssetDetailHeaderView.configure(withAsset:photos:latestEntry:entryCount:).

    /** The current asset, or null when viewing all logs (assetId == null). */
    val asset: StateFlow<AssetEntity?> = combine(
        accountId,
        assetId,
    ) { acctId, aId -> acctId to aId }
        .flatMapLatest { (acctId, aId) ->
            if (acctId == null || aId == null) flowOf(null)
            else try {
                assetRepository.observeAssets(acctId).map { list ->
                    list.firstOrNull { it.assetId == aId }
                }
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] Error observing asset $aId")
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Photos for the current asset, ordered by sort_order. Empty when assetId is null. */
    val photos: StateFlow<List<PhotoEntity>> = combine(
        accountId,
        assetId,
    ) { acctId, aId -> acctId to aId }
        .flatMapLatest { (acctId, aId) ->
            if (acctId == null || aId == null) flowOf(emptyList())
            else try {
                dbFactory.get(acctId).photoDao().observeByEntity(aId, "asset")
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] Error observing photos for $aId")
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Most-recent log entryDate for the current asset, or null. */
    val lastServiceDate: StateFlow<Long?> = logs
        .map { it.maxOfOrNull { entry -> entry.entryDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Total log count for the current asset (after assetId filter, before category filter). */
    val entryCount: StateFlow<Int> = combine(
        identity.activeAccountId.filterNotNull(),
        assetId,
    ) { acctId, aId -> acctId to aId }
        .flatMapLatest { (acctId, aId) ->
            if (aId == null) flowOf(0)
            else try {
                dbFactory.get(acctId).logDao().observeAll(acctId)
                    .map { list -> list.count { it.assetId == aId } }
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] Error counting entries")
                flowOf(0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setAssetId(id: String?) {
        val prior = assetId.value
        assetId.value = id
        // iOS parity: opening the asset detail / log screen triggers
        // LogPictureDAO.downloadIfNeeded for each attached photo. On Android
        // we batch-fetch presigned download URLs once when the screen opens
        // so server-side photos render in the carousel even on devices that
        // didn't originally capture them. The generic /sync pull only brings
        // down storage_key — short-lived presigned URLs have to be re-resolved
        // here per-session.
        if (id != null && id != prior) {
            viewModelScope.launch {
                val acctId = identity.activeAccountId.value ?: return@launch
                try {
                    photoDownloader.refreshForEntity(acctId, id)
                } catch (e: Exception) {
                    Timber.e(e, "[LogListViewModel] PhotoDownloader.refreshForEntity failed for $id")
                }
            }
        }
    }

    fun setFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] Refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Persist a new asset photo from either a gallery URI or a camera-capture file URI.
     *
     * Mirrors the iOS path: copy the source bytes into an app-private file under
     * `filesDir/asset_photos/`, insert a `PhotoEntity` with `localPath` set (and a stub
     * `downloadUrl` so the header can render immediately), then trigger
     * `SyncEngine.pushIfNeeded()`. The shared `PhotoUploader.sweep` will pick it up via
     * `PhotoDao.pendingUpload`, fetch a presigned URL, upload the binary, and clear
     * `localPath` on success.
     */
    fun addAssetPhoto(uri: Uri) {
        val aId = assetId.value ?: return
        viewModelScope.launch {
            val acctId = identity.activeAccountId.value ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    val resolver: ContentResolver = appContext.contentResolver
                    val dir = File(appContext.filesDir, "asset_photos").apply { mkdirs() }
                    val photoId = UUID.randomUUID().toString()
                    val outFile = File(dir, "$photoId.jpg")
                    resolver.openInputStream(uri).use { input ->
                        FileOutputStream(outFile).use { out ->
                            if (input == null) {
                                Timber.w("[LogListViewModel] Could not open URI $uri")
                                return@withContext
                            }
                            input.copyTo(out)
                        }
                    }
                    val now = System.currentTimeMillis()
                    val db = dbFactory.get(acctId)
                    val currentMaxOrder = db.photoDao().observeByEntity(aId, "asset")
                        // Use synchronous max via the existing list snapshot
                        .let { _ -> 0L }
                    val entity = PhotoEntity(
                        photoId = photoId,
                        entityId = aId,
                        entityType = "asset",
                        accountId = acctId,
                        storageKey = null,
                        downloadUrl = Uri.fromFile(outFile).toString(),
                        sortOrder = (photos.value.maxOfOrNull { it.sortOrder } ?: -1L) + 1L,
                        isPrimary = photos.value.isEmpty(),
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0L,
                        localPath = outFile.absolutePath,
                    )
                    db.photoDao().upsert(entity)
                    Timber.d("[LogListViewModel] Added asset photo $photoId → ${outFile.absolutePath}")
                }
                syncEngine.pushIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] addAssetPhoto failed for $uri")
            }
        }
    }

    /** Soft-deletes a photo and triggers a sync push. */
    fun deleteAssetPhoto(photoId: String) {
        viewModelScope.launch {
            val acctId = identity.activeAccountId.value ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    dbFactory.get(acctId).photoDao().softDelete(photoId, now)
                }
                syncEngine.pushIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] deleteAssetPhoto failed for $photoId")
            }
        }
    }

    /** Sets the given photo as primary (cover) and demotes others. */
    fun setCoverPhoto(photoId: String) {
        viewModelScope.launch {
            val acctId = identity.activeAccountId.value ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    val db = dbFactory.get(acctId)
                    val now = System.currentTimeMillis()
                    val current = photos.value
                    current.forEach { p ->
                        val shouldBePrimary = p.photoId == photoId
                        if (p.isPrimary != shouldBePrimary) {
                            db.photoDao().upsert(p.copy(isPrimary = shouldBePrimary, updatedAt = now))
                        }
                    }
                }
                syncEngine.pushIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "[LogListViewModel] setCoverPhoto failed for $photoId")
            }
        }
    }
}

