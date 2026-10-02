plugins {
    // Downloads the JDK toolchain (21) if it is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ultimate-analytics"

// Module dependency rules: docs/SPEC.md section 7.2.
include(
    "domain",
    "fit-parser",
    "analysis",
    "infra:db-migrations",
    "app",
    // React SPA, built with npm (not a JVM module)
    "frontend",
)
