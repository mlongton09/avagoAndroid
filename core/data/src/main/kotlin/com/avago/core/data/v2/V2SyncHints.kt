package com.avago.core.data.v2

/**
 * E13 sync hints (`{"t":"sync_hint","account_id","w"}`, P3-15b): a silent push that only says the
 * account stream reached head `w`. The device opens a sync session when `w` is ahead of its
 * committed cursor.
 */
object V2SyncHints {
    enum class Outcome(val wire: String, val syncs: Boolean) {
        /** Ahead of the local cursor, or nothing is synced yet: pull now. */
        AHEAD("ahead", true),

        /** Already at or past `w` (the device's own commits, or a hint that arrived late). */
        STALE("stale", false),

        /** For a signed-in account that is not active; it syncs when the user switches to it. */
        OTHER_ACCOUNT("other_account", false),

        /** No readable head: pull, as any push means something changed. */
        MALFORMED("malformed", true),
    }

    /** [cursor] is the committed account cursor of [activeAccountId]'s store; null before a bootstrap completed. */
    fun decide(hintAccountId: String?, hintHead: String?, activeAccountId: String?, cursor: Long?): Outcome {
        if (hintAccountId != null && activeAccountId != null && !hintAccountId.equals(activeAccountId, ignoreCase = true)) {
            return Outcome.OTHER_ACCOUNT
        }
        val head = hintHead?.toLongOrNull() ?: return Outcome.MALFORMED
        return if (cursor == null || head > cursor) Outcome.AHEAD else Outcome.STALE
    }
}
