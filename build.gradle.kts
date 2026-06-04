plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.octaviusframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}