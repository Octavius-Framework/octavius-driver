plugins {
    alias(libs.plugins.kotlin.jvm)
    id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    implementation(project(":driver"))
    implementation("org.postgresql:postgresql:42.7.3") // Latest pgjdbc
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

}

jmh {
    jmhVersion.set("1.37")

    // gc reports allocation per operation; stack samples thread states and the frames underneath
    // them, which is where a difference too small for the clock still shows up.
    profilers.add("gc")
    profilers.add("stack")

    providers.gradleProperty("jmh").orNull?.let { includes.add(it) }
}
