plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Exposed: a code migration is handed a session, so the driver's types are in this module's own signatures.
    api(projects.driver)

    // The reason this is not hand-rolled: a classpath path can mean a jar inside a jar, the module path or a
    // Spring Boot fat jar, and walking that correctly is not a weekend's work.
    implementation(libs.classgraph)

    implementation(libs.kotlin.logging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
}
