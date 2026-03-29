plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.naaammme.bbspace.infra.player"
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
    implementation(project(":infra:crypto"))
    implementation(project(":infra:network-http"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.media3.exoplayer)
    api(libs.media3.exoplayer.dash)
    api(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    api(libs.media3.ui)

    implementation(libs.kotlinx.coroutines.android)
}
