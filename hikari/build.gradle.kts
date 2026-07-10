plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("maven-publish")
}

dependencies {
    implementation(project(":driver"))
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}
