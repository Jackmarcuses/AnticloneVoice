plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("application")
}

group = "com.jackmarcus"
version = "1.0-SNAPSHOT"

// Repositories are managed in settings.gradle.kts

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.jbcrypt)
    implementation(libs.logback.classic)
    implementation(libs.firebase.admin)
    
    // Database (Explicitly using stable versions for build reliability)
    val exposed_version = "0.53.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    implementation(libs.h2)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.client.negotiation)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

application {
    mainClass.set("com.jackmarcus.backend.ApplicationKt")
}

kotlin {
    jvmToolchain(11)
}
