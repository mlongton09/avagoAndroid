plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.avago.feature.chat"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    implementation(project(":core:sync"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    // Asset glyph + color mapping for asset-type thread rows (iOS parity:
    // ThreadRowCell renders asset threads with the same colored-circle avatar
    // the assets list uses).
    implementation(project(":feature:assets"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.nav.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.nav.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.ktor.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
}
