plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "io.github.octaviusframework"
version = "0.4.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
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