plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.naaammme.bbspace.core.settings"
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
    api(project(":core:designsystem"))

    implementation(project(":core:common"))
    implementation(project(":infra:crypto"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
}
