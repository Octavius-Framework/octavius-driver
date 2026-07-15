import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

allprojects {
    group = "io.github.octavius-framework"
    version = "0.5.2"

    repositories {
        mavenCentral()
    }
}

subprojects {
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
