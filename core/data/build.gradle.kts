plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

// Internal cutover builds: `-Pv2Default=true` turns schema v2 on by default (V2Flag.defaultEnabled).
// Without the property v2 stays off; see docs/v2-android-status.md.
val v2Default = (project.findProperty("v2Default") as String?)?.toBoolean() ?: false

android {
    namespace = "com.avago.core.data"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "V2_DEFAULT", "$v2Default")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Exported Room schemas: the migration-test baselines (also packaged into androidTest assets).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    // Rentals: JSON-record store helpers (LocalFleet parses synced rows).
    implementation(libs.serialization.json)
    // Schema v2 store (V2Database): bundled SQLite, never the platform build (L16).
    implementation(libs.androidx.sqlite.bundled)

    // Rentals domain unit tests (Money, fleet status rule fixtures).
    testImplementation(libs.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.serialization.json)
    testImplementation(libs.coroutines.test)
    // v2 store/sync tests run the generated DDL on a real SQLite (>= 3.45) through JDBC.
    testImplementation(libs.sqlite.jdbc)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Room migration tests (MigrationTestHelper) run on a device/emulator.
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
