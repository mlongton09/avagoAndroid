plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.avago"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.avago"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val baseUrl = project.findProperty("avago.base.url") as? String ?: "https://api.avagomate.com"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "upload-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.icons)
    implementation(libs.nav.compose)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.hilt.android)
    implementation(libs.hilt.nav.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.splashscreen)
    implementation(libs.timber)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.datetime)

    implementation(project(":core:design"))
    implementation(project(":core:ui"))
    implementation(project(":core:auth"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:push"))
    implementation(project(":core:data"))
    implementation(project(":core:seed"))
    implementation(project(":core:i18n"))
    implementation(project(":core:ai"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:assets"))
    implementation(project(":feature:log"))
    implementation(project(":feature:workorders"))
    implementation(project(":feature:inventory"))
    implementation(project(":feature:schedule"))
    implementation(project(":feature:docs"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}

ksp {
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
}
