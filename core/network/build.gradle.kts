plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.avago.core.network"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "BASE_URL", "\"https://api.avagomate.com\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // Ktor
    api(libs.ktor.core)
    api(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.json)
    implementation(libs.ktor.auth)
    implementation(libs.ktor.logging)
    implementation(libs.datastore.preferences)

    // Serialization
    implementation(libs.serialization.json)
    implementation(libs.kotlinx.datetime)
}
