enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("spring") {
            from(files("gradle/spring.versions.toml"))
        }
        create("hikari") {
            from(files("gradle/hikari.versions.toml"))
        }
    }
}

rootProject.name = "octavius-driver"
include("driver")
include("hikari")
include("driver-spring-integration")
