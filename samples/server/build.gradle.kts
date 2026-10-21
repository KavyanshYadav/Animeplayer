plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation(project(":network"))
    implementation(project(":proxy"))
    implementation(project(":storage"))
    implementation(project(":sources:hianime"))
    implementation(project(":sources:schedule"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.jdbc)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.simple)
}

application {
    mainClass.set("dev.animeplayer.server.ServerKt")
}

/**
 * A runtime image of the JVM for the sidecar.
 *
 * The desktop application ships the server next to itself, so it cannot count on a JVM
 * being installed on the machine. `jlink` builds a small runtime with only the modules
 * the server needs.
 */
val jreImage by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds a runtime image of the JVM for the sidecar."

    val javaHome = System.getProperty("java.home")
    val output = layout.buildDirectory.dir("jre")
    val modules = listOf(
        "java.base",
        "java.datatransfer",
        "java.desktop",
        "java.instrument",
        "java.logging",
        "java.management",
        "java.naming",
        "java.net.http",
        "java.prefs",
        "java.rmi",
        "java.scripting",
        "java.security.jgss",
        "java.sql",
        "java.transaction.xa",
        "java.xml",
        "jdk.crypto.ec",
        "jdk.unsupported",
        "jdk.zipfs",
    ).joinToString(",")

    inputs.property("modules", modules)
    outputs.dir(output)

    doFirst {
        // jlink refuses to write into an existing directory.
        output.get().asFile.deleteRecursively()
    }

    commandLine(
        "$javaHome/bin/jlink",
        "--add-modules", modules,
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress=2",
        "--output", output.get().asFile.absolutePath,
    )
}
