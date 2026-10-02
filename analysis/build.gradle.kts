// Pure analysis pipeline. No Spring, no I/O, no FIT SDK (spec 7.2).
plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
}

dependencies {
    api(project(":domain"))
}
