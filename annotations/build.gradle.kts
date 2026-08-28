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
}

mavenPublishing {
    pom {
        name.set("Octavius Annotations")
        description.set(
            "The annotation declarations Octavius reads off your own classes, and nothing else. Kotlin Multiplatform, " +
            "so that commonMain can carry them."
        )
    }
}
