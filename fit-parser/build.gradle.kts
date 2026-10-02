// FIT file parsing into domain types. No Spring, no database (spec 7.2).
plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    // Shares the golden-file helper with the analysis tests.
    `java-test-fixtures`
}

dependencies {
    api(project(":domain"))
    implementation(libs.garmin.fit)

    testFixturesImplementation(libs.kotest.assertions.core)
}

tasks.test {
    // Golden files (spec 12): `./gradlew test -PupdateGolden` rewrites the approved snapshots.
    systemProperty("golden.fit.dir", layout.projectDirectory.dir("src/test/resources/fit").asFile.absolutePath)
    systemProperty("golden.snapshot.dir", layout.projectDirectory.dir("src/test/resources/fit/expected").asFile.absolutePath)
    systemProperty("golden.update", providers.gradleProperty("updateGolden").isPresent)
}
