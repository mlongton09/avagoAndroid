plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.avago.core.auth"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
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

    implementation(libs.security.crypto)
    implementation(libs.serialization.json)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    implementation(libs.work.runtime)
    api(project(":core:network"))
    implementation(project(":core:data"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit5)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
