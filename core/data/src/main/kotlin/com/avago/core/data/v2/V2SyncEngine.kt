package com.avago.core.data.v2

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/** Outcome of one [V2SyncEngine.sync] run. */
sealed interface V2SyncResult {
    data class Synced(val mode: String, val w: String, val g: String, val pages: Int, val items: Int) : V2SyncResult

    /** `426 upgrade_required`: the app must show the update screen (M-02). */
    data object UpgradeRequired : V2SyncResult

    /** No HTTP response; the chain is kept, so the next run continues where this one stopped. */
    data class Offline(val message: String) : V2SyncResult

    /** `503 overloaded` / 5xx: try again after [retryAfterMs] (null: use the caller's backoff). */
    data class RetryLater(val retryAfterMs: Long?, val reason: String) : V2SyncResult

    /** `403 forbidden`: the user is no longer an active member. Drop the account locally. */
    data object Forbidden : V2SyncResult

    /** `401 device_revoked`: sign out and wipe local data. */
    data object DeviceRevoked : V2SyncResult

    /** `409 device_owned_elsewhere`: generate a new device id and try again. */
    data object DeviceIdRejected : V2SyncResult

    /** A page could not be applied (constraint/mapping failure) or the server broke the protocol. */
    data class Failed(val error: Throwable) : V2SyncResult
}

/** The server broke a sync-wire.md rule (contiguity, session id, missing token). */
class V2ProtocolException(message: String) : IllegalStateException(message)

/**
 * The schema-v2 pull side (sync-wire.md "Sync loop"): open → fetch pages until `is_final` → apply
 * **each page in one local transaction** and stage its `last_seq` → ack → commit cursors.
 *
 * - **Bootstrap** (first install, `bootstrap_required`, `scope_changed`, feed generation / scope
 *   epoch / projection schema mismatch): wipe the synced tables, open `{"mode":"bootstrap"}`.
 * - **Continuation**: a session that dies mid-way (`410 session_expired`, `400 invalid_token`,
 *   offline) is reopened with the same mode, the staged cursors and `continuation_of`.
 * - **Failures are surfaced, never skipped**: a page whose item cannot be written rolls back as a
 *   whole and the run returns [V2SyncResult.Failed] with the staged position unchanged. The one
 *   exception is a foreign key whose parent can only be on a later page of the same session (the
 *   server orders by `seq`, not by `apply_rank`): such a page is retried together with the next
 *   page(s) in one transaction; if the final page still fails, the store is marked
 *   `bootstrap_required`.
 */
class V2SyncEngine(
    private val api: V2Api,
    private val business: V2Database,
    private val chat: V2Database?,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val manifest: Map<String, V2Projection> = V2GeneratedSchema.projectionsByType,
    private val networkRetries: Int = 3,
) {
    private val mutex = Mutex()
    private val businessApplier = V2ProjectionApplier()
    private val chatApplier = V2ProjectionApplier()

    private data class Chain(
        val sessionId: String,
        val mode: String,
        val stagedAccount: String,
        val stagedGlobal: String,
        val accountGeneration: Long,
        val globalGeneration: Long,
        val scopeEpoch: String,
    )

    private data class StreamState(val cursor: Long, val feedGeneration: Long, val bootstrapState: String, val status: String)

    private data class LocalState(
        val account: StreamState,
        val global: StreamState,
        val scopeEpoch: Long,
        val chain: Chain?,
        val projectionVersions: String?,
    )

    private sealed interface Step {
        data object Restart : Step
        data class Done(val result: V2SyncResult) : Step
    }

    /**
     * One sync run. [serverProjectionVersions] is `capabilities.projections[].schema_version`; a
     * difference from the versions the store was bootstrapped with forces a bootstrap.
     */
    suspend fun sync(serverProjectionVersions: Map<String, Int>? = null): V2SyncResult = mutex.withLock {
        val desiredVersions = desiredVersions(serverProjectionVersions)
        repeat(MAX_RESTARTS) {
            when (val step = runSession(desiredVersions)) {
                is Step.Done -> return@withLock step.result
                Step.Restart -> Unit
            }
        }
        V2SyncResult.Failed(V2ProtocolException("sync restarted $MAX_RESTARTS times without completing"))
    }

    /**
     * Discards the local copy: the next [sync] wipes the synced tables and bootstraps again.
     * Queued commands and their overlays are kept and still sent.
     */
    suspend fun requestBootstrap() = mutex.withLock { resetToBootstrap(STATUS_BOOTSTRAP_REQUIRED) }

    /** Status column of `sync_state` (both streams carry the same value). */
    suspend fun status(): String = business.read { it.scalarText("SELECT status FROM sync_state WHERE stream = 'account'") ?: "ok" }

    // ---------------------------------------------------------------------------------------
    // Session

    private suspend fun runSession(desiredVersions: String): Step {
        registerDevice()?.let { return it }
        val state = loadState()
        val needsBootstrap = state.account.bootstrapState != BOOTSTRAP_COMPLETE ||
            state.global.bootstrapState != BOOTSTRAP_COMPLETE ||
            state.account.status in RESET_STATUSES || state.global.status in RESET_STATUSES ||
            state.projectionVersions != desiredVersions
        val chain = state.chain
        val mode: String
        val request = when {
            chain != null -> {
                mode = chain.mode
                V2OpenSessionRequest(
                    mode = chain.mode,
                    account = V2StreamPosition(chain.stagedAccount, chain.accountGeneration),
                    global = V2StreamPosition(chain.stagedGlobal, chain.globalGeneration),
                    scopeEpoch = chain.scopeEpoch,
                    continuationOf = chain.sessionId,
                )
            }
            needsBootstrap -> {
                mode = MODE_BOOTSTRAP
                V2OpenSessionRequest(mode = MODE_BOOTSTRAP)
            }
            else -> {
                mode = MODE_INCREMENTAL
                V2OpenSessionRequest(
                    mode = MODE_INCREMENTAL,
                    account = V2StreamPosition(state.account.cursor.toString(), state.account.feedGeneration),
                    global = V2StreamPosition(state.global.cursor.toString(), state.global.feedGeneration),
                    scopeEpoch = state.scopeEpoch.toString(),
                )
            }
        }

        val session = try {
            api.openSession(request)
        } catch (e: V2ApiException) {
            return openError(e, chain)
        }
        if (session.kind != mode) {
            return Step.Done(V2SyncResult.Failed(V2ProtocolException("asked for $mode, server opened ${session.kind}")))
        }

        // sync-wire.md "When the client must reset": never merge across a generation/epoch change.
        val expectedGenerations = when {
            chain != null -> Triple(chain.accountGeneration, chain.globalGeneration, chain.scopeEpoch)
            mode == MODE_INCREMENTAL -> Triple(state.account.feedGeneration, state.global.feedGeneration, state.scopeEpoch.toString())
            else -> null
        }
        if (expectedGenerations != null &&
            expectedGenerations != Triple(session.account.feedGeneration, session.global.feedGeneration, session.scopeEpoch)
        ) {
            Timber.w("v2 sync: feed generation / scope epoch changed; re-bootstrapping")
            closeQuietly(session, "aborted")
            resetToBootstrap(STATUS_BOOTSTRAP_REQUIRED)
            return Step.Restart
        }

        val newChain = Chain(
            sessionId = session.sessionId,
            mode = mode,
            stagedAccount = session.account.from,
            stagedGlobal = session.global.from,
            accountGeneration = session.account.feedGeneration,
            globalGeneration = session.global.feedGeneration,
            scopeEpoch = session.scopeEpoch,
        )
        if (mode == MODE_BOOTSTRAP && chain == null) {
            wipeForBootstrap(newChain)
        } else {
            business.write { tx -> putChain(tx.connection, newChain) }
        }
        return pull(session, newChain, desiredVersions)
    }

    private suspend fun pull(session: V2OpenSessionResponse, chain: Chain, desiredVersions: String): Step {
        val expected = mutableMapOf(V2Stream.ACCOUNT to chain.stagedAccount, V2Stream.GLOBAL to chain.stagedGlobal)
        val carried = mutableListOf<V2PageResponse>()
        var token = session.firstPageToken
        var pages = 0
        var items = 0
        while (token != null) {
            val page = when (val fetched = fetchPage(session, token)) {
                is PageFetch.Ok -> fetched.page
                is PageFetch.Stop -> return fetched.step
            }
            val stream = V2Stream.fromWire(page.stream)
            if (page.sessionId != session.sessionId || stream == null || page.after != expected[stream]) {
                val error = V2ProtocolException(
                    "page ${page.pageNo} (${page.stream}) after=${page.after}, expected ${stream?.let(expected::get)}",
                )
                Timber.e(error, "v2 sync: non-contiguous page")
                clearChain()
                return Step.Done(V2SyncResult.Failed(error))
            }
            expected[stream] = page.lastSeq
            verifyChecksum(page)

            try {
                applyChat(page)
            } catch (e: V2ApplyException) {
                Timber.e(e, "v2 sync: chat page ${page.pageNo} failed")
                return Step.Done(V2SyncResult.Failed(e))
            }
            carried += page
            try {
                applyBusiness(carried)
                carried.clear()
            } catch (e: V2ApplyException) {
                if (e.isForeignKey && !page.isFinal && carried.size < MAX_CARRIED_PAGES) {
                    Timber.i("v2 sync: page ${page.pageNo} waits for a parent on a later page (${e.message})")
                } else {
                    Timber.e(e, "v2 sync: page ${page.pageNo} failed; nothing from it was applied")
                    if (e.isForeignKey) {
                        // The session's final state is not referentially complete: start over.
                        resetToBootstrap(STATUS_BOOTSTRAP_REQUIRED)
                    }
                    return Step.Done(V2SyncResult.Failed(e))
                }
            }
            pages++
            items += page.items.size
            if (page.isFinal) break
            token = page.nextPageToken
                ?: return Step.Done(V2SyncResult.Failed(V2ProtocolException("page ${page.pageNo} is not final but has no next_page_token")))
        }
        return finish(session, chain, desiredVersions, pages, items)
    }

    private suspend fun finish(
        session: V2OpenSessionResponse,
        chain: Chain,
        desiredVersions: String,
        pages: Int,
        items: Int,
    ): Step {
        val w = session.account.w
        val g = session.global.g
        var upgrade = false
        try {
            api.ack(V2AckRequest(session.sessionId, w, g, bootstrapCompleted = chain.mode == MODE_BOOTSTRAP))
        } catch (e: V2ApiException.Network) {
            Timber.w("v2 sync: ack lost (${e.message}); the next open sends the same cursors")
        } catch (e: V2ApiException.UpgradeRequired) {
            upgrade = true
        } catch (e: V2ApiException.Status) {
            when {
                e.code == "scope_changed" || e.code == "bootstrap_required" -> {
                    resetToBootstrap(if (e.code == "scope_changed") STATUS_SCOPE_CHANGED else STATUS_BOOTSTRAP_REQUIRED)
                    return Step.Restart
                }
                e.status >= 500 || e.code == "overloaded" ->
                    Timber.w("v2 sync: ack not stored (${e.status}); the next open sends the same cursors")
                else -> return Step.Done(V2SyncResult.Failed(e))
            }
        } catch (e: V2ApiException) {
            return Step.Done(V2SyncResult.Failed(e))
        }
        commit(session, desiredVersions)
        if (upgrade) {
            markUpgradeRequired()
            return Step.Done(V2SyncResult.UpgradeRequired)
        }
        return Step.Done(V2SyncResult.Synced(chain.mode, w, g, pages, items))
    }

    private sealed interface PageFetch {
        data class Ok(val page: V2PageResponse) : PageFetch
        data class Stop(val step: Step) : PageFetch
    }

    /** Fetches a page; network errors and 5xx retry the same token (pages are repeatable). */
    private suspend fun fetchPage(session: V2OpenSessionResponse, token: String): PageFetch {
        var attempt = 0
        while (true) {
            try {
                return PageFetch.Ok(api.page(session.sessionId, session.sessionToken, token))
            } catch (e: V2ApiException.Network) {
                if (++attempt > networkRetries) return PageFetch.Stop(Step.Done(V2SyncResult.Offline(e.message.orEmpty())))
                sleep(backoffMs(attempt))
            } catch (e: V2ApiException.UpgradeRequired) {
                markUpgradeRequired()
                return PageFetch.Stop(Step.Done(V2SyncResult.UpgradeRequired))
            } catch (e: V2ApiException.Status) {
                when {
                    e.code == "session_expired" || e.code == "invalid_token" || e.status == 410 ->
                        // Same mode, staged cursors, continuation_of (the chain is persisted).
                        return PageFetch.Stop(Step.Restart)
                    e.code == "scope_changed" || e.code == "bootstrap_required" -> {
                        resetToBootstrap(if (e.code == "scope_changed") STATUS_SCOPE_CHANGED else STATUS_BOOTSTRAP_REQUIRED)
                        return PageFetch.Stop(Step.Restart)
                    }
                    e.code == "overloaded" ->
                        return PageFetch.Stop(Step.Done(V2SyncResult.RetryLater(e.retryAfterMs, "overloaded")))
                    e.status >= 500 -> {
                        if (++attempt > networkRetries) {
                            return PageFetch.Stop(Step.Done(V2SyncResult.RetryLater(e.retryAfterMs, "page ${e.status}")))
                        }
                        sleep(e.retryAfterMs ?: backoffMs(attempt))
                    }
                    else -> return PageFetch.Stop(statusStep(e))
                }
            } catch (e: V2ApiException.Decoding) {
                return PageFetch.Stop(Step.Done(V2SyncResult.Failed(e)))
            }
        }
    }

    private suspend fun openError(e: V2ApiException, chain: Chain?): Step = when (e) {
        is V2ApiException.Network -> Step.Done(V2SyncResult.Offline(e.message.orEmpty()))
        is V2ApiException.UpgradeRequired -> {
            markUpgradeRequired()
            Step.Done(V2SyncResult.UpgradeRequired)
        }
        is V2ApiException.Decoding -> Step.Done(V2SyncResult.Failed(e))
        is V2ApiException.Status -> when {
            e.code == "bootstrap_required" -> {
                Timber.w("v2 sync: bootstrap_required (${e.body?.reason})")
                resetToBootstrap(STATUS_BOOTSTRAP_REQUIRED)
                Step.Restart
            }
            e.code == "scope_changed" -> {
                resetToBootstrap(STATUS_SCOPE_CHANGED)
                Step.Restart
            }
            chain != null && (e.code == "session_expired" || e.code == "invalid_token" || e.status == 410) -> {
                // The continuation was refused: an incremental chain restarts from the committed
                // cursors; a bootstrap chain starts over (bootstrap_state stays in_progress).
                clearChain()
                Step.Restart
            }
            e.code == "device_not_registered" -> {
                business.write { V2Database.metaPut(it.connection, META_DEVICE_REGISTERED, null) }
                Step.Restart
            }
            e.code == "overloaded" || e.status >= 500 -> Step.Done(V2SyncResult.RetryLater(e.retryAfterMs, e.code ?: "${e.status}"))
            else -> statusStep(e)
        }
    }

    private fun statusStep(e: V2ApiException.Status): Step = Step.Done(
        when {
            e.code == "forbidden" || e.status == 403 -> V2SyncResult.Forbidden
            e.code == "device_revoked" -> V2SyncResult.DeviceRevoked
            e.code == "device_owned_elsewhere" -> V2SyncResult.DeviceIdRejected
            else -> V2SyncResult.Failed(e)
        },
    )

    private suspend fun registerDevice(): Step? {
        val deviceId = api.deviceId
        if (business.meta(META_DEVICE_REGISTERED) == deviceId) return null
        try {
            api.registerDevice(
                V2RegisterDeviceRequest(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    contractVersion = V2Contract.CLIENT_CONTRACT_VERSION,
                ),
            )
        } catch (e: V2ApiException) {
            return openError(e, chain = null).let { if (it == Step.Restart) Step.Done(V2SyncResult.Failed(e)) else it }
        }
        business.write { tx ->
            tx.execute("UPDATE database_identity SET device_id = ?", listOf(deviceId))
            V2Database.metaPut(tx.connection, META_DEVICE_REGISTERED, deviceId)
        }
        return null
    }

    private suspend fun closeQuietly(session: V2OpenSessionResponse, reason: String) {
        runCatching { api.closeSession(session.sessionId, session.sessionToken, reason) }
    }

    // ---------------------------------------------------------------------------------------
    // Local state

    private suspend fun loadState(): LocalState = business.read { conn ->
        val streams = conn.query("SELECT stream, cursor, feed_generation, bootstrap_state, status FROM sync_state")
            .associateBy { it["stream"] as String }
        fun stream(name: String) = streams[name]?.let {
            StreamState(
                (it["cursor"] as Number).toLong(),
                (it["feed_generation"] as Number).toLong(),
                it["bootstrap_state"] as String,
                it["status"] as String,
            )
        } ?: StreamState(0, 1, "none", "ok")
        val chain = V2Database.metaGet(conn, META_CHAIN)?.let { raw ->
            runCatching {
                val o = Json.parseToJsonElement(raw).jsonObject
                Chain(
                    sessionId = o.getValue("session_id").jsonPrimitive.content,
                    mode = o.getValue("mode").jsonPrimitive.content,
                    stagedAccount = V2Database.metaGet(conn, META_STAGED_ACCOUNT) ?: o.getValue("from_account").jsonPrimitive.content,
                    stagedGlobal = V2Database.metaGet(conn, META_STAGED_GLOBAL) ?: o.getValue("from_global").jsonPrimitive.content,
                    accountGeneration = o.getValue("account_fg").jsonPrimitive.content.toLong(),
                    globalGeneration = o.getValue("global_fg").jsonPrimitive.content.toLong(),
                    scopeEpoch = o.getValue("scope_epoch").jsonPrimitive.content,
                )
            }.getOrNull()
        }
        LocalState(
            account = stream("account"),
            global = stream("global"),
            scopeEpoch = conn.scalarLong("SELECT scope_epoch FROM database_identity") ?: 0L,
            chain = chain,
            projectionVersions = V2Database.metaGet(conn, META_PROJECTION_VERSIONS),
        )
    }

    private fun putChain(conn: V2Connection, chain: Chain) {
        val o = JsonObject(
            mapOf(
                "session_id" to JsonPrimitive(chain.sessionId),
                "mode" to JsonPrimitive(chain.mode),
                "from_account" to JsonPrimitive(chain.stagedAccount),
                "from_global" to JsonPrimitive(chain.stagedGlobal),
                "account_fg" to JsonPrimitive(chain.accountGeneration),
                "global_fg" to JsonPrimitive(chain.globalGeneration),
                "scope_epoch" to JsonPrimitive(chain.scopeEpoch),
            ),
        )
        V2Database.metaPut(conn, META_CHAIN, o.toString())
        V2Database.metaPut(conn, META_STAGED_ACCOUNT, chain.stagedAccount)
        V2Database.metaPut(conn, META_STAGED_GLOBAL, chain.stagedGlobal)
    }

    private fun clearChainIn(conn: V2Connection) {
        V2Database.metaPut(conn, META_CHAIN, null)
        V2Database.metaPut(conn, META_STAGED_ACCOUNT, null)
        V2Database.metaPut(conn, META_STAGED_GLOBAL, null)
    }

    private suspend fun clearChain() = business.write { clearChainIn(it.connection) }

    /** Marks both streams for a bootstrap; the next session wipes and starts over. */
    private suspend fun resetToBootstrap(status: String) = business.write { tx ->
        tx.execute("UPDATE sync_state SET bootstrap_state = 'none', status = ?", listOf(status))
        clearChainIn(tx.connection)
        tx.markChanged("sync_state")
    }

    private suspend fun markUpgradeRequired() = business.write { tx ->
        tx.execute("UPDATE sync_state SET status = ?", listOf(STATUS_UPGRADE_REQUIRED))
        tx.markChanged("sync_state")
    }

    /** Wipes every synced table (business, then chat) and records the new bootstrap chain. */
    private suspend fun wipeForBootstrap(chain: Chain) {
        chat?.let { store ->
            store.writeWithoutForeignKeys { tx -> wipeTables(tx, V2StoreKind.CHAT) }
        }
        business.writeWithoutForeignKeys { tx ->
            wipeTables(tx, V2StoreKind.BUSINESS)
            tx.execute("UPDATE sync_state SET bootstrap_state = 'in_progress', cursor = 0")
            V2Database.metaPut(tx.connection, META_PROJECTION_VERSIONS, null)
            putChain(tx.connection, chain)
            tx.markChanged("sync_state")
        }
    }

    private fun wipeTables(tx: V2Transaction, kind: V2StoreKind) {
        val tables = manifest.values.filter { it.store == kind }.mapNotNull { it.mobileTable }.toSortedSet()
        for (table in tables) {
            tx.execute("DELETE FROM ${V2SqlScript.quote(table)}")
        }
        tx.markChanged(*tables.toTypedArray())
    }

    private suspend fun commit(session: V2OpenSessionResponse, desiredVersions: String) = business.write { tx ->
        val now = clock()
        val w = session.account.w.toLong()
        tx.execute(
            "UPDATE sync_state SET cursor = ?, feed_generation = ?, last_session_id = ?, last_applied_at = ?, " +
                "bootstrap_state = 'complete', status = 'ok' WHERE stream = 'account'",
            listOf(w, session.account.feedGeneration, session.sessionId, now),
        )
        tx.execute(
            "UPDATE sync_state SET cursor = ?, feed_generation = ?, last_session_id = ?, last_applied_at = ?, " +
                "bootstrap_state = 'complete', status = 'ok' WHERE stream = 'global'",
            listOf(session.global.g.toLong(), session.global.feedGeneration, session.sessionId, now),
        )
        tx.execute(
            "UPDATE database_identity SET scope_epoch = ?, feed_generation = ?, global_feed_generation = ?",
            listOf(session.scopeEpoch.toLong(), session.account.feedGeneration, session.global.feedGeneration),
        )
        clearChainIn(tx.connection)
        V2Database.metaPut(tx.connection, META_PROJECTION_VERSIONS, desiredVersions)
        // Optimistic rows retire once an acked session has w >= result_watermark (overlays and
        // dependency edges cascade).
        val retired = tx.execute(
            "DELETE FROM pending_commands WHERE state = 'acknowledged' AND (result_watermark IS NULL OR result_watermark <= ?)",
            listOf(w),
        )
        if (retired > 0) tx.markChanged("pending_commands", "local_overlays")
        tx.markChanged("sync_state")
    }

    // ---------------------------------------------------------------------------------------
    // Apply

    private data class Resolved(val projection: V2Projection, val item: V2PageItem)

    private fun resolve(items: List<V2PageItem>, kind: V2StoreKind): List<Resolved> {
        // Every key appears once per session; collapse defensively (highest seq wins) when pages are carried.
        val latest = LinkedHashMap<Pair<String, String>, V2PageItem>()
        for (item in items) {
            val key = item.entityType to item.entityKey
            val existing = latest[key]
            if (existing == null || item.seq.toBigInteger() >= existing.seq.toBigInteger()) latest[key] = item
        }
        return latest.values.mapNotNull { item ->
            val projection = manifest[item.entityType]
            when {
                projection == null -> {
                    Timber.d("v2 sync: ignoring unknown entity_type ${item.entityType}")
                    null
                }
                projection.mobileTable == null || projection.store != kind -> null
                else -> Resolved(projection, item)
            }
        }
    }

    private fun applyItems(conn: V2Connection, applier: V2ProjectionApplier, resolved: List<Resolved>): Set<String> {
        val upserts = resolved.filter { it.item.op == OP_UPSERT }
            .sortedWith(compareBy<Resolved> { it.projection.applyRank }.thenBy { it.item.seq.toBigInteger() })
        val deletes = resolved.filter { it.item.op == OP_DELETE }
            .sortedWith(compareByDescending<Resolved> { it.projection.applyRank }.thenBy { it.item.seq.toBigInteger() })
        resolved.firstOrNull { it.item.op != OP_UPSERT && it.item.op != OP_DELETE }?.let {
            throw V2ApplyException(it.item.entityType, it.item.entityKey, "unknown op ${it.item.op}")
        }
        for (r in upserts) {
            val body = r.item.body ?: throw V2ApplyException(r.item.entityType, r.item.entityKey, "upsert without body")
            applier.upsert(conn, r.projection, r.item.entityKey, body)
        }
        for (r in deletes) applier.delete(conn, r.projection, r.item.entityKey)
        return resolved.mapNotNull { it.projection.mobileTable }.toSet()
    }

    /** Chat rows go to the chat store in their own transaction first (iOS parity; idempotent). */
    private suspend fun applyChat(page: V2PageResponse) {
        val store = chat ?: return
        val resolved = resolve(page.items, V2StoreKind.CHAT)
        if (resolved.isEmpty()) return
        try {
            store.write { tx ->
                tx.execute("PRAGMA defer_foreign_keys = ON")
                tx.markChanged(*applyItems(tx.connection, chatApplier, resolved).toTypedArray())
            }
        } catch (e: V2SqlException) {
            throw V2ApplyException("chat", "page ${page.pageNo}", e.message.orEmpty(), e)
        }
    }

    /** One transaction for [pages] (normally one page) plus their staged positions. */
    private suspend fun applyBusiness(pages: List<V2PageResponse>) {
        val resolved = resolve(pages.flatMap { it.items }, V2StoreKind.BUSINESS)
        try {
            business.write { tx ->
                // Parents may follow children within the page; checked at COMMIT.
                tx.execute("PRAGMA defer_foreign_keys = ON")
                tx.markChanged(*applyItems(tx.connection, businessApplier, resolved).toTypedArray())
                for (page in pages) {
                    val key = if (page.stream == V2Stream.ACCOUNT.wire) META_STAGED_ACCOUNT else META_STAGED_GLOBAL
                    V2Database.metaPut(tx.connection, key, page.lastSeq)
                }
            }
        } catch (e: V2SqlException) {
            // A deferred foreign key fails the COMMIT itself.
            val last = pages.last()
            throw V2ApplyException("page", "${last.stream}#${last.pageNo}", e.message.orEmpty(), e)
        }
    }

    private fun verifyChecksum(page: V2PageResponse) {
        val checksum = page.checksum ?: return
        val items = JsonArray(page.items.map { V2Wire.json.encodeToJsonElement(V2PageItem.serializer(), it) })
        val actual = "sha256:" + V2Canonical.sha256Hex(V2Canonical.string(items))
        // Optional (sync-wire.md): floats are not canonicalised, so a mismatch is only logged.
        if (actual != checksum) Timber.d("v2 sync: page ${page.pageNo} checksum differs from the local canonical form")
    }

    private fun desiredVersions(server: Map<String, Int>?): String {
        val versions = manifest.values.filter { it.mobileTable != null }
            .associate { it.entityType to (server?.get(it.entityType) ?: it.schemaVersion) }
            .toSortedMap()
        return JsonObject(versions.mapValues { JsonPrimitive(it.value) }).toString()
    }

    private fun backoffMs(attempt: Int): Long = (500L shl (attempt - 1).coerceAtMost(6)).coerceAtMost(30_000L)

    companion object {
        const val MODE_BOOTSTRAP = "bootstrap"
        const val MODE_INCREMENTAL = "incremental"
        private const val OP_UPSERT = "upsert"
        private const val OP_DELETE = "delete"
        private const val BOOTSTRAP_COMPLETE = "complete"
        const val STATUS_BOOTSTRAP_REQUIRED = "bootstrap_required"
        const val STATUS_SCOPE_CHANGED = "scope_changed"
        const val STATUS_UPGRADE_REQUIRED = "upgrade_required"
        private val RESET_STATUSES = setOf(STATUS_BOOTSTRAP_REQUIRED, STATUS_SCOPE_CHANGED)
        private const val MAX_RESTARTS = 4
        private const val MAX_CARRIED_PAGES = 32

        const val META_CHAIN = "sync.chain"
        const val META_STAGED_ACCOUNT = "sync.staged.account"
        const val META_STAGED_GLOBAL = "sync.staged.global"
        const val META_PROJECTION_VERSIONS = "sync.projection_versions"
        const val META_DEVICE_REGISTERED = "device.registered"

        /** Projection version map for tests and diagnostics. */
        internal fun parseVersions(raw: String): Map<String, Int> =
            Json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.int }
    }
}
