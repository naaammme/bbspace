plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    // alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.naaammme.bbspace.core.data"
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

// room {
//     schemaDirectory("$projectDir/schemas")
// }

dependencies {
    api(project(":core:domain"))
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(project(":core:designsystem"))

    implementation(project(":infra:crypto"))
    implementation(project(":infra:network-http"))
    implementation(project(":infra:network-grpc"))
    implementation(project(":infra:coldstart"))
    implementation(project(":infra:protobuf"))
    implementation(project(":infra:player"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    api(libs.datastore.preferences)

    implementation(libs.coil.core)
    implementation(libs.kotlinx.coroutines.android)
}
