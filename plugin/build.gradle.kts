import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("idea")
    // Gradle IntelliJ Plugin
    // https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.13.0"
    // Kotlin JVM plugin to add support for Kotlin
    // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
    kotlin("jvm") version "1.7.0"
    // https://kotlinlang.org/docs/serialization.html#example-json-serialization
    kotlin("plugin.serialization") version "1.7.0"
}

group = "cms.rendner.intellij"
version = "0.15.1"

repositories {
    mavenCentral()
}

dependencies {
    // https://github.com/beryx/awt-color-factory
    implementation("org.beryx:awt-color-factory:1.0.2")
    // https://github.com/Kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    // https://github.com/junit-team/junit5-samples/blob/r5.8.2/junit5-jupiter-starter-gradle-kotlin/build.gradle.kts
    testImplementation(platform("org.junit:junit-bom:5.8.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("org.assertj:assertj-core:3.23.1")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
// https://intellij-support.jetbrains.com/hc/en-us/community/posts/206590865-Creating-PyCharm-plugins
intellij {
    plugins.add("python-ce") // is required even if we specify a PyCharm IDE
    version.set("2022.3")
    type.set("PC")
    downloadSources.set(true)
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
    data class PipenvEnvironment(val name: String, val dataFrameLibraries: List<String>, val pipfileRoot: File? = null)

    data class PythonDockerImage(
        val pythonVersion: String,
        val dockerFileBaseDir: String,
        val environments: List<PipenvEnvironment>,
        val buildArgs: List<String> = emptyList(),
        ) {
        private val workdir = "/usr/src/app"
        val dockerImageName = "sdfv-plugin-dockered-python_$pythonVersion"
        val additionalBuildContentDir = "$dockerFileBaseDir/content/"

        fun getTransferDirMappings(hostTransferPath: String) = arrayOf(
            "$hostTransferPath/in:${workdir}/transfer/in:ro",
            "$hostTransferPath/out:${workdir}/transfer/out",
        )

        fun getPathInContainer(pipenvEnvironment: PipenvEnvironment): String {
            return "${workdir}/pipenv_environments/${pipenvEnvironment.name}"
        }
    }

    data class MultiPipenvImageBuilder(val pythonVersion: String) {
        private val projectsToAdd = mutableMapOf<String, List<String>>()
        private val environmentsToAdd = mutableListOf<PipenvEnvironment>()

        fun addGroupedProjects(baseDir: String, projects: List<String>) = apply { projectsToAdd[baseDir] = projects }
        fun addPipenvEnvironment(environment: PipenvEnvironment) = apply { environmentsToAdd.add(environment) }

        fun build(project: Project): PythonDockerImage {
            return PythonDockerImage(
                pythonVersion,
                "${project.projectDir}/dockered-python/$pythonVersion",
                projectsToAdd.flatMap { entry ->
                    val providerDir = "../projects/python_plugin_code/${entry.key}"
                    entry.value.map { PipenvEnvironment(it, listOf(entry.key), project.file("$providerDir/$it")) }
                }.toMutableList().apply { addAll(environmentsToAdd) }
            )
        }
    }

    fun singlePipenvEnvironmentImage(
        pythonVersion: String,
        environment: PipenvEnvironment,
    ): PythonDockerImage {
        return PythonDockerImage(
            pythonVersion,
            "$projectDir/dockered-python/$pythonVersion",
            listOf(environment),
            listOf("--build-arg", "pipenv_environment=${environment.name}")
        )
    }

    val pythonDockerImages = listOf(
        // order of python versions: latest to oldest
        // order of pipenv environments: latest to oldest
        singlePipenvEnvironmentImage(
            "3.12",
            PipenvEnvironment("python_3.12", listOf("pandas", "polars")),
        ),
        singlePipenvEnvironmentImage(
            "3.11",
            PipenvEnvironment("python_3.11", listOf("pandas", "polars")),
        ),
        singlePipenvEnvironmentImage(
            "3.10",
            PipenvEnvironment("python_3.10", listOf("pandas", "polars")),
        ),
        MultiPipenvImageBuilder("3.9")
            .addGroupedProjects("pandas", listOf("pandas_2.2", "pandas_2.1"))
            .build(project),
        MultiPipenvImageBuilder("3.8")
            .addGroupedProjects("polars", listOf("polars_x.y"))
            .addGroupedProjects("pandas", listOf("pandas_2.0", "pandas_1.5", "pandas_1.4"))
            .build(project),
        MultiPipenvImageBuilder("3.7")
            .addGroupedProjects("pandas", listOf("pandas_1.3", "pandas_1.2", "pandas_1.1"))
            .build(project),
    )

    val buildPythonDockerImagesTasks = mutableListOf<Task>()
    pythonDockerImages.forEach { entry ->

        register<DefaultTask>("buildPythonDockerImage_${entry.pythonVersion}") {
            description = "Builds the python ${entry.pythonVersion} docker image."
            group = "docker"

            if (buildPythonDockerImagesTasks.isNotEmpty()) {
                shouldRunAfter(buildPythonDockerImagesTasks.last())
            }
            buildPythonDockerImagesTasks.add(this)

            doLast {
                delete(entry.additionalBuildContentDir)

                entry.environments.forEach { pipEnv ->

                    pipEnv.pipfileRoot?.let {
                        val pipFileLock = it.resolve("Pipfile.lock")
                        if (pipFileLock.exists()) {
                            copy {
                                from(pipFileLock)
                                into("${entry.additionalBuildContentDir}/pipenv_environments/${pipEnv.name}/")
                            }
                        } else {
                            throw GradleException("Missing Pipfile.lock for environment: $pipEnv")
                        }
                    }
                }

                exec {
                    workingDir = file(entry.dockerFileBaseDir)
                    executable = "docker"
                    args("build", *entry.buildArgs.toTypedArray(), ".", "-t", entry.dockerImageName)
                }
            }
        }
    }

    register<DefaultTask>("buildPythonDockerImages") {
        description = "Builds all python docker images."
        group = "docker"
        dependsOn(project.tasks.filter { it.name.startsWith("buildPythonDockerImage_") })
    }

    register<DefaultTask>("killAllPythonContainers") {
        description = "Kills all python docker containers (cleanup task)."
        group = "docker"

        doLast {
            pythonDockerImages.forEach { entry ->
                val containerIdsResult = ByteArrayOutputStream().use { outputStream ->
                    exec {
                        executable = "docker"
                        args("ps", "-aq", "--filter", "ancestor=${entry.dockerImageName}")
                        standardOutput = outputStream
                    }
                    outputStream.toString().trim()
                }

                if (containerIdsResult.isNotEmpty()) {
                    containerIdsResult.split(System.lineSeparator()).let { ids ->
                        logger.lifecycle("killing containers $ids for image ${entry.dockerImageName}")
                        exec {
                            executable = "docker"
                            args(mutableListOf("rm", "-f").apply { addAll(ids) })
                        }
                    }
                } else {
                    logger.lifecycle("no running containers found for image ${entry.dockerImageName}")
                }
            }
        }
    }

    val integrationTestTasks = mutableListOf<Task>()
    pythonDockerImages.forEach { entry ->
        entry.environments.forEach { pipenvEnvironment ->
            register<Test>("integrationTest_${pipenvEnvironment.name}") {
                description = "Runs integration test on pipenv environment: ${pipenvEnvironment.name}."
                group = "verification"
                outputs.upToDateWhen { false }

                testClassesDirs = testDockeredSourceSet.output.classesDirs
                classpath = testDockeredSourceSet.runtimeClasspath

                systemProperty(
                    "cms.rendner.dataframe.viewer.dataframe.libraries",
                    pipenvEnvironment.dataFrameLibraries,
                )
                systemProperty(
                    "cms.rendner.dataframe.viewer.docker.image",
                    entry.dockerImageName,
                )
                systemProperty(
                    "cms.rendner.dataframe.viewer.docker.workdir",
                    entry.getPathInContainer(pipenvEnvironment),
                )
                systemProperty(
                    "cms.rendner.dataframe.viewer.docker.volumes",
                    listOf(
                        *entry.getTransferDirMappings("$projectDir/src/test-dockered/transfer"),
                    ).joinToString(";"),
                )
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
    }

    register<DefaultTask>("integrationTest_all") {
        description = "Runs all integrationTest tasks."
        group = "verification"
        outputs.upToDateWhen { false }
        dependsOn(project.tasks.filter { it.name.startsWith("integrationTest_") && it.name != this.name })
    }

    test {
        useJUnitPlatform()
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set(project.version.toString())
    }

    runIde {
        // enable debug log for plugin
        // -> https://github.com/JetBrains/gradle-intellij-plugin/issues/708#issuecomment-870442081
        systemProperty(
            "idea.log.debug.categories",
            "#cms.rendner",
        )
        // environment["PYCHARM_DEBUG"] = "True"
        // environment["PYDEV_DEBUG"] = "True"
        environment["PYDEVD_USE_CYTHON"] = "NO"
        environment["idea.is.internal"] = true
        // ideDir.set(File("/snap/intellij-idea-community/current"))
    }

    processResources {
        // Note:
        // Copying the resources has to happen in "doFirst" instead of "doLast".
        //
        // Problem:
        // When using "doLast" and running the gradle-task "runIde", the ide is started with the old
        // resources. The resources are copied, but it seems the ide is started too early?
        //
        // Traceability:
        // - switch to "doLast"
        // - delete all "src/resources/pandas_x.y" folders
        // - run gradle-task "runIde"
        // - try to use the plugin code (by inspecting a DataFrame.style)
        // - plugin crashes because the resources are not available
        doFirst {
            logger.lifecycle("copy Python 'plugin_code' files")
            copy {
                from(project.file("../projects/python_plugin_code/sdfv_base/generated"))
                into(project.file("src/main/resources/sdfv_base"))
            }
            listOf(
                project.file("../projects/python_plugin_code/pandas/"),
                project.file("../projects/python_plugin_code/polars/"),
            ).forEach { dataFrameLibraryRoot ->
                dataFrameLibraryRoot.listFiles { f -> f.isDirectory }?.forEach { projectDir ->
                    val pluginCode = projectDir.resolve("generated/plugin_modules_dump.json")
                    if (pluginCode.exists()) {
                        copy {
                            from(pluginCode)
                            into(project.file("src/main/resources/${projectDir.name}"))
                        }
                    } else {
                        throw GradleException("Missing file 'plugin_modules_dump.json' for: ${projectDir.name}")
                    }
                }
            }
        }
        outputs.upToDateWhen { false }
    }

    runPluginVerifier {
        // See https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-runpluginverifier
        // See https://data.services.jetbrains.com/products?fields=code,name,releases.version,releases.build,releases.type&code=PC
        //ideVersions.addAll(listOf("PC-2022.3", "PC-2023.1"))
    }

    listProductsReleases {
        sinceVersion.set("2022.3")
        // untilVersion.set("2024.3")
        untilVersion.set("241.8102.133")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
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