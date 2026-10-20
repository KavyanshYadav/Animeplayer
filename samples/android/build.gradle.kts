plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.animeplayer.sample.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.animeplayer.sample.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":network"))
    implementation(project(":player"))
    implementation(project(":sources:hianime"))

    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.kotlinx.coroutines.core)
}
