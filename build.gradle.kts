buildscript {
    dependencies {
        // AGP 9 built-in Kotlin: pin the Kotlin Gradle plugin to the catalog version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt) apply false
}

// ---- Developer API target switch -----------------------------------------
// Same flag as AvagoWeb/AvagoAdmin. Resolved once here and applied to the
// BuildConfig of :app, :core:network and :feature:settings (debug only —
// release always uses production). Lookup order for each setting:
//   -P / gradle.properties  →  local.properties (git-ignored)  →  env var
//   avago.api.target     / AVAGO_API_TARGET     = prod | local
//   avago.local.api.url  / AVAGO_LOCAL_API_URL  = default http://10.0.2.2:8080
//                                                 (emulator → host PC)
// `avago.base.url` (legacy) is still honoured for debug builds when no
// avago.api.target is set.
val avagoProdBaseUrl = "https://api.avagomate.com"
val avagoDefaultLocalUrl = "http://10.0.2.2:8080"
val avagoLocalProperties = java.util.Properties().apply {
    providers.fileContents(layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(java.io.StringReader(it)) }
}

fun avagoSetting(property: String, envVar: String? = null): String? =
    listOf(
        providers.gradleProperty(property).orNull,
        avagoLocalProperties.getProperty(property),
        envVar?.let { providers.environmentVariable(it).orNull },
    ).firstNotNullOfOrNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }

fun avagoHttpUrl(raw: String?, fallback: String, label: String): String {
    if (raw == null) return fallback
    if (Regex("^https?://[^/\\s]+.*$", RegexOption.IGNORE_CASE).matches(raw)) return raw.trimEnd('/')
    logger.warn("[avago] Ignoring invalid $label=\"$raw\" (expected http(s)://host:port); using $fallback")
    return fallback
}

val avagoRequestedTarget = avagoSetting("avago.api.target", "AVAGO_API_TARGET")?.lowercase()?.let { target ->
    if (target == "prod" || target == "local") {
        target
    } else {
        logger.warn("[avago] Ignoring invalid avago.api.target=\"$target\" (expected prod or local)")
        null
    }
}
val avagoLegacyBaseUrl = avagoSetting("avago.base.url")
val (avagoDebugApiTarget, avagoDebugBaseUrl) = when {
    avagoRequestedTarget == "local" ->
        "local" to avagoHttpUrl(
            avagoSetting("avago.local.api.url", "AVAGO_LOCAL_API_URL"),
            avagoDefaultLocalUrl,
            "avago.local.api.url",
        )
    avagoRequestedTarget == "prod" -> "prod" to avagoProdBaseUrl
    avagoLegacyBaseUrl != null ->
        "custom" to avagoHttpUrl(avagoLegacyBaseUrl, avagoProdBaseUrl, "avago.base.url")
    else -> "prod" to avagoProdBaseUrl
}
extra["avago.prodBaseUrl"] = avagoProdBaseUrl
extra["avago.debugApiTarget"] = avagoDebugApiTarget
extra["avago.debugBaseUrl"] = avagoDebugBaseUrl
logger.lifecycle("[avago] API target (debug builds): $avagoDebugApiTarget → $avagoDebugBaseUrl; release → $avagoProdBaseUrl")
