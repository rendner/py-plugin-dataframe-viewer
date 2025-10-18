import images.plugin_code_projects.DockerfileGenerator
import images.plugin_code_projects.PluginCodeProjectsRegistry
import images.python_versions.PythonImageRegistry
import java.io.ByteArrayOutputStream

/**
 * Pre-compiled plugin for managing Docker images.
 */

val buildSrcDir = project.rootProject.projectDir.resolve("buildSrc")

tasks {
    register<DefaultTask>("generateDockerfile_pluginCode") {
        description = "Generates the Dockerfile for all plugin code projects."
        group = "docker"

        doLast {
            val repoRoot = projectDir.parentFile
            val outputFile = repoRoot.resolve("docker/plugin_code_projects/Dockerfile")
            DockerfileGenerator.writeDockerfile(PluginCodeProjectsRegistry.image, outputFile)
        }
    }

    register<DefaultTask>("buildImage_pluginCode") {
        description = "Builds the Docker image for all plugin code projects."
        group = "docker"

        doLast {
            val repoRoot = projectDir.parentFile
            val dockerfilePath = repoRoot.resolve("docker/plugin_code_projects/Dockerfile")

            if (!dockerfilePath.exists()) {
                throw GradleException("Dockerfile not found at: ${dockerfilePath.absolutePath}. Run 'generateDockerfile_pluginCode' first.")
            }

            val hostUid: String
            val hostGid: String
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                hostUid = "1000"  // Use Docker defaults
                hostGid = "1000"
            } else {
                hostUid = ByteArrayOutputStream().use { output ->
                    exec {
                        commandLine("id", "-u")
                        standardOutput = output
                    }
                    output.toString().trim()
                }
                hostGid = ByteArrayOutputStream().use { output ->
                    exec {
                        commandLine("id", "-g")
                        standardOutput = output
                    }
                    output.toString().trim()
                }
            }
            logger.lifecycle("Building Docker image with USER_UID=$hostUid and USER_GID=$hostGid")

            exec {
                workingDir = repoRoot
                executable = "docker"
                args(
                    "build",
                    ".",
                    "-f", dockerfilePath,
                    "--build-arg", "USER_UID=$hostUid",
                    "--build-arg", "USER_GID=$hostGid",
                    "-t", PluginCodeProjectsRegistry.image.dockerImageName
                )
                environment("DOCKER_BUILDKIT", "1")
            }
        }
    }

    val buildPythonDockerImagesTasks = mutableListOf<Task>()
    PythonImageRegistry.images.forEach { entry ->
        val task = register<DefaultTask>("buildImage_pythonVersion_${entry.project.pythonVersion}") {
            description = "Builds the python ${entry.project.pythonVersion} docker image."
            group = "docker"

            if (buildPythonDockerImagesTasks.isNotEmpty()) {
                shouldRunAfter(buildPythonDockerImagesTasks.last())
            }

            doLast {
                val repoRoot = projectDir.parentFile
                exec {
                    workingDir = repoRoot.resolve("docker/python_versions/${entry.project.pythonVersion}")
                    executable = "docker"
                    args(
                        "build",
                        ".",
                        "--build-arg", "PROJECT=${entry.project.name}",
                        "-t", entry.dockerImageName
                    )
                }
            }
        }
        buildPythonDockerImagesTasks.add(task.get())
    }
    register<DefaultTask>("buildImages_allPythonVersions") {
        description = "Builds all Python version Docker images."
        group = "docker"

        dependsOn(buildPythonDockerImagesTasks)
    }

    register<DefaultTask>("killAllContainers") {
        description = "Kills all docker containers for this project (cleanup task)."
        group = "docker"

        doLast {
            val dockerImageNames = mutableListOf<String>().apply {
                add(PluginCodeProjectsRegistry.image.dockerImageName)
                PythonImageRegistry.images.forEach { entry -> add(entry.dockerImageName) }
            }
            dockerImageNames.forEach { dockerImageName ->
                val containerIdsResult = ByteArrayOutputStream().use { outputStream ->
                    exec {
                        executable = "docker"
                        args("ps", "-aq", "--filter", "ancestor=${dockerImageName}")
                        standardOutput = outputStream
                        isIgnoreExitValue = true
                    }
                    outputStream.toString().trim()
                }

                if (containerIdsResult.isNotEmpty()) {
                    containerIdsResult.split(System.lineSeparator()).let { ids ->
                        logger.lifecycle("killing containers $ids for image $dockerImageName")
                        exec {
                            executable = "docker"
                            args(mutableListOf("rm", "-f").apply { addAll(ids) })
                        }
                    }
                } else {
                    logger.lifecycle("no running containers found for image $dockerImageName")
                }
            }
        }
    }

    register<DefaultTask>("runScript_pluginCode_runAllTests") {
        description = "Runs tests for all Python plugin code projects."
        group = "docker"

        doLast {
            logger.lifecycle("Running tests for all Python plugin code projects...")

            val volumeMappingsBuilder = PluginCodeProjectsRegistry.image
                .createConfiguredVolumeMappingBuilder(project.projectDir)

            exec {
                executable = "docker"
                args(
                    "run",
                    "--rm",
                    *volumeMappingsBuilder.buildAsCliArguments(),
                    PluginCodeProjectsRegistry.image.dockerImageName,
                    "python",
                    volumeMappingsBuilder.paths.getContainerScriptPath("run_all_tests.py")
                )
            }
        }
    }

    register<DefaultTask>("runScript_pluginCode_generateAllDumps") {
        description = "Generates dump files for all Python plugin code projects."
        group = "docker"

        finalizedBy(rootProject.tasks.named("copyDumps"))

        doLast {
            logger.lifecycle("Generating dumps for all Python plugin code projects...")

            val volumeMappingsBuilder = PluginCodeProjectsRegistry.image
                .createConfiguredVolumeMappingBuilder(project.projectDir)
                .apply {
                    addMapping(paths.pluginCodeProjectsGeneratedDumps)
                }

            exec {
                executable = "docker"
                args(
                    "run",
                    "--rm",
                    *volumeMappingsBuilder.buildAsCliArguments(),
                    PluginCodeProjectsRegistry.image.dockerImageName,
                    "python",
                    volumeMappingsBuilder.paths.getContainerScriptPath("generate_all_dumps.py"),
                )
            }
        }
    }
}
