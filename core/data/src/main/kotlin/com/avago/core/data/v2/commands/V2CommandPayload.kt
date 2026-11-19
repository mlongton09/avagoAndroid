package com.avago.core.data.v2.commands

import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2NewUpload
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.JsonObject

/** The catalog's command kinds (sync-wire.md "Catalog"). */
enum class V2CommandKind { CREATE, UPDATE, DELETE, ACTION }

/** A catalog enum value; encoded as its [wire] string. */
interface V2WireEnum {
    val wire: String
}

/** A nested payload object (document lines). */
interface V2PayloadStruct {
    fun toJson(): JsonObject
}

/** A typed catalog command payload (generated in V2GeneratedCommands.kt). */
interface V2CommandPayload {
    val commandName: String
    val commandEntityType: String

    /** The payload `id`: the row a create writes or the row an update/delete/action targets. */
    val commandEntityKey: String
    val commandKind: V2CommandKind

    /** `base_version: required` commands (updates, deletes, PO transitions). */
    val requiresBaseVersion: Boolean

    fun toJson(): JsonObject
}

/**
 * The outbox entry for [this] command. [baseVersion] is the `version` of the row the edit was
 * based on (null for creates); commands with [V2CommandPayload.requiresBaseVersion] must carry it.
 */
fun V2CommandPayload.toNewCommand(
    baseVersion: Long? = null,
    overlay: V2OverlayPatch? = null,
    dependsOn: List<String> = emptyList(),
    uploads: List<V2NewUpload> = emptyList(),
): V2NewCommand {
    require(commandKind != V2CommandKind.CREATE || baseVersion == null) { "$commandName: creates carry no base_version" }
    require(!requiresBaseVersion || baseVersion != null) {
        "$commandName needs the base version of $commandEntityType $commandEntityKey"
    }
    return V2NewCommand(
        operation = commandName,
        payload = toJson(),
        payloadSchemaVersion = V2_COMMAND_CATALOG_VERSION,
        entityType = commandEntityType,
        entityKey = commandEntityKey,
        baseVersion = baseVersion,
        dependsOn = dependsOn,
        overlayPatch = overlay?.toJson(),
        reducerVersion = V2OverlayPatch.REDUCER_VERSION,
        uploads = uploads,
    )
}
