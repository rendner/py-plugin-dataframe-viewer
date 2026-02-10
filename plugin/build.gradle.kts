import images.plugin_code_projects.PluginCodeProjectsRegistry
import images.python_versions.PythonImageRegistry
import utils.ExecHelper
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("idea")
    // Gradle IntelliJ Plugin
    // https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij.platform")
    // Kotlin JVM plugin to add support for Kotlin
    // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
    kotlin("jvm")
    // https://kotlinlang.org/docs/serialization.html#example-json-serialization
    kotlin("plugin.serialization")
    id("plugin-docker-images")
}

group = "cms.rendner.intellij"
version = "0.20.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // https://github.com/beryx/awt-color-factory
    implementation("org.beryx:awt-color-factory:1.0.2")
    // https://github.com/Kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // https://github.com/junit-team/junit5-samples/blob/r5.11.4/junit5-jupiter-starter-gradle-kotlin/build.gradle.kts
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // https://github.com/assertj/assertj
    testImplementation("org.assertj:assertj-core:3.27.3")
    // https://github.com/mockk/mockk
    testImplementation("io.mockk:mockk:1.14.2")

    // https://plugins.jetbrains.com/docs/intellij/pycharm.html#pycharm-plugin-setup
    intellijPlatform {
        pycharmCommunity("2025.1")
        bundledPlugin("PythonCore")
    }
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
    }
    buildSearchableOptions = false
    pluginVerification {
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId"
        )
        ides {
            recommended()
        }
    }
}

// Configure the test-dockered source set and IDEA integration
val mainSourceSet = sourceSets.main.get()
val testDockeredSourceSet = sourceSets.create("test-dockered") {
    // Integration tests need access to main source output for testing
    compileClasspath += mainSourceSet.output
    runtimeClasspath += mainSourceSet.output + output + configurations["intellijPlatformTestClasspath"]
}

// Configure test-dockered configurations to extend from test configurations
configurations {
    named(testDockeredSourceSet.implementationConfigurationName) {
        extendsFrom(configurations.testImplementation.get())
    }
    named(testDockeredSourceSet.runtimeOnlyConfigurationName) {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
    named(testDockeredSourceSet.compileOnlyConfigurationName) {
        extendsFrom(configurations.testCompileOnly.get())
    }
}

// Configure IDEA to recognize test-dockered as test sources
idea {
    module {
        testSources.from(testDockeredSourceSet.allJava.srcDirs)
        testResources.from(testDockeredSourceSet.resources.srcDirs)
    }
}

// https://github.com/nazmulidris/idea-plugin-example/blob/main/build.gradle.kts
tasks {

    test {
        useJUnitPlatform()
        jvmArgs("-Djava.awt.headless=true")
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
                if (!project.objects.newInstance<ExecHelper>().dockerImageExists(dockerImage.dockerImageName)) {
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
        }
    }

    register<DefaultTask>("copyDumps") {
        description = "Copies generated dump files to resources directory."
        group = "build"
        outputs.upToDateWhen { false }

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
    }

    prepareSandbox {
        dependsOn("copyDumps")
    }

    printProductsReleases {
        channels.set(listOf(ProductRelease.Channel.RELEASE))
        sinceBuild.set("251")
    }
}

kotlin {
    jvmToolchain(21)
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
