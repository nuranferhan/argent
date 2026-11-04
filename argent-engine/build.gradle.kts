plugins {
    `java-library`
    id("maven-publish")
    application
    id("me.champeau.jmh")
}

application {
    mainClass.set("com.argent.engine.bench.MatchingBenchmarkKt")
}

dependencies {
    api(project(":argent-common"))
}

tasks.register<JacocoCoverageVerification>("coverageVerification") {
    dependsOn(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    // JMH fixtures are measurement tooling, not production engine code.
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("**/bench/**")
        },
    )
    sourceDirectories.setFrom(layout.projectDirectory.dir("src/main/kotlin"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

jmh {
    jmhVersion.set("1.37")
    includes.set(listOf(".*addLimitOrderToBookOfConfiguredSize.*"))
    warmupIterations.set(3)
    warmup.set("2s")
    iterations.set(5)
    fork.set(1)
    timeOnIteration.set("2s")
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("reports/jmh/results.json"))
}

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
