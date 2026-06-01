package com.avago.core.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.ScoutHistoryEntity
import com.avago.core.data.db.entity.ScoutPendingEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun enqueue(transcript: String, screenContext: ScreenContext, skillHint: String? = null): String {
        val accountId = identityManager.activeAccountId.value ?: error("No active account")
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val db = databaseFactory.get(accountId)
        db.scoutPendingDao().upsert(
            ScoutPendingEntity(
                id = id,
                accountId = accountId,
                transcript = transcript,
                screenContext = json.encodeToString(screenContext),
                skillHint = skillHint,
                createdAt = now,
                updatedAt = now,
            )
        )
        db.scoutHistoryDao().upsert(
            ScoutHistoryEntity(
                id = id,
                accountId = accountId,
                transcript = transcript,
                skillName = skillHint,
                targetScreen = null,
                status = "queued",
                createdAt = now,
            )
        )
        scheduleDrain()
        return id
    }

    suspend fun recordHistory(
        transcript: String,
        response: ScoutResponse?,
        status: String = "completed",
        id: String = UUID.randomUUID().toString(),
    ) {
        val accountId = identityManager.activeAccountId.value ?: return
        databaseFactory.get(accountId).scoutHistoryDao().upsert(
            ScoutHistoryEntity(
                id = id,
                accountId = accountId,
                transcript = transcript,
                skillName = response?.skillName,
                targetScreen = response?.targetScreen,
                status = status,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    fun observeHistory(): Flow<List<ScoutHistoryEntity>> = flow {
        val accountId = identityManager.activeAccountId.value
        if (accountId == null) {
            emit(emptyList())
        } else {
            databaseFactory.get(accountId).scoutHistoryDao().observeRecent(accountId).collect { emit(it) }
        }
    }

    fun scheduleDrain() {
        val request = OneTimeWorkRequestBuilder<ScoutDrainWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("scout-pending-drain", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun drainPending(): Boolean {
        val accountId = identityManager.activeAccountId.value ?: return true
        val db = databaseFactory.get(accountId)
        val pending = db.scoutPendingDao().pendingList()
        for (row in pending) {
            val ctx = runCatching { json.decodeFromString<ScreenContext>(row.screenContext) }.getOrNull()
            when (
                val result = serviceClient.scoutQuery(
                    accountId = row.accountId,
                    query = row.transcript,
                    currentScreen = ctx?.screen,
                )
            ) {
                is NetworkResult.Success -> {
                    val response = result.data.toDomain()
                    db.scoutPendingDao().delete(row.id)
                    db.scoutHistoryDao().upsert(
                        ScoutHistoryEntity(
                            id = row.id,
                            accountId = row.accountId,
                            transcript = row.transcript,
                            skillName = response.skillName,
                            targetScreen = response.targetScreen,
                            status = "completed",
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
                is NetworkResult.Unauthorized -> return false
                is NetworkResult.Error -> {
                    db.scoutPendingDao().markError(row.id, result.message, System.currentTimeMillis())
                    Timber.w("Scout drain failed: HTTP ${result.code} ${result.message}")
                    return false
                }
            }
        }
        return true
    }
}
