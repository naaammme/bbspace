plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.naaammme.bbspace.infra.crypto"
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 应用常量（BiliConstants）
    implementation(project(":core:common"))

    // Protobuf 支持
    implementation(project(":infra:protobuf"))

    // Android 核心库
    implementation(libs.androidx.core.ktx)

    // 网络请求
    implementation(libs.okhttp)

    // 协程
    implementation(libs.kotlinx.coroutines.android)
}
