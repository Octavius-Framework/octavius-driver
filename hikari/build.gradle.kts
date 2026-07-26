plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("maven-publish")
}

dependencies {
    implementation(projects.driver)
    implementation(hikari.hikaricp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}
