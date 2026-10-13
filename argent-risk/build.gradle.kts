dependencies {
    implementation(project(":argent-common"))
    implementation(project(":argent-ledger"))
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("com.zaxxer:HikariCP:6.0.0")
}
