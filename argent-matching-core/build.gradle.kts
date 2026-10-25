plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":argent-common"))
    api(project(":argent-engine"))
    api(project(":argent-ledger"))
}

publishing {
    publications {
        create<MavenPublication>("matchingCore") {
            from(components["java"])
            artifactId = "argent-matching-core"
        }
    }
}
