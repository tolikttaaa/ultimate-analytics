import com.github.gradle.node.npm.task.NpmTask

// React SPA (spec 10), built with npm through the node-gradle plugin; not a JVM module (spec 7.2).
// The app serves dist/ as static resources.
plugins {
    base
    alias(libs.plugins.node)
}

node {
    // Downloaded by Gradle, so the build needs no Node.js on the machine (CI, Docker).
    version = "24.21.0"
    download = true
    npmInstallCommand = "ci"
}

/** Everything the npm scripts read; the API types generated into src/api are excluded. */
val sources = files(
    fileTree("src") { exclude("api/schema.d.ts") },
    "public", "index.html", "openapi.json", "package.json", "package-lock.json", "vite.config.ts",
    "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json", ".oxlintrc.json",
)

/** An npm script with up-to-date checks: it runs again only when the sources change. */
fun npmScript(name: String, script: String, description: String) = tasks.register<NpmTask>(name) {
    this.description = description
    dependsOn(tasks.npmInstall)
    args = listOf("run", script)
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmBuild = npmScript("npmBuild", "build", "Generates the API types, type-checks and bundles the SPA into dist/.")
npmBuild.configure { outputs.dir("dist") }

for ((name, script) in listOf("npmTest" to "test", "npmLint" to "lint")) {
    val marker = layout.buildDirectory.file("$name.done")
    npmScript(name, script, "Runs `npm run $script`.").configure {
        outputs.file(marker)
        doLast { marker.get().asFile.writeText("ok") }
    }
}

tasks.assemble { dependsOn(npmBuild) }
tasks.check { dependsOn("npmTest", "npmLint") }

/** The built SPA for the app. */
val dist = configurations.create("dist") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(dist.name, layout.projectDirectory.dir("dist")) { builtBy(npmBuild) }
}
