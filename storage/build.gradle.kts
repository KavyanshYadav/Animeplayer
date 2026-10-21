import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
    `maven-publish`
}


kotlin {
    jvmToolchain(21)
    explicitApi()

    android {
        namespace = "dev.animeplayer.storage"
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


    // SQLDelight does not ship a JavaScript driver, so the storage module is limited to
    // the JVM and Android. Consumers on other platforms use the in-memory store from the
    // core module or bring their own implementation of the WatchHistoryStore interface.

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("AnimePlayerDatabase") {
            // The database class uses the package name, the generated queries use the
            // directory of the .sq file. Both end up in `dev.animeplayer.storage.db`.
            packageName.set("dev.animeplayer.storage.db")
            generateAsync.set(false)
        }
    }
}
