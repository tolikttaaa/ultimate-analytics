import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Spring Boot application. No analysis algorithms, SQL migrations or environment-specific config (spec 7.2).
plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(project(":domain"))
    implementation(project(":fit-parser"))
    implementation(project(":analysis"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.bootJar {
    archiveFileName = "ultimate-analytics.jar"
}
