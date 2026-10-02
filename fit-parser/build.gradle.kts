// FIT file parsing into domain types. No Spring, no database (spec 7.2).
plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
}

dependencies {
    api(project(":domain"))
}
