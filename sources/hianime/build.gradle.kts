import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}


kotlin {
    jvmToolchain(21)
    explicitApi()

    android {
        namespace = "dev.animeplayer.sources.hianime"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }


    js {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(project(":network"))
        }
    }
}

// The golden payloads live in a single place at the repository root so that the fixture
// based regression tests always run against the real upstream markup.
tasks.withType<Test>().configureEach {
    systemProperty("animeplayer.fixturesDir", rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath)
    // The live contract tests only run when the `live` property is set, so that the
    // regular build never touches the upstream site.
    systemProperty("animeplayer.live", providers.gradleProperty("live").getOrElse("false"))
}
