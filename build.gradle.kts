/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    group = "dev.animeplayer"
    version = providers.gradleProperty("animeplayer.version").getOrElse("0.1.0")
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                mavenLocal()
                maven {
                    name = "animeplayer"
                    url = uri(
                        providers.gradleProperty("animeplayer.repositoryUrl")
                            .getOrElse(layout.buildDirectory.dir("repo").get().asFile.toURI().toString()),
                    )
                }
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(this@subprojects.name)
                    description.set("animeplayer - multiplatform anime streaming library")
                    url.set("https://github.com/aufvim/animeplayer")
                    licenses {
                        license {
                            name.set("GNU General Public License v3.0")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("animeplayer")
                            name.set("animeplayer contributors")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/aufvim/animeplayer.git")
                        url.set("https://github.com/aufvim/animeplayer")
                    }
                }
            }
        }
    }
}

/**
 * Runs the contract tests against the real upstream site.
 *
 * ```
 * ./gradlew liveTest -Plive=true
 * ```
 */
tasks.register("liveTest") {
    group = "verification"
    description = "Runs the contract tests against the live upstream site."
    dependsOn(":sources:hianime:jvmTest")
}
