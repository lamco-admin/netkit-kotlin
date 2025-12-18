plugins {
    kotlin("multiplatform") version "2.2.21" apply false
    id("org.jetbrains.dokka") version "2.0.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
}

allprojects {
    group = "io.lamco.netkit"
    version = "1.0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvmToolchain(25)

        jvm {
            testRuns["test"].executionTask.configure {
                useJUnitPlatform()
            }
        }

        sourceSets {
            commonMain {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                }
            }

            commonTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }

            jvmTest {
                dependencies {
                    implementation("org.junit.jupiter:junit-jupiter:5.10.1")
                    implementation("io.mockk:mockk:1.13.8")
                    implementation("io.kotest:kotest-runner-junit5:5.8.0")
                    implementation("io.kotest:kotest-assertions-core:5.8.0")
                    implementation("io.kotest:kotest-property:5.8.0")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
                }
            }
        }
    }

    tasks.named<Test>("jvmTest") {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }

    tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
        dokkaSourceSets {
            configureEach {
                displayName.set("NetKit ${project.name}")

                includes.from("README.md")

                perPackageOption {
                    matchingRegex.set(".*\\.internal.*")
                    suppress.set(true)
                }

                externalDocumentationLink {
                    url.set(uri("https://kotlinlang.org/api/latest/jvm/stdlib/").toURL())
                }
                externalDocumentationLink {
                    url.set(uri("https://kotlinlang.org/api/kotlinx.coroutines/").toURL())
                }
            }
        }
    }

    // ktlint configuration (permanent)
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.7.0")
        debug.set(false)
        verbose.set(false)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(false)
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }

    configure<PublishingExtension> {
        publications {
            withType<MavenPublication> {
                pom {
                    name.set("NetKit ${project.name}")
                    description.set("Kotlin Multiplatform networking toolkit - ${project.name} module")
                    url.set("https://github.com/lamco-admin/netkit-kotlin")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("lamco")
                            name.set("Greg Lamberson")
                            email.set("greg@lamco.io")
                            organization.set("Lamco Development")
                            organizationUrl.set("https://lamco.io")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/lamco-admin/netkit-kotlin.git")
                        developerConnection.set("scm:git:ssh://github.com:lamco-admin/netkit-kotlin.git")
                        url.set("https://github.com/lamco-admin/netkit-kotlin")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "Sonatype"
                val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

                credentials {
                    username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                    password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }

    configure<SigningExtension> {
        if (findProperty("signing.keyId") != null) {
            sign(the<PublishingExtension>().publications)
        }
    }
}

tasks.register("cleanAll") {
    description = "Clean all subprojects"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("buildAll") {
    description = "Build all subprojects"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("testAll") {
    description = "Test all subprojects"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("jvmTest") })
}

tasks.register("dokkaHtmlAll") {
    description = "Generate Dokka documentation for all subprojects"
    group = "documentation"
    dependsOn(subprojects.map { it.tasks.named("dokkaHtml") })
}

// ktlint aggregated tasks
tasks.register("ktlintCheckAll") {
    description = "Run ktlint check for all subprojects"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("ktlintCheck") })
}

tasks.register("ktlintFormatAll") {
    description = "Auto-format code with ktlint for all subprojects"
    group = "formatting"
    dependsOn(subprojects.map { it.tasks.named("ktlintFormat") })
}

tasks.register("lintAll") {
    description = "Run ktlint for all subprojects"
    group = "verification"
    dependsOn("ktlintCheckAll")
}
