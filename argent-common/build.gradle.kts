plugins { id("maven-publish") }
dependencies {
    // Common is pure domain + events
}

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
