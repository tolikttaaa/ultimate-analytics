// FIT file parsing into domain types. No Spring, no database (spec 7.2).
plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
}

dependencies {
    api(project(":domain"))
    implementation(libs.garmin.fit)
}

tasks.test {
    // Golden files (spec 12): `./gradlew :fit-parser:test -PupdateGolden` rewrites the approved snapshots.
    systemProperty("golden.dir", layout.projectDirectory.dir("src/test/resources/fit").asFile.absolutePath)
    systemProperty("golden.update", providers.gradleProperty("updateGolden").isPresent)
}
