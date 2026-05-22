package com.avago.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ── Data model ────────────────────────────────────────────────────────────────

data class OssLibrary(
    val name: String,
    val license: String,
    val url: String? = null,
)

// ── Static OSS library list ───────────────────────────────────────────────────
// Derived from dependencies declared in app/build.gradle.kts and feature module
// build.gradle.kts files.

val OSS_LIBRARIES = listOf(
    OssLibrary("Jetpack Compose", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    OssLibrary("Hilt (Dagger)", "Apache 2.0", "https://dagger.dev/hilt/"),
    OssLibrary("Hilt Navigation Compose", "Apache 2.0", "https://developer.android.com/jetpack/compose/libraries#hilt"),
    OssLibrary("Room", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
    OssLibrary("Kotlin Coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OssLibrary("kotlinx.serialization", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    OssLibrary("kotlinx-datetime", "Apache 2.0", "https://github.com/Kotlin/kotlinx-datetime"),
    OssLibrary("Ktor (HTTP Client)", "Apache 2.0", "https://ktor.io"),
    OssLibrary("DataStore Preferences", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    OssLibrary("Navigation Compose", "Apache 2.0", "https://developer.android.com/jetpack/compose/navigation"),
    OssLibrary("Lifecycle / ViewModel Compose", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    OssLibrary("WorkManager", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/work"),
    OssLibrary("CameraX", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/camera"),
    OssLibrary("Coil (Image Loading)", "Apache 2.0", "https://coil-kt.github.io/coil/"),
    OssLibrary("ML Kit — Barcode Scanning", "Apache 2.0", "https://developers.google.com/ml-kit/vision/barcode-scanning"),
    OssLibrary("ML Kit — Text Recognition", "Apache 2.0", "https://developers.google.com/ml-kit/vision/text-recognition"),
    OssLibrary("ML Kit — Document Scanner", "Apache 2.0", "https://developers.google.com/ml-kit/vision/doc-scanner"),
    OssLibrary("Firebase BOM / Auth / Messaging / Analytics / Crashlytics", "Apache 2.0", "https://firebase.google.com/support/release-notes/android"),
    OssLibrary("Google Credential Manager", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/credentials"),
    OssLibrary("Timber (Logging)", "Apache 2.0", "https://github.com/JakeWharton/timber"),
    OssLibrary("Splash Screen API", "Apache 2.0", "https://developer.android.com/develop/ui/views/launch/splash-screen"),
    OssLibrary("AndroidX Core (Desugar JDK Libs)", "BSD / Apache 2.0", "https://github.com/google/desugar_jdk_libs"),
    OssLibrary("Kotlin Standard Library", "Apache 2.0", "https://kotlinlang.org/"),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun LicensesScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
    ) {
        item {
            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        item {
            Text(
                text = "This application includes the following open-source libraries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }

        items(OSS_LIBRARIES) { lib ->
            ListItem(
                headlineContent = { Text(lib.name) },
                supportingContent = {
                    Text(
                        text = lib.license + if (lib.url != null) "  ·  tap to view" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = if (lib.url != null) {
                    Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))
                        )
                    }
                } else {
                    Modifier
                },
            )
            HorizontalDivider()
        }
    }
}
