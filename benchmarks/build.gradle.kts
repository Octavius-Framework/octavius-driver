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
    iterations.set(5)
    warmupIterations.set(3)
    fork.set(1)
    threads.set(1)
    jmhVersion.set("1.37")
}
