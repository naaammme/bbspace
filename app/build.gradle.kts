plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val signingStoreFile: String? = providers.gradleProperty("SIGNING_STORE_FILE").orNull
    ?: System.getenv("SIGNING_STORE_FILE")
val signingStorePassword: String? = providers.gradleProperty("SIGNING_STORE_PASSWORD").orNull
    ?: System.getenv("SIGNING_STORE_PASSWORD")
val signingKeyAlias: String? = providers.gradleProperty("SIGNING_KEY_ALIAS").orNull
    ?: System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword: String? = providers.gradleProperty("SIGNING_KEY_PASSWORD").orNull
    ?: System.getenv("SIGNING_KEY_PASSWORD")
    ?: signingStorePassword
val hasReleaseSigning: Boolean =
    !signingStoreFile.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.naaammme.bbspace"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.naaammme.bbspace"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(project(":feature:home"))
    implementation(project(":feature:dynamic"))
    implementation(project(":feature:search"))
    implementation(project(":feature:space"))
    implementation(project(":feature:user"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:bbspace"))
    implementation(project(":feature:history"))
    implementation(project(":feature:favorite"))
    implementation(project(":feature:comment"))
    implementation(project(":feature:video"))
    implementation(project(":feature:live"))
    implementation(project(":feature:download"))
    implementation(project(":feature:webview"))
    implementation(project(":feature:listen"))
    implementation(project(":feature:im"))

    implementation(project(":core:auth"))
    implementation(project(":core:comment"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:common"))
    implementation(project(":core:download"))
    implementation(project(":core:feed"))
    implementation(project(":core:history"))
    implementation(project(":core:im"))
    implementation(project(":core:live"))
    implementation(project(":core:playback"))
    implementation(project(":core:settings"))
    implementation(project(":core:video"))

    implementation(project(":infra:coldstart"))
    implementation(project(":infra:crypto"))
    implementation(project(":infra:network-http"))
    implementation(project(":infra:network-grpc"))
    implementation(project(":infra:player"))

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media3.session)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("dagger.hilt.disableModulesHaveInstallInCheck", "true")
    arg("dagger.fastInit", "enabled")
}

hilt {
    enableAggregatingTask = true
}
