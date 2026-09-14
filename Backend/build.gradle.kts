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
    
    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    
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
