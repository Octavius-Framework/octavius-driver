plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    // `BigDecimal` is an expect class, which the compiler still reports as Beta on every build. The one
    // construct the warning is about is the one this module needs: a JVM typealias to `java.math.BigDecimal`
    // is what keeps the shared type and the type the driver decodes into the same class.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    js {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                // Both are in the public API: the serializers are `KSerializer`s and the infinity markers are
                // kotlinx.datetime values. A consumer writing `@Contextual val paid: LocalDate` needs each of
                // them on its own compile classpath anyway.
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Octavius PostgreSQL Model")
        description.set(
            "The types your own classes are written in terms of: the annotations Octavius reads off them, a " +
            "multiplatform BigDecimal, the serializers that keep numeric precision and PostgreSQL's infinity " +
            "intact through JSON, and the case converter both sides name things with. Kotlin Multiplatform, so " +
            "that commonMain can carry them."
        )
    }
}
