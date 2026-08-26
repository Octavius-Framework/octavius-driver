import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish) apply false
}

allprojects {
    group = "io.github.octavius-framework"
    version = "0.9.8-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

dependencies {
    dokka(projects.annotations)
    dokka(projects.driver)
    dokka(projects.client)
    dokka(projects.clientScanner)
    dokka(projects.hikariIntegrationTests)
    dokka(projects.driverSpringIntegration)
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    extensions.configure<DokkaExtension> {
        moduleName.set(name)

        dokkaSourceSets.configureEach {
            documentedVisibilities.set(
                setOf(
                    VisibilityModifier.Public,
                    VisibilityModifier.Protected,
                    VisibilityModifier.Internal
                )
            )
            skipEmptyPackages.set(true)
        }
    }

    // The annotations module is the one multiplatform project here, so its JVM target is set through the
    // multiplatform extension rather than the JVM one below.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(21)
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
        
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    val publishedProjects = listOf("annotations", "driver", "driver-spring-integration", "client", "client-scanner")

    if (publishedProjects.contains(project.name)) {
        apply(plugin = "com.vanniktech.maven.publish")

        extensions.configure<MavenPublishBaseExtension> {
            coordinates(group.toString(), project.name, version.toString())

            pom {
                val isAnnotations = project.name == "annotations"
                val isClient = project.name == "client" || project.name.startsWith("client-")
                name.set(
                    when {
                        isAnnotations -> "Octavius - ${project.name}"
                        isClient -> "Octavius Client - ${project.name}"
                        else -> "Octavius Driver - ${project.name}"
                    }
                )
                description.set(
                    when {
                        isAnnotations ->
                            "Annotations Octavius reads off your own classes, multiplatform so that shared code can carry them."
                        isClient -> "SQL-first data access for Kotlin and PostgreSQL, built on the Octavius driver."
                        else -> "Kotlin-first PostgreSQL driver built on Virtual Threads and the raw wire protocol."
                    }
                )
                url.set("https://github.com/octavius-framework/octavius-driver")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("PolskiAnonim")
                        name.set("PolskiAnonim")
                        email.set("115878440+PolskiAnonim@users.noreply.github.com")
                        organization.set("Octavius Framework")
                        organizationUrl.set("https://github.com/Octavius-Framework")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Octavius-Framework/octavius-driver.git")
                    developerConnection.set("scm:git:ssh://github.com/Octavius-Framework/octavius-driver.git")
                    url.set("https://github.com/Octavius-Framework/octavius-driver")
                }
            }

            publishToMavenCentral()

            val isSigningKeyPresent = project.hasProperty("signingInMemoryKey") || project.hasProperty("signingKey") || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
            if (isSigningKeyPresent) {
                signAllPublications()
            }
        }
    }
}
