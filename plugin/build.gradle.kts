import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("idea")
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.4.0"
    // Kotlin JVM plugin to add support for Kotlin
    // https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
    kotlin("jvm") version "1.4.0" // provided by 2020.3
}

group = "cms.rendner.intellij"
version = "0.6.0"

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

    testImplementation("org.assertj:assertj-core:3.22.0")
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

    data class PythonDockerImage(val path: String, val pythonVersion: String, val pipenvEnvironments: List<String>) {
        val dockerImageName = "sdfv-plugin-dockered-python_$pythonVersion"
        val contentPath = "$path/content/"
        fun getWorkdir(pipenvEnvironment: String) = "/usr/src/app/pipenv_environments/$pipenvEnvironment"
    }

    val pythonDockerBaseDir = "$projectDir/dockered-python"
    val pythonDockerImages = listOf(
        // order of python versions: latest to oldest
        // order of pipenv environments: latest to oldest
        PythonDockerImage(
            "$pythonDockerBaseDir/python_3.8",
            "3.8",
            listOf("pandas_1.4"),
        ),
        PythonDockerImage(
            "$pythonDockerBaseDir/python_3.7",
            "3.7",
            listOf("pandas_1.3", "pandas_1.2", "pandas_1.1"),
        ),
    )
    val exportTestDataPath = "$projectDir/src/test/resources/generated/"
    val exportTestErrorImagesPath = "$projectDir/src/test/resources/generated-error-images/"

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
                delete(entry.contentPath)

                entry.pipenvEnvironments.forEach { pipEnvEnvironment ->
                    val pythonSourceProjectPath = "../projects/html_from_styler/${pipEnvEnvironment}_styler"

                    val pipFile = project.file("$pythonSourceProjectPath/Pipfile")
                    val pipFileLock = project.file("$pythonSourceProjectPath/Pipfile.lock")
                    if (pipFile.exists() && pipFileLock.exists()) {
                        copy {
                            from(pipFile, pipFileLock)
                            into("${entry.contentPath}/pipenv_environments/$pipEnvEnvironment/")
                        }
                    } else {
                        throw GradleException("Incomplete Pipfiles for environment: $pipEnvEnvironment")
                    }

                    val exportData = project.file("$pythonSourceProjectPath/export_data")
                    if (exportData.exists() && exportData.isDirectory) {
                        // The additional content of the pipenv environments (like Python source code, etc.)
                        // The content is separated on purpose to profit from the Docker's layer caching. Without the separation
                        // the pipenv environments are re-build whenever a source file has changed.
                        copy {
                            from(exportData)
                            into("${entry.contentPath}/merge_into_pipenv_environments/$pipEnvEnvironment/export_data")
                        }
                    } else {
                        throw GradleException("No export-data found for environment: $pipEnvEnvironment")
                    }
                }

                exec {
                    workingDir = file(entry.path)
                    executable = "docker"
                    args("build", ".", "-t", entry.dockerImageName)
                }
            }
        }
    }

    register<DefaultTask>("buildPythonDockerImages") {
        description = "Builds all python docker images."
        group = "docker"
        dependsOn(buildPythonDockerImagesTasks)
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
        entry.pipenvEnvironments.forEach { pipEnvEnvironment ->
            register<Test>("integrationTest_$pipEnvEnvironment") {
                description = "Runs integration test on pipenv environment: $pipEnvEnvironment."
                group = "verification"

                testClassesDirs = testDockeredSourceSet.output.classesDirs
                classpath = testDockeredSourceSet.runtimeClasspath

                systemProperty(
                    "cms.rendner.dataframe.renderer.dockered.test.image",
                    entry.dockerImageName,
                )
                systemProperty(
                    "cms.rendner.dataframe.renderer.dockered.test.workdir",
                    entry.getWorkdir(pipEnvEnvironment),
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
        dependsOn(integrationTestTasks)
    }

    val generateTestDataTasks = mutableListOf<Task>()
    pythonDockerImages.forEach { entry ->
        entry.pipenvEnvironments.forEach { pipEnvEnvironment ->

            val deleteTestData by register<Delete>("deleteTestData_$pipEnvEnvironment") {
                delete("$exportTestDataPath$pipEnvEnvironment")
            }

            val exportTestData by register<Test>("exportTestData_$pipEnvEnvironment") {
                testClassesDirs = testDockeredSourceSet.output.classesDirs
                classpath = testDockeredSourceSet.runtimeClasspath

                systemProperty(
                    "cms.rendner.dataframe.renderer.export.test.data.dir",
                    exportTestDataPath,
                )
                systemProperty(
                    "cms.rendner.dataframe.renderer.dockered.test.image",
                    entry.dockerImageName,
                )
                systemProperty(
                    "cms.rendner.dataframe.renderer.dockered.test.workdir",
                    entry.getWorkdir(pipEnvEnvironment),
                )
                useJUnitPlatform {
                    include("**/export/**")
                }
                shouldRunAfter(deleteTestData)
            }

            val extractComputedCSS by register<Exec>("extractComputedCSS_$pipEnvEnvironment") {
                workingDir = project.file("../projects/extract_computed_css")
                System.getenv("JCEF_11_JDK")?.let {
                    environment["JAVA_HOME"] = it
                }
                if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                    commandLine(
                        "cmd",
                        "/c",
                        "gradlew.bat",
                        "-PinputDir=$projectDir/src/test/resources/generated/$pipEnvEnvironment",
                        "extractComputedCSSForPlugin"
                    )
                } else {
                    commandLine(
                        "./gradlew",
                        "-PinputDir=$projectDir/src/test/resources/generated/$pipEnvEnvironment",
                        "extractComputedCSSForPlugin"
                    )
                }
                shouldRunAfter(exportTestData)
            }

            val addFilesToGit by register<Exec>("addTestDataToGit_$pipEnvEnvironment") {
                workingDir = project.file("src/test/resources/generated/$pipEnvEnvironment")
                executable = "git"
                args("add", ".")
                shouldRunAfter(extractComputedCSS)
            }

            register<DefaultTask>("generateTestData_$pipEnvEnvironment") {
                description = "Generates test-data from pipenv environment: $pipEnvEnvironment."
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
    }

    register<DefaultTask>("generateTestData_all") {
        description = "Runs all generate-test-data tasks."
        group = "generate"
        dependsOn(generateTestDataTasks)
    }

    test {
        systemProperty(
            "cms.rendner.dataframe.renderer.export.test.data.dir",
            exportTestDataPath,
        )
        systemProperty(
            "cms.rendner.dataframe.renderer.export.test.error.image.dir",
            exportTestErrorImagesPath,
        )
        useJUnitPlatform()
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set(project.version.toString())
    }

    runIde {
        systemProperty(
            "cms.rendner.dataframe.renderer.enable.test.data.export.action",
            true,
        )
        systemProperty(
            "cms.rendner.dataframe.renderer.export.test.data.dir",
            exportTestDataPath,
        )
        // enable debug log for plugin
        // -> https://github.com/JetBrains/gradle-intellij-plugin/issues/708#issuecomment-870442081
        systemProperty(
            "idea.log.debug.categories",
            "#cms.rendner",
        )
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
            logger.lifecycle("copy 'plugin_code' files")
            pythonDockerImages.forEach { entry ->
                entry.pipenvEnvironments.forEach { pipenvEnvironment ->
                    val pluginCode =
                        project.file("../projects/python_plugin_code/${pipenvEnvironment}_code/generated/plugin_code")
                    if (pluginCode.exists()) {
                        copy {
                            from(pluginCode)
                            into(project.file("src/main/resources/$pipenvEnvironment"))
                        }
                    } else {
                        throw GradleException("Missing file 'plugin_code' for version: $pipenvEnvironment")
                    }
                }
            }
        }
        outputs.upToDateWhen { false }
    }

    runPluginVerifier {
        // See https://github.com/JetBrains/gradle-intellij-plugin#plugin-verifier-dsl
        // See https://data.services.jetbrains.com/products?fields=code,name,releases.version,releases.build,releases.type&code=PC
        //ideVersions.addAll(listOf("PC-2020.3.3", "PC-2021.3.2", "PC-2022.1"))
    }

    listProductsReleases {
        sinceVersion.set("2020.3")
        untilVersion.set("221.4165.171") // 2022.1 EAP
        //untilVersion.set("2022.1")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}