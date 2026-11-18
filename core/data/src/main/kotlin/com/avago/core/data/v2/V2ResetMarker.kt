package com.avago.core.data.v2

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Cutover marker states (plan §14.4 step 1). Absent means the reset has never started. */
enum class V2ResetState(val raw: String) {
    ABSENT(""),
    STARTED("reset_started"),
    COMPLETE("reset_complete"),
}

/**
 * App-wide cutover marker: a small file in `noBackupFilesDir`, outside every account database
 * and excluded from Auto Backup. It stays [V2ResetState.STARTED] until every reset step has
 * finished, so a reset interrupted by a crash or process kill runs again on the next launch.
 */
class V2ResetMarker(private val file: File) {

    fun read(): V2ResetState {
        if (!file.isFile) return V2ResetState.ABSENT
        val raw = runCatching { file.readText().trim() }.getOrDefault("")
        // An unreadable or unknown value is treated as an interrupted reset, never as complete.
        return V2ResetState.entries.firstOrNull { it != V2ResetState.ABSENT && it.raw == raw }
            ?: V2ResetState.STARTED
    }

    val isComplete: Boolean get() = read() == V2ResetState.COMPLETE

    /** Writes [state] atomically (temp file + atomic rename) so a crash never leaves a torn value. */
    fun write(state: V2ResetState) {
        require(state != V2ResetState.ABSENT) { "use clear() to remove the marker" }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(state.raw)
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Test/developer helper; production never removes the marker once complete. */
    fun clear() {
        file.delete()
    }
}
