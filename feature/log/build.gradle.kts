plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.avago.feature.log"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.nav.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.nav.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:auth"))
    implementation(project(":core:sync"))
    implementation(project(":core:network"))
}
