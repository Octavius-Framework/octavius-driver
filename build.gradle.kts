import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

allprojects {
    group = "io.github.octavius-framework"
    version = "0.9.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }


        apply(plugin = "com.vanniktech.maven.publish")

        extensions.configure<MavenPublishBaseExtension> {
            coordinates(group.toString(), project.name, version.toString())

            pom {
                name.set(project.name)
                description.set("Octavius Driver - ${project.name}")
                // description
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

        tasks.withType<Test> {
            useJUnitPlatform()
        }


    }
}
