pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "argent"

include(
    "argent-common",
    "argent-engine",
    "argent-ledger",
    "argent-market",
    "argent-risk",
    "argent-gateway",
    "argent-terminal",
    "argent-android"
)
