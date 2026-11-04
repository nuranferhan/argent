plugins { id("maven-publish") }
dependencies {
    implementation(project(":argent-common"))
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.register<JacocoCoverageVerification>("coverageVerification") {
    dependsOn(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    classDirectories.setFrom(layout.buildDirectory.dir("classes/kotlin/main"))
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

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
