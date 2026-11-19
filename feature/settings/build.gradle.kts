plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.avago.feature.settings"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        // API target (see root build.gradle.kts): release is always production.
        buildConfigField("String", "BASE_URL", "\"${rootProject.extra["avago.prodBaseUrl"]}\"")
        buildConfigField("String", "API_TARGET", "\"prod\"")
        val versionName = project.findProperty("avago.version.name") as? String ?: "1.0.0"
        val versionCode = (project.findProperty("avago.version.code") as? String)?.toInt() ?: 1
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("int", "VERSION_CODE", "$versionCode")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"${rootProject.extra["avago.debugBaseUrl"]}\"")
            buildConfigField("String", "API_TARGET", "\"${rootProject.extra["avago.debugApiTarget"]}\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:design"))
    implementation(project(":core:push"))
    implementation(project(":core:ui"))
    implementation(libs.datastore.preferences)
}
