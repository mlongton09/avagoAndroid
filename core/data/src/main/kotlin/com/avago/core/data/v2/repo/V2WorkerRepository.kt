package com.avago.core.data.v2.repo

import com.avago.core.data.domain.Category
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.ReferenceRepository
import com.avago.core.data.domain.ReferenceValue
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerDraft
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.commands.MaintCommands
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2WorkerRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : WorkerRepository {

    override fun observeWorkers(): Flow<List<Worker>> =
        store.observe(setOf("workers", "work_orders"), emptyList()) { conn -> V2MaintRows.workers(conn).filter { it.status == "active" } }

    override fun observeMe(): Flow<Worker?> = store.observe(setOf("workers", "work_orders"), null) { conn ->
        val me = commands.currentUserId() ?: return@observe null
        V2MaintRows.workers(conn).firstOrNull { it.userId == me }
    }

    override suspend fun worker(id: String): Worker? = store.read(null) { conn -> V2MaintRows.workers(conn).firstOrNull { it.id == id } }

    override suspend fun update(id: String, draft: WorkerDraft) {
        val stored = worker(id) ?: throw RecordNotFoundException("worker", id)
        MaintCommands.updateWorker(stored, draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }
}

@Singleton
class V2ReferenceRepository @Inject constructor(private val store: V2StoreAccess) : ReferenceRepository {

    override fun observeValues(kind: String): Flow<List<ReferenceValue>> =
        store.observe(setOf("reference_values"), emptyList()) { conn -> V2MaintRows.referenceValues(conn, kind) }

    override fun observeCategories(kind: String): Flow<List<Category>> =
        store.observe(setOf("categories"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "categories", "kind = ?", listOf(kind)) { it["kind"] == kind }
                .map { r ->
                    Category(
                        id = r.str("id"),
                        kind = r.str("kind"),
                        name = r.str("name"),
                        parentId = r.strOrNull("parent_id"),
                        color = r.strOrNull("color"),
                        icon = r.strOrNull("icon"),
                        defaultPriority = r.strOrNull("default_priority"),
                        systemCode = r.strOrNull("system_code"),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Category::name))
        }
}
