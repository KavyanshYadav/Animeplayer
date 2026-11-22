plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val tmdbReadAccessToken = providers.environmentVariable("TMDB_READ_ACCESS_TOKEN").orElse(
    providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText.map { contents ->
        contents.lineSequence().map(String::trim)
            .firstOrNull { it.substringBefore('=').trim() == "TMDB_READ_ACCESS_TOKEN" }
            ?.substringAfter('=')?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
    },
).orElse("").map { token ->
    val value = token.trim()
    require(value.isEmpty() || value.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid TMDB_READ_ACCESS_TOKEN format" }
    value
}

android {
    namespace = "dev.animeplayer.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.animeplayer.tv"
        // API 26 supports the bundled variable typeface and the platform date/time APIs.
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"${tmdbReadAccessToken.get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation(project(":core"))
    implementation(project(":network"))
    implementation(project(":storage"))
    implementation(project(":player"))
    implementation(project(":sources:hianime"))
    implementation(project(":sources:schedule"))
    implementation(project(":sources:anilist"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.animation)
    implementation(libs.tv.material)
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okio)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1") {
        // NewPipe uses Rhino directly to decode signatures. Android lacks JSR 223.
        exclude(group = "org.mozilla", module = "rhino-engine")
    }
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}
