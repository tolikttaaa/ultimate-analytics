// Pure analysis pipeline. No Spring, no I/O, no FIT SDK (spec 7.2).
plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
}

dependencies {
    api(project(":domain"))

    // Golden tests only: parse the golden FIT files (spec 12). The main code never sees the FIT SDK.
    testImplementation(testFixtures(project(":fit-parser")))
}

tasks.test {
    // Golden files (spec 12): `./gradlew test -PupdateGolden` rewrites the approved snapshots.
    val goldenFitDir = rootDir.resolve("fit-parser/src/test/resources/fit")
    inputs.dir(goldenFitDir).withPropertyName("goldenFitFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("golden.fit.dir", goldenFitDir.absolutePath)
    systemProperty("golden.snapshot.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    systemProperty("golden.update", providers.gradleProperty("updateGolden").isPresent)
}
