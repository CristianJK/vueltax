import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "app.vueltax"
version = "0.1.0"

application {
    mainClass = "app.vueltax.backend.MainKt"
    applicationName = "vueltax-backend"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.serialization.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    implementation(libs.json.schema.validator)
    implementation(libs.logback)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}

// El schema de reglas vive en rules/schema.json (única fuente de verdad) y se empaqueta
// en el jar para validar los rule sets que llegan por el endpoint de administración.
val rulesDir = layout.projectDirectory.dir("../rules")

tasks.processResources {
    from(rulesDir.file("schema.json")) {
        into("rules")
    }
}

tasks.test {
    useJUnitPlatform()
    // Las pruebas validan los archivos reales de rules/.
    inputs.dir(rulesDir).withPropertyName("rulesDir")
    systemProperty("vueltax.rulesDir", rulesDir.asFile.absolutePath)
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }
}
