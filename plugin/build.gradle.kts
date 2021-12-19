import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "1.0"
    // https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
    kotlin("jvm") version "1.4.0" // provided by 2020.3
    kotlin("plugin.serialization") version "1.4.0"
}

group = "cms.rendner.intellij"
version = "0.5.1"

val exportTestDataPath = "$projectDir/src/test/resources/generated/"
val exportTestErrorImagesPath = "$projectDir/src/test/resources/generated-error-images/"
val projectNamesOfSupportedPandasVersions = listOf("pandas_1.1", "pandas_1.2", "pandas_1.3")

repositories {
    mavenCentral()
}

dependencies {
    // is provided by the intellij instance which runs the plugin
    compileOnly(kotlin("stdlib"))

    implementation("org.jsoup:jsoup:1.14.3")
    implementation("net.sourceforge.cssparser:cssparser:0.9.29")
    implementation("org.beryx:awt-color-factory:1.0.2")

    // https://github.com/junit-team/junit5-samples/blob/r5.8.1/junit5-jupiter-starter-gradle-kotlin/build.gradle.kts
    testImplementation(platform("org.junit:junit-bom:5.8.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("org.assertj:assertj-core:3.21.0")

    // latest usable version for kotlin version 1.4.0
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
// https://intellij-support.jetbrains.com/hc/en-us/community/posts/206590865-Creating-PyCharm-plugins
intellij {
    plugins.add("python-ce") // is required even if we specify a pyCharm ide
    version.set("2020.3")
    type.set("PC")
    downloadSources.set(false)
    updateSinceUntilBuild.set(false)
}

// https://github.com/nazmulidris/idea-plugin-example/blob/main/build.gradle.kts
tasks {

    register<Exec>("buildPythonDockerImage") {
        description = "Builds the python docker image for the integration tests."
        group = "build"

        projectNamesOfSupportedPandasVersions.forEach { version ->
            val pipFile = project.file("../projects/python_plugin_code/${version}_code/Pipfile")
            val pipFileLock = project.file("../projects/python_plugin_code/${version}_code/Pipfile.lock")
            if (pipFile.exists() && pipFileLock.exists()) {
                copy {
                    from(pipFile, pipFileLock)
                    into(project.file("integration-test/dockered-python/$version"))
                }
            } else {
                logger.error("Incomplete Pipfiles, can't copy files for version: $version")
            }
        }

        workingDir = project.file("integration-test/dockered-python")
        commandLine("docker", "build", ".", "-t", "plugin-docker-python")
    }

    test {
        systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
        systemProperty("cms.rendner.dataframe.renderer.export.test.error.image.dir", exportTestErrorImagesPath)
        useJUnitPlatform {
            exclude("**/integration/**")
        }
        failFast = true
    }

    val integrationTests = mutableListOf<Task>()
    projectNamesOfSupportedPandasVersions.forEach { version ->
        register<Test>("integrationTest-$version") {
            description = "Runs integration test with pipenv environment for $version."
            group = "verification"
            shouldRunAfter("test")

            systemProperty("cms.rendner.dataframe.renderer.integration.test", true)
            systemProperty("cms.rendner.dataframe.renderer.integration.test.pipenv.environment", version)
            useJUnitPlatform {
                include("**/integration/**")
            }
            integrationTests.add(this)
        }
    }

    register<Test>("integrationTest-all") {
        description = "Runs all integration tests."
        group = "verification"
        shouldRunAfter("test")

        var previousTask: Task? = null
        integrationTests.forEach {
            dependsOn(it)
            if (previousTask != null) {
                it.shouldRunAfter(previousTask)
            }
            previousTask = it
        }
    }

    // don't force it yet
    /*
    check {
        dependsOn("integrationTest")
    }*/

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set(project.version.toString())
    }

    runIde {
        systemProperty("cms.rendner.dataframe.renderer.enable.test.data.export", true)
        systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
        // ideDir.set(File("/snap/intellij-idea-community/current"))
    }

    processResources {
        logger.lifecycle("copy 'plugin_code' files")
        projectNamesOfSupportedPandasVersions.forEach { version ->
            val pluginCode = project.file("../projects/python_plugin_code/${version}_code/generated/plugin_code")
            if (pluginCode.exists()) {
                copy {
                    from(pluginCode)
                    into(project.file("src/main/resources/$version"))
                }
            } else {
                logger.error("Missing file 'plugin_code' for version: $version")
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}