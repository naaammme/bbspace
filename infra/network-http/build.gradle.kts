plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.naaammme.bbspace.infra.http"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.retrofit)
    api(libs.retrofit.kotlin.serialization)
    api(libs.okhttp)
    api(libs.okhttp.brotli)
    api(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)

    implementation(project(":infra:crypto"))
    implementation(project(":infra:protobuf"))
    implementation(libs.kotlinx.coroutines.core)
}
