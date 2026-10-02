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
    runtimeOnly(project(":infra:db-migrations"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.bootJar {
    archiveFileName = "ultimate-analytics.jar"
}

// ./gradlew :app:bootRun runs the `local` profile against `infra/scripts/dev-up.sh --db-only`,
// with the same config file and credentials (infra/docker/.env) as the compose stack.
tasks.bootRun {
    workingDir = rootDir
    environment("SPRING_CONFIG_ADDITIONAL_LOCATION", "optional:file:$rootDir/infra/config/")
    val envFile = rootDir.resolve("infra/docker/.env")
    if (envFile.isFile) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .forEach { environment(it.substringBefore("=").trim(), it.substringAfter("=").trim()) }
    }
}
