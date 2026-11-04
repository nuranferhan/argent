plugins {
    `java-library`
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

dependencies {
    api(project(":argent-common"))
    implementation("org.jetbrains.compose.runtime:runtime:1.7.3")
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-cio:3.0.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-client-websockets:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
}

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
