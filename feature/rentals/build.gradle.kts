plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.avago.feature.rentals"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.icons)
    // Unit photo thumbnails from the shared asset photo pipeline (already used by core:ui / feature:assets).
    implementation(libs.coil.compose)
    implementation(libs.nav.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.serialization.json)
    implementation(libs.datastore.preferences)
    // Avago Marketplace: renter session token (encrypted, separate from staff tokens) and the
    // "Use my location" buttons (already used by feature/rentalsops / feature/assets).
    implementation(libs.security.crypto)
    implementation(libs.play.services.location)
    // Scan + document capture (already used by feature/log, feature/assets — no new weight).
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    // Home-screen fleet widget.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:auth"))
    implementation(project(":core:sync"))
    implementation(project(":core:network"))
    implementation(project(":core:permissions"))
    implementation(project(":feature:assets"))
    implementation(project(":feature:log"))

    testImplementation(libs.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.serialization.json)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
