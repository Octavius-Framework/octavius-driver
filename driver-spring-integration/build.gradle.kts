plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    api(projects.driver)
    api(spring.spring.boot.starter.jdbc)
    implementation(hikari.hikaricp)
    implementation(libs.kotlin.logging)

    testImplementation(spring.spring.boot.starter.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

