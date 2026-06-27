plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.naaammme.bbspace.core.playback"
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
    api(project(":core:model"))
    api(libs.media3.common)

    implementation(project(":core:common"))
    implementation(project(":core:settings"))
    implementation(project(":core:auth"))
    implementation(project(":core:danmaku"))
    implementation(project(":core:download"))
    implementation(project(":core:history"))
    implementation(project(":core:live"))
    implementation(project(":core:video"))
    implementation(project(":infra:crypto"))
    implementation(project(":infra:network-http"))
    implementation(project(":infra:player"))
    implementation(project(":infra:network-grpc"))
    implementation(project(":infra:network-web"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
}
