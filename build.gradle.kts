plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("me.champeau.jmh") version "0.7.3" apply false
}

allprojects {
    group = "com.argent"
    version = "1.0.0"

    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

subprojects {
    if (name == "argent-terminal" || name == "argent-android") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }

    dependencies {
        "implementation"(kotlin("stdlib"))
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        "implementation"("org.slf4j:slf4j-api:2.0.16")
        "implementation"("ch.qos.logback:logback-classic:1.5.12")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test")
        "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        "testImplementation"("io.kotest:kotest-runner-junit5:5.9.1")
        "testImplementation"("io.kotest:kotest-assertions-core:5.9.1")
        "testImplementation"("io.kotest:kotest-property:5.9.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
