import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.dokka)
}

allprojects {
    group = "io.github.octavius-framework"
    version = "0.8.9"

    repositories {
        mavenCentral()
    }
}

dependencies {
    dokka(projects.driver)
    dokka(projects.hikari)
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

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }

        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }


    }
}
