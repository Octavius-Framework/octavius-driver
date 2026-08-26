plugins {
    alias(libs.plugins.kotlin.jvm)
    // The test fixtures declare @Serializable classes for the scanner to find.
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    // Exposed: the scan registers onto a client, and reports what it registered in the driver's own terms.
    api(projects.client)

    // The reason this is a module of its own. Walking a classpath correctly - jars inside jars, the module
    // path, a Spring Boot fat jar - is not something to hand-roll, and not something to put on everyone who
    // only wants to register their types by hand.
    implementation(libs.classgraph)

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(hikari.hikaricp)
    testImplementation(libs.logback.classic)
}
