plugins {
    application
}

application {
    mainClass.set("com.argent.gateway.ApplicationKt")
}

dependencies {
    implementation(project(":argent-common"))
    implementation(project(":argent-engine"))
    implementation(project(":argent-ledger"))
    implementation(project(":argent-market"))
    implementation(project(":argent-risk"))

    implementation("io.ktor:ktor-server-core:3.0.1")
    implementation("io.ktor:ktor-server-netty:3.0.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-server-websockets:3.0.1")
    implementation("io.ktor:ktor-server-cors:3.0.1")
    implementation("io.ktor:ktor-server-call-logging:3.0.1")
    implementation("io.ktor:ktor-server-auth:3.0.1")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.1")
    implementation("io.ktor:ktor-server-status-pages:3.0.1")
    implementation("io.ktor:ktor-server-metrics-micrometer:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.6")
    implementation("io.micrometer:micrometer-core:1.16.6")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")

    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("com.zaxxer:HikariCP:6.0.0")
}
