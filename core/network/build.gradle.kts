plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.avago.core.network"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        // API target (see root build.gradle.kts): release is always production.
        buildConfigField("String", "BASE_URL", "\"${rootProject.extra["avago.prodBaseUrl"]}\"")
        buildConfigField("String", "API_TARGET", "\"prod\"")
    }
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"${rootProject.extra["avago.debugBaseUrl"]}\"")
            buildConfigField("String", "API_TARGET", "\"${rootProject.extra["avago.debugApiTarget"]}\"")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
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

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit5)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
