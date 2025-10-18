import images.plugin_code_projects.PluginCodeProjectsRegistry
import images.python_versions.PythonImageRegistry
import org.gradle.api.tasks.testing.Test
import java.io.ByteArrayOutputStream

plugins {
    id("idea")
    // Gradle IntelliJ Plugin
    // https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.13.0"
    // Kotlin JVM plugin to add support for Kotlin
    // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
    kotlin("jvm") version "1.9.21"
    // https://kotlinlang.org/docs/serialization.html#example-json-serialization
    kotlin("plugin.serialization") version "1.9.21"
    id("plugin-docker-images")
}

group = "cms.rendner.intellij"
version = "0.19.0"

repositories {
    mavenCentral()
}

dependencies {
    // https://github.com/beryx/awt-color-factory
    implementation("org.beryx:awt-color-factory:1.0.2")
    // https://github.com/Kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // https://github.com/junit-team/junit5-samples/blob/r5.11.4/junit5-jupiter-starter-gradle-kotlin/build.gradle.kts
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // https://github.com/assertj/assertj
    testImplementation("org.assertj:assertj-core:3.27.3")
    // https://github.com/mockk/mockk
    testImplementation("io.mockk:mockk:1.14.2")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
// https://intellij-support.jetbrains.com/hc/en-us/community/posts/206590865-Creating-PyCharm-plugins
intellij {
    plugins.add("python-ce") // is required even if we specify a PyCharm IDE
    version.set("2023.3")
    type.set("PC") // PC = PyCharm Community Edition, PY = PyCharm
    downloadSources.set(true)
    updateSinceUntilBuild.set(false)
}

// Configure the test-dockered source set and IDEA integration
val mainSourceSet = sourceSets.main.get()
val testDockeredSourceSet = sourceSets.create("test-dockered") {
    // Integration tests need access to main source output for testing
    compileClasspath += mainSourceSet.output
    runtimeClasspath += mainSourceSet.output + output
}

// Configure test-dockered configurations to extend from test configurations
configurations {
    named(testDockeredSourceSet.implementationConfigurationName) {
        extendsFrom(testImplementation.get())
    }
    named(testDockeredSourceSet.runtimeOnlyConfigurationName) {
        extendsFrom(testRuntimeOnly.get())
    }
    named(testDockeredSourceSet.compileOnlyConfigurationName) {
        extendsFrom(testCompileOnly.get())
    }
}

// Configure IDEA to recognize test-dockered as test sources
idea {
    module {
        testSourceDirs = testSourceDirs.plus(testDockeredSourceSet.allJava.srcDirs)
        testResourceDirs = testResourceDirs.plus(testDockeredSourceSet.resources.srcDirs)
    }
}

// https://github.com/nazmulidris/idea-plugin-example/blob/main/build.gradle.kts
tasks {

    test {
        useJUnitPlatform()
        jvmArgs("-Djava.awt.headless=true")
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set(project.version.toString())
    }

    register<Delete>("rebuildBuildSrc") {
        description = "Cleans buildSrc (precompiled script plugins) to force recompilation on next build."
        group = "build"

        delete(file("buildSrc/build"))

        doLast {
            logger.lifecycle("buildSrc cleaned. Run any task to trigger recompilation of precompiled script plugins.")
        }
    }

    val testDockeredClassesTask = named(testDockeredSourceSet.classesTaskName)

    // Track all integration test tasks for ordering and aggregate task
    val allIntegrationTestTasks = mutableListOf<Task>()

    fun createIntegrationTestTask(
        dockerImage: DataFrameProjectsDockerImage,
        dataFrameProject: DataFrameProject,
        taskPrefix: String = ""
    ): Task {
        val taskName = if (taskPrefix.isEmpty()) {
            "integrationTest_${dataFrameProject.name}"
        } else {
            "integrationTest_${taskPrefix}_${dataFrameProject.name}"
        }

        val task = register<Test>(taskName) {
            description = "Runs integration test on pipenv environment: ${dataFrameProject.name}."
            group = "integration"
            outputs.upToDateWhen { false }

            testClassesDirs = testDockeredSourceSet.output.classesDirs
            classpath = testDockeredSourceSet.runtimeClasspath

            // Ensure test-dockered sources are compiled before running tests
            dependsOn(testDockeredClassesTask)

            doFirst {
                // Check if the required Docker image exists
                val imageCheckResult = ByteArrayOutputStream().use { outputStream ->
                    exec {
                        executable = "docker"
                        args("images", "-q", dockerImage.dockerImageName)
                        standardOutput = outputStream
                        isIgnoreExitValue = true
                    }
                    outputStream.toString().trim()
                }

                if (imageCheckResult.isEmpty()) {
                    throw GradleException(
                        "Required Docker image '${dockerImage.dockerImageName}' not found. " +
                        "Please build the image first using the appropriate build task."
                    )
                }

                val volumeMappingsBuilder = dockerImage
                    .createConfiguredVolumeMappingBuilder(project.projectDir)
                    .apply {
                        addMapping(paths.transferIn)
                        addMapping(paths.transferOut)
                    }

                systemProperty(
                    "cms.rendner.dataframe.viewer.dataframe.libraries",
                    dataFrameProject.installedDataFrameModules,
                )
                systemProperty(
                    "cms.rendner.dataframe.viewer.docker.image",
                    dockerImage.dockerImageName,
                )
                systemProperty(
                    "cms.rendner.dataframe.viewer.docker.workdir",
                    volumeMappingsBuilder.paths.getContainerWorkDir(dataFrameProject),
                )
                systemProperty(
                    "cms.rendner.dataframe.viewer.docker.volumes",
                    volumeMappingsBuilder.build().joinToString(";"),
                )
                useJUnitPlatform {
                    include("**/integration/**")
                }
            }

            if (allIntegrationTestTasks.isNotEmpty()) {
                shouldRunAfter(allIntegrationTestTasks.last())
            }

            afterTest(KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
                val clsName = descriptor.parent?.displayName ?: descriptor.className
                println("\t$clsName > ${descriptor.name}: ${result.resultType}")
            }))
        }
        return task.get()
    }

    val pluginCodeProjectsIntegrationTestTasks = mutableListOf<Task>()
    PluginCodeProjectsRegistry.getAllProjects().forEach { dataFrameProject ->
        val taskInstance = createIntegrationTestTask(PluginCodeProjectsRegistry.image, dataFrameProject, "pluginCode")
        pluginCodeProjectsIntegrationTestTasks.add(taskInstance)
        allIntegrationTestTasks.add(taskInstance)
    }

    val pythonVersionsIntegrationTestTasks = mutableListOf<Task>()
    PythonImageRegistry.images.forEach { dockerImage ->
        dockerImage.projects.forEach { dataFrameProject ->
            val taskInstance = createIntegrationTestTask(dockerImage, dataFrameProject, "pythonVersion")
            pythonVersionsIntegrationTestTasks.add(taskInstance)
            allIntegrationTestTasks.add(taskInstance)
        }
    }

    register<DefaultTask>("integrationTest_pluginCode") {
        description = "Runs all integration tests for plugin code projects."
        group = "integration"
        dependsOn(pluginCodeProjectsIntegrationTestTasks)
    }

    register<DefaultTask>("integrationTest_pythonVersion") {
        description = "Runs all integration tests for Python version environments."
        group = "integration"
        dependsOn(pythonVersionsIntegrationTestTasks)
    }

    register<DefaultTask>("integrationTest_all") {
        description = "Runs all integration tests across all Python/pandas/polars versions."
        group = "integration"
        dependsOn(allIntegrationTestTasks)
    }

    runIde {
        doFirst {
            // Enable debug log for plugin
            // -> https://github.com/JetBrains/gradle-intellij-plugin/issues/708#issuecomment-870442081
            systemProperty(
                "idea.log.debug.categories",
                "#cms.rendner",
            )

            // https://plugins.jetbrains.com/docs/intellij/disposers.html#diagnosing-disposer-leaks
            // log is located at: plugin/build/idea-sandbox/system/log/idea.log
            // search in logs for: "Memory leak detected"
            systemProperty("idea.disposer.debug", "off") // "on" | "off"

            // environment["PYCHARM_DEBUG"] = "True"
            // environment["PYDEV_DEBUG"] = "True"
            environment["PYDEVD_USE_CYTHON"] = "NO"
            environment["idea.is.internal"] = true
            // ideDir.set(File("/snap/intellij-idea-community/current"))
        }
    }

    register<DefaultTask>("copyDumps") {
        description = "Copies generated dump files to resources directory."
        group = "build"

        doLast {
            logger.lifecycle("copy generated Python 'plugin_code' files")
            val generatedDumps = project.file("../projects/python_plugin_code/generated_dumps")
            if (generatedDumps.exists() && generatedDumps.isDirectory) {
                val targetDir = project.file("src/main/resources/generated_dumps")
                targetDir.mkdirs()
                copy {
                    from(generatedDumps)
                    into(targetDir)
                }
            } else {
                throw GradleException("Missing file 'plugin_modules_dump.json' for: ${projectDir.name}")
            }
        }

        outputs.upToDateWhen { false }
    }

    prepareSandbox {
        dependsOn("copyDumps")
    }

    runPluginVerifier {
        // See https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-runpluginverifier
        // See https://data.services.jetbrains.com/products?fields=code,name,releases.version,releases.build,releases.type&code=PC
        //ideVersions.addAll(listOf("PC-2023.3", "PC-2025.1"))
    }

    listProductsReleases {
        sinceVersion.set("2023.3")
        // untilVersion.set("2025.1")
        untilVersion.set("251.17181.23")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    exclude {
        // Exclude unwanted files.
        // These files are created sometimes when using the python files from the plugin resource folder directly
        // in a PyCharm project.
        // info: https://www.jetbrains.com/help/pycharm/cleaning-pyc-files.html
        it.name == "__pycache__" || it.file.extension == ".pyc"
    }
}