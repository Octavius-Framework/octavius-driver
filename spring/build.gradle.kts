plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("maven-publish")
}

dependencies {
    implementation(projects.driver)
    implementation(spring.spring.boot.starter.jdbc)
    implementation(hikari.hikaricp)

    testImplementation(spring.spring.boot.starter.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}
