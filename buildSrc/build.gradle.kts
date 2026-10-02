plugins {
    // Precompiled convention plugins live in src/main/kotlin as *.gradle.kts files.
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Plugins on this classpath can be applied by id, without a version, in every module.
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.spring.boot.gradle.plugin)
}
