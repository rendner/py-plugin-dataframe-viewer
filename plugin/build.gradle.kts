import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "1.0"
    // https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
    kotlin("jvm") version "1.4.0" // provided by 2020.3
    kotlin("plugin.serialization") version "1.4.0"
}

group = "cms.rendner.intellij"
version = "0.5.1"

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

    val exportTestDataPath = "$projectDir/src/test/resources/generated/"
    val exportTestErrorImagesPath = "$projectDir/src/test/resources/generated-error-images/"
    val dockeredPythonPath = "$projectDir/dockered-python"
    // order: oldest to latest
    val projectNamesOfSupportedPandasVersions = listOf("pandas_1.1", "pandas_1.2", "pandas_1.3")

    register<Exec>("buildPythonDockerImage") {
        description = "Builds the python docker image."
        group = "build"

        // Contains all pipenv environments
        val dockerContentPipenvEnvironmentsPath = "$dockeredPythonPath/content/pipenv_environments"
        // Contains the additional content of the pipenv environments (like Python source code, etc.)
        // The content is separated on purpose to profit from the Docker's layer caching. Without the separation
        // the pipenv environments are re-build whenever a source file has changed.
        val dockerContentMergeIntoPipenvEnvironmentsPath = "$dockeredPythonPath/content/merge_into_pipenv_environments"

        logger.lifecycle("copy 'pipenv environments' files")
        projectNamesOfSupportedPandasVersions.forEach { version ->
            val pythonProjectPath = "../projects/html_from_styler/${version}_styler"

            val pipFile = project.file("$pythonProjectPath/Pipfile")
            val pipFileLock = project.file("$pythonProjectPath/Pipfile.lock")
            if (pipFile.exists() && pipFileLock.exists()) {
                copy {
                    from(pipFile, pipFileLock)
                    into(project.file("$dockerContentPipenvEnvironmentsPath/$version"))
                }
            } else {
                throw GradleException("Incomplete Pipfiles, can't copy files for version: $version")
            }

            val exportData = project.file("$pythonProjectPath/export_data")
            if (exportData.exists() && exportData.isDirectory) {
                copy {
                    from(exportData)
                    into(project.file("$dockerContentMergeIntoPipenvEnvironmentsPath/$version/export_data/"))
                }
            } else {
                throw GradleException("No data to export, can't copy files for version: $version")
            }
        }

        logger.lifecycle("starting docker build")
        workingDir = project.file(dockeredPythonPath)
        commandLine("docker", "build", ".", "-t", "plugin-docker-python")
    }

    val integrationTests = mutableListOf<Task>()
    projectNamesOfSupportedPandasVersions.forEach { version ->
        register<Test>("integrationTest-$version") {
            description = "Runs integration test on pipenv environment: $version."
            group = "verification"
            shouldRunAfter("test")

            systemProperty("cms.rendner.dataframe.renderer.dockered.test.pipenv.environment", version)
            useJUnitPlatform {
                include("**/integration/**")
            }
            integrationTests.add(this)
        }
    }

    register<Test>("integrationTest-all") {
        description = "Runs all integration-test tasks."
        group = "verification"
        shouldRunAfter("test")

        var previousTask: Task? = null
        // run the latest first
        integrationTests.asReversed().forEach {
            dependsOn(it)
            if (previousTask != null) {
                it.shouldRunAfter(previousTask)
            }
            previousTask = it
        }
    }

    val exportTestDataTests = mutableListOf<Task>()
    projectNamesOfSupportedPandasVersions.forEach { version ->
        register<Test>("exportTestData-$version") {
            description = "Runs export-test-data on pipenv environment: $version."
            group = "generate"

            // todo: delete old data (implement after the "export-expected-css" is also automated)
            systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
            systemProperty("cms.rendner.dataframe.renderer.dockered.test.pipenv.environment", version)
            useJUnitPlatform {
                include("**/export/**")
            }
            exportTestDataTests.add(this)

            // todo: run "export-expected-css" in "doLast" to create expected css for re-generated html
        }
    }

    register<Test>("exportTestData-all") {
        description = "Runs all export-test-data tasks."
        group = "generate"

        var previousTask: Task? = null
        // run the latest first
        exportTestDataTests.asReversed().forEach {
            dependsOn(it)
            if (previousTask != null) {
                it.shouldRunAfter(previousTask)
            }
            previousTask = it
        }
    }

    test {
        systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
        systemProperty("cms.rendner.dataframe.renderer.export.test.error.image.dir", exportTestErrorImagesPath)
        useJUnitPlatform {
            exclude("**/integration/**")
            exclude("**/export/**")
        }
        failFast = true
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set(project.version.toString())
    }

    runIde {
        systemProperty("cms.rendner.dataframe.renderer.enable.test.data.export.action", true)
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
                throw GradleException("Missing file 'plugin_code' for version: $version")
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}