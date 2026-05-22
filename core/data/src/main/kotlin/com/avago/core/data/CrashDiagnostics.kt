package com.avago.core.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Centralises all Crashlytics interactions so that every non-fatal report and
 * breadcrumb includes consistent account/user context.
 *
 * Call [setUserContext] after sign-in and on app launch to seed Crashlytics with
 * non-PII identifiers. Never sends display names, emails, or other PII — only
 * opaque UUIDs assigned by the server.
 *
 * [activeAccountId] and [activeUserId] are [StateFlow]s sourced from
 * [IdentityManager] and provided via Hilt so that core:data does not depend on
 * core:auth.
 */
@Singleton
class CrashDiagnostics @Inject constructor(
    private val activeAccountId: @JvmSuppressWildcards StateFlow<String?>,
    @Named("activeUserId") private val activeUserId: @JvmSuppressWildcards StateFlow<String?>,
) {

    private val crashlytics: FirebaseCrashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    /**
     * Sets Crashlytics user context.
     *
     * Emits only opaque UUID-style identifiers — no PII.
     */
    fun setUserContext() {
        val accountId = activeAccountId.value
        val userId = activeUserId.value
        try {
            crashlytics.setUserId(accountId ?: "anonymous")
            crashlytics.setCustomKey("account_id", accountId ?: "")
            crashlytics.setCustomKey("user_id", userId ?: "")
            Timber.d("CrashDiagnostics: user context set (account=%s)", accountId)
        } catch (e: Exception) {
            Timber.w(e, "CrashDiagnostics: failed to set user context")
        }
    }

    /**
     * Records a non-fatal error with optional context key/value pairs.
     *
     * Each [context] entry is attached as a Crashlytics custom key before the
     * exception is recorded, so it appears in the crash report for this session.
     */
    fun recordNonFatal(throwable: Throwable, context: Map<String, String> = emptyMap()) {
        try {
            context.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
            crashlytics.recordException(throwable)
            Timber.w(throwable, "CrashDiagnostics: non-fatal recorded")
        } catch (e: Exception) {
            Timber.w(e, "CrashDiagnostics: failed to record non-fatal")
        }
    }

    /**
     * Sets a single custom key for the next crash report.
     * Call before risky operations to aid post-mortem diagnosis.
     */
    fun setKey(key: String, value: String) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Timber.w(e, "CrashDiagnostics: failed to set key=%s", key)
        }
    }

    /**
     * Logs a breadcrumb message. Appears in the Crashlytics crash report log
     * (visible in the Firebase console under "Logs" tab for the session).
     */
    fun log(message: String) {
        try {
            crashlytics.log(message)
        } catch (e: Exception) {
            Timber.w(e, "CrashDiagnostics: failed to log message")
        }
    }
}
