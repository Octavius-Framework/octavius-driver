plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                // In the public API: the infinity markers are kotlinx.datetime values, so a consumer naming
                // one needs the library on its own compile classpath anyway.
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
            "The types your own classes are written in terms of: the annotations Octavius reads off them, the " +
            "case converter both sides name things with, and the values standing for PostgreSQL's infinite " +
            "dates. Kotlin Multiplatform, so that commonMain can carry them."
        )
    }
}
