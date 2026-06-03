plugins {
    kotlin("jvm") version "2.3.10"
    application
}

application {
    mainClass.set("io.github.octaviusframework.MainKt")
}

group = "io.github.octaviusframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}