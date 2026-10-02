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

/** The built SPA of :frontend, served as static resources (spec 7.1). */
val frontend = configurations.create("frontend") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    frontend(project(path = ":frontend", configuration = "dist"))

    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(project(":domain"))
    implementation(project(":fit-parser"))
    implementation(project(":analysis"))
    runtimeOnly(project(":infra:db-migrations"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.springdoc.openapi.webmvc.ui)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":fit-parser")))
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.processResources {
    from(frontend) {
        into("static")
    }
}

tasks.test {
    // The golden FIT files of spec 12 serve as real uploads in the integration tests.
    val goldenFitDir = rootDir.resolve("fit-parser/src/test/resources/fit")
    inputs.dir(goldenFitDir).withPropertyName("goldenFitFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("golden.fit.dir", goldenFitDir.absolutePath)
    // The OpenAPI snapshot the frontend generates its API types from (spec 10.3, 12); `-PupdateGolden` rewrites it.
    systemProperty("openapi.snapshot", rootDir.resolve("frontend/openapi.json").absolutePath)
    systemProperty("golden.update", providers.gradleProperty("updateGolden").isPresent)
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
