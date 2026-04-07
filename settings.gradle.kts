pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

/* includeBuild("../DanmakuFlameMaster") {
    dependencySubstitution {
        substitute(module("com.github.naaammme:DanmakuFlameMaster"))
            .using(project(":DanmakuFlameMaster"))
    }
}
*/

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.naaammme")
            }
        }
    }
}

rootProject.name = "bbspace"
include(":app")

// Infra 层
include(":infra:protobuf")
include(":infra:crypto")
include(":infra:network-http")
include(":infra:network-grpc")
include(":infra:coldstart")
include(":infra:player")

// Core 层
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:navigation")

// Feature 层
include(":feature:home")
include(":feature:video")
include(":feature:dynamic")
include(":feature:search")
include(":feature:user")
include(":feature:auth")
include(":feature:settings")

