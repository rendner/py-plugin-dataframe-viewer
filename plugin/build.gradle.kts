import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("idea")
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
    plugins.add("python-ce") // is required even if we specify a PyCharm IDE
    version.set("2020.3")
    type.set("PC")
    downloadSources.set(false)
    updateSinceUntilBuild.set(false)
}

val testDockeredSourceSet by sourceSets.register("test-dockered") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations {
    getByName(testDockeredSourceSet.implementationConfigurationName) { extendsFrom(testImplementation.get()) }
    getByName(testDockeredSourceSet.runtimeOnlyConfigurationName) { extendsFrom(testRuntimeOnly.get()) }
}

idea {
    module {
        testSourceDirs = testSourceDirs.plus(testDockeredSourceSet.allJava.srcDirs)
        testResourceDirs = testResourceDirs.plus(testDockeredSourceSet.resources.srcDirs)
    }
}

// https://github.com/nazmulidris/idea-plugin-example/blob/main/build.gradle.kts
tasks {

    data class PythonDockerImage(val pythonVersion: String, val pipenvEnvironments: List<String>) {
        val dockerImageName = "plugin-docker-$pythonVersion"
    }
    val pythonDockerImages = listOf(
        // order of python versions: latest to oldest
        // order of pipenv environments: latest to oldest
        PythonDockerImage("python_3.8", listOf("pandas_1.4")),
        PythonDockerImage("python_3.7", listOf("pandas_1.3", "pandas_1.2", "pandas_1.1")),
    )
    // todo: use imageNames from "pythonDockerImages"
    val pythonDockerImageName = "plugin-docker-python"
    val exportTestDataPath = "$projectDir/src/test/resources/generated/"
    val exportTestErrorImagesPath = "$projectDir/src/test/resources/generated-error-images/"
    // order: latest to oldest
    // todo: use "pythonDockerImages"
    val projectNamesOfSupportedPandasVersions = listOf("pandas_1.4", "pandas_1.3", "pandas_1.2", "pandas_1.1")

    register<DefaultTask>("buildPythonDockerImages") {
        description = "Builds the python docker images."
        group = "docker"

        val dockeredPythonPath = "$projectDir/dockered-python"
        doLast {
            pythonDockerImages.forEach { entry ->

                val pythonContentPath = "$dockeredPythonPath/${entry.pythonVersion}/content/"
                // Folder for all pipenv environments
                val dockerContentPipenvEnvironmentsPath = "$pythonContentPath/pipenv_environments"
                // Folder for the additional content of the pipenv environments (like Python source code, etc.)
                // The content is separated on purpose to profit from the Docker's layer caching. Without the separation
                // the pipenv environments are re-build whenever a source file has changed.
                val dockerContentMergeIntoPipenvEnvironmentsPath = "$pythonContentPath/merge_into_pipenv_environments"

                entry.pipenvEnvironments.forEach { pipEnvEnvironment ->

                    val pythonProjectPath = "../projects/html_from_styler/${pipEnvEnvironment}_styler"
                    val pipFile = project.file("$pythonProjectPath/Pipfile")
                    val pipFileLock = project.file("$pythonProjectPath/Pipfile.lock")

                    if (pipFile.exists() && pipFileLock.exists()) {
                        copy {
                            from(pipFile, pipFileLock)
                            into(project.file("$dockerContentPipenvEnvironmentsPath/$pipEnvEnvironment"))
                        }
                    } else {
                        throw GradleException("Incomplete Pipfiles, can't copy files for environment: $pipEnvEnvironment")
                    }

                    val exportData = project.file("$pythonProjectPath/export_data")
                    if (exportData.exists() && exportData.isDirectory) {
                        copy {
                            from(exportData)
                            into(project.file("$dockerContentMergeIntoPipenvEnvironmentsPath/$pipEnvEnvironment/export_data/"))
                        }
                    } else {
                        throw GradleException("No data to export, can't copy files for environment: $pipEnvEnvironment")
                    }
                }

                exec {
                    workingDir = file("$dockeredPythonPath/${entry.pythonVersion}")
                    executable = "docker"
                    args("build", ".", "-t", entry.dockerImageName)
                }
            }
        }
    }

    register<DefaultTask>("killAllPythonContainers") {
        description = "Kills all python docker containers (cleanup task)."
        group = "docker"

        doLast {
            val containerIdsResult = ByteArrayOutputStream().use { outputStream ->
                exec {
                    executable = "docker"
                    args("ps", "-aq", "--filter", "ancestor=$pythonDockerImageName")
                    standardOutput = outputStream
                }
                outputStream.toString().trim()
            }

            if (containerIdsResult.isNotEmpty()) {
                containerIdsResult.split(System.lineSeparator()).let { ids ->
                    logger.lifecycle("killing containers: $ids")
                    exec {
                        executable = "docker"
                        args(mutableListOf("rm", "-f").apply { addAll(ids) })
                    }
                }
            } else {
                logger.lifecycle("no running containers found for image $pythonDockerImageName")
            }
        }
    }

    val integrationTestTasks = mutableListOf<Task>()
    projectNamesOfSupportedPandasVersions.forEach { version ->
        register<Test>("integrationTest_$version") {
            description = "Runs integration test on pipenv environment: $version."
            group = "verification"

            testClassesDirs = testDockeredSourceSet.output.classesDirs
            classpath = testDockeredSourceSet.runtimeClasspath

            systemProperty("cms.rendner.dataframe.renderer.dockered.test.pipenv.environment", version)
            useJUnitPlatform {
                include("**/integration/**")
            }

            shouldRunAfter(test)
            if (integrationTestTasks.isNotEmpty()) {
                shouldRunAfter(integrationTestTasks.last())
            }
            integrationTestTasks.add(this)

            afterTest(KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
                val clsName = descriptor.parent?.displayName ?: descriptor.className
                println("\t$clsName > ${descriptor.name}: ${result.resultType}")
            }))
        }
    }

    register<DefaultTask>("integrationTest_all") {
        description = "Runs all integrationTest tasks."
        group = "verification"
        dependsOn(integrationTestTasks)
    }

    val generateTestDataTasks = mutableListOf<Task>()
    projectNamesOfSupportedPandasVersions.forEach { version ->

        val deleteTestData by register<Delete>("deleteTestData_$version") {
            delete("$exportTestDataPath$version")
        }

        val exportTestData by register<Test>("exportTestData_$version") {
            testClassesDirs = testDockeredSourceSet.output.classesDirs
            classpath = testDockeredSourceSet.runtimeClasspath

            systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
            systemProperty("cms.rendner.dataframe.renderer.dockered.test.pipenv.environment", version)
            useJUnitPlatform {
                include("**/export/**")
            }
            shouldRunAfter(deleteTestData)
        }

        val extractComputedCSS by register<Exec>("extractComputedCSS_$version") {
            workingDir = project.file("../projects/extract_computed_css")
            System.getenv("JCEF_11_JDK")?.let {
                environment["JAVA_HOME"] = it
            }
            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                commandLine(
                    "cmd",
                    "/c",
                    "gradlew.bat",
                    "-PinputDir=$projectDir/src/test/resources/generated/$version",
                    "extractComputedCSSForPlugin"
                )
            } else {
                commandLine(
                    "./gradlew",
                    "-PinputDir=$projectDir/src/test/resources/generated/$version",
                    "extractComputedCSSForPlugin"
                )
            }
            shouldRunAfter(exportTestData)
        }

        val addFilesToGit by register<Exec>("addExportTestDataToGit_$version") {
            workingDir = project.file("src/test/resources/generated/$version")
            executable = "git"
            args("add", "--all")
            shouldRunAfter(extractComputedCSS)
        }

        register<DefaultTask>("generateTestData_$version") {
            description = "Generates test-data from pipenv environment: $version."
            group = "generate"
            dependsOn(
                deleteTestData,
                exportTestData,
                extractComputedCSS,
                addFilesToGit,
            )
            if (generateTestDataTasks.isNotEmpty()) {
                shouldRunAfter(generateTestDataTasks.last())
            }
            generateTestDataTasks.add(this)
        }
    }

    register<DefaultTask>("generateTestData_all") {
        description = "Runs all generate-test-data tasks."
        group = "generate"
        dependsOn(generateTestDataTasks)
    }

    test {
        systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
        systemProperty("cms.rendner.dataframe.renderer.export.test.error.image.dir", exportTestErrorImagesPath)
        useJUnitPlatform()
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
        doLast {
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
        outputs.upToDateWhen { false }
    }

    runPluginVerifier {
        // See https://github.com/JetBrains/gradle-intellij-plugin#plugin-verifier-dsl
        // See https://data.services.jetbrains.com/products?fields=code,name,releases.version,releases.build,releases.type&code=PCC
        //ideVersions.addAll(listOf("PCC-2020.3.3", "PCC-2021.3"))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}