plugins {
    application
    id("me.champeau.jmh")
}

application {
    mainClass.set("com.argent.engine.bench.MatchingBenchmarkKt")
}

dependencies {
    implementation(project(":argent-common"))
}

jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(3)
    warmup.set("2s")
    iterations.set(5)
    fork.set(1)
    timeOnIteration.set("2s")
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("reports/jmh/results.json"))
}
