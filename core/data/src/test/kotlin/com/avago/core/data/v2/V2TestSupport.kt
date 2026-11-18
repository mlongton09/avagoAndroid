package com.avago.core.data.v2

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Records every request and answers from [route]. */
class MockTransport : V2Transport {
    val requests = mutableListOf<V2Request>()
    var route: (V2Request) -> V2Response = { V2Response(404, """{"error":"not_found"}""") }

    override suspend fun send(request: V2Request): V2Response {
        requests += request
        return route(request)
    }

    fun matching(method: String, suffix: String) = requests.filter { it.method == method && it.path.endsWith(suffix) }
}

/** Throws from [send] like a dropped connection. */
class OfflineError : java.io.IOException("offline")

/**
 * A scripted sync server: [opens] answers session opens in order, [pages] maps page tokens to
 * responses (a token may answer differently on later fetches via [pageQueue]).
 */
class ScriptedServer(val transport: MockTransport = MockTransport()) {
    val opens = ArrayDeque<V2Response>()
    val pages = mutableMapOf<String, V2Response>()
    val pageQueue = mutableMapOf<String, ArrayDeque<V2Response>>()
    var ack: () -> V2Response = { V2Response(204) }
    val commands = ArrayDeque<() -> V2Response>()
    val receipts = ArrayDeque<() -> V2Response>()

    init {
        transport.route = { r ->
            when {
                r.method == "POST" && r.path == "/v2/devices" -> V2Response(204)
                r.method == "POST" && r.path.endsWith("/sync/sessions") -> opens.removeFirstOrNull() ?: error("unexpected open")
                r.method == "GET" && r.path.endsWith("/pages") -> {
                    val token = r.query.getValue("page_token")
                    pageQueue[token]?.removeFirstOrNull() ?: pages[token] ?: error("unknown page token $token")
                }
                r.method == "POST" && r.path.endsWith("/sync/acks") -> ack()
                r.method == "POST" && r.path.endsWith("/close") -> V2Response(204)
                r.method == "POST" && r.path.endsWith("/commands") -> (commands.removeFirstOrNull() ?: error("unexpected command"))()
                r.method == "GET" && r.path.contains("/commands/") -> (receipts.removeFirstOrNull() ?: error("unexpected receipt"))()
                else -> error("unexpected ${r.method} ${r.path}")
            }
        }
    }
}

object V2Fixtures {
    val ACCOUNT = V2Uuid.v7()
    val USER = V2Uuid.v7()
    val DEVICE = V2Uuid.v7()
    val identity = V2StoreIdentity(ACCOUNT, USER, DEVICE)

    fun business(conn: V2Connection = JdbcV2Connection(), id: V2StoreIdentity = identity) =
        V2Database.open(conn, V2StoreKind.BUSINESS, id, nowMillis = 1_000, dispatcher = Dispatchers.Unconfined)

    fun chat(conn: V2Connection = JdbcV2Connection(), id: V2StoreIdentity = identity) =
        V2Database.open(conn, V2StoreKind.CHAT, id, nowMillis = 1_000, dispatcher = Dispatchers.Unconfined)

    fun json(status: Int, body: JsonElement) = V2Response(status, body.toString())

    fun error(status: Int, code: String) = V2Response(status, """{"error":"$code","message":"$code"}""")

    fun open(
        sessionId: String,
        kind: String,
        w: String,
        g: String,
        from: String = "0",
        gFrom: String = "0",
        fg: Long = 1,
        gfg: Long = 1,
        epoch: String = "1",
        firstToken: String? = "t1",
    ) = json(
        201,
        buildJsonObject {
            put("session_id", sessionId)
            put("session_token", "tok-$sessionId")
            put("kind", kind)
            put("state", if (firstToken == null) "completed" else "open")
            put("account", buildJsonObject { put("from", from); put("w", w); put("feed_generation", fg) })
            put("global", buildJsonObject { put("from", gFrom); put("g", g); put("feed_generation", gfg) })
            put("scope_epoch", epoch)
            put("first_page_token", firstToken?.let(::JsonPrimitive) ?: JsonNull)
        },
    )

    fun page(
        sessionId: String,
        stream: String,
        pageNo: Int,
        after: String,
        lastSeq: String,
        items: List<JsonObject>,
        next: String?,
    ) = json(
        200,
        buildJsonObject {
            put("session_id", sessionId)
            put("stream", stream)
            put("page_no", pageNo)
            put("after", after)
            put("last_seq", lastSeq)
            put("items", JsonArray(items))
            put("next_page_token", next?.let(::JsonPrimitive) ?: JsonNull)
            put("is_final", next == null)
        },
    )

    fun location(id: String, seq: Long, name: String = "Loc $id", parent: String? = null, version: Long = 1) = buildJsonObject {
        put("entity_type", "locations")
        put("entity_key", id)
        put("seq", seq.toString())
        put("version", version)
        put("op", "upsert")
        put(
            "body",
            buildJsonObject {
                put("id", id)
                put("account_id", ACCOUNT)
                put("created_at", "2026-01-02T03:04:05.678Z")
                put("updated_at", "2026-01-02T03:04:05.678Z")
                put("version", version)
                put("kind", "site")
                put("code", "C-$seq")
                put("name", name)
                put("locality", "Oslo")
                put("country_code", "NO")
                put("latitude", "59.9139")
                put("longitude", "10.7522")
                put("coordinate_source", "manual")
                put("is_primary", false)
                put("parent_location_id", parent?.let(::JsonPrimitive) ?: JsonNull)
            },
        )
    }

    fun bin(id: String, locationId: String, seq: Long, code: String? = "B-$seq", version: Long = 1) = buildJsonObject {
        put("entity_type", "bins")
        put("entity_key", id)
        put("seq", seq.toString())
        put("version", version)
        put("op", "upsert")
        put(
            "body",
            buildJsonObject {
                put("id", id)
                put("account_id", ACCOUNT)
                put("created_at", "2026-01-02T03:04:05Z")
                put("updated_at", "2026-01-02T03:04:05Z")
                put("version", version)
                put("location_id", locationId)
                put("code", code?.let(::JsonPrimitive) ?: JsonNull)
                put("bin_kind", "shelf")
                put("capacity_qty", "12.5")
            },
        )
    }

    fun delete(entityType: String, id: String, seq: Long) = buildJsonObject {
        put("entity_type", entityType)
        put("entity_key", id)
        put("seq", seq.toString())
        put("op", "delete")
    }
}
