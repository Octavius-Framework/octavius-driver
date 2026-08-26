plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    // Part of the public contract, not an implementation detail: `@PgName` goes on the caller's own classes,
    // so anyone using registerAutoComposite has to be able to write it without adding a dependency by hand.
    api(projects.annotations)

    // Exposed in the public API: SharedFlow notifications and OctaviusDispatchers,
    // JsonElement for json/jsonb, and kotlinx.datetime types for date/time columns.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
}
