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
    implementation(project(":player"))
    implementation(project(":storage"))
    implementation(project(":sources:hianime"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.jdbc)
    implementation(libs.slf4j.simple)
}

application {
    mainClass.set("dev.animeplayer.gui.AnimeplayerWindowKt")
}
