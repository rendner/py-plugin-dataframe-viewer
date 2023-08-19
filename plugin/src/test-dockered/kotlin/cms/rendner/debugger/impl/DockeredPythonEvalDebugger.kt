/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cms.rendner.debugger.impl

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Allows to use a dockered Python interpreter during tests.
 *
 * The [dockerImage] has to exist and contain a pre-configured pip-env
 * environment.
 *
 * @param dockerImage the docker image to start. The docker image has to exist.
 * @param workdir the pipenv environment workdir to use.
 * The workdir has to be an already existing pipenv environment.
 * Otherwise, the command to start the Python interpreter will
 * create a new pipenv environment instead of starting the Python interpreter.
 * Therefore, a debugger can't be started.
 * @param volumes volumes to map.
 */
class DockeredPythonEvalDebugger(
    private val dockerImage: String,
    private val workdir: String,
    private val volumes: List<String>?,
) : PythonEvalDebugger() {

    companion object {
        private val logger = Logger.getInstance(DockeredPythonEvalDebugger::class.java)
    }

    private var containerId: String? = null
    private val containerIdRegex = "\\p{XDigit}+".toRegex()


    /**
     * Creates a docker container of the specified [dockerImage].
     */
    fun startContainer() {

        val processArgs = mutableListOf<String>().apply{
            add("docker")
            add("run")
            volumes?.forEach { add("-v"); add(it) }
            add("-d")
            add(dockerImage)
            // command to keep the container running
            addAll("tail -f /dev/null".split(" "))
        }

        ProcessBuilder(processArgs)
            .redirectErrorStream(true)
            .start().also {
                val reader = BufferedReader(InputStreamReader(it.inputStream, StandardCharsets.UTF_8))
                reader.use { r ->
                    val lines = r.readLines()
                    val firstLine = lines.first()

                    if (containerIdRegex.matches(firstLine)) {
                        containerId = firstLine
                        logger.info("container from image '$dockerImage' started with id: $containerId")

                        if (lines.size > 1) {
                            logger.error("container '$containerId' will be terminated because it is in an unexpected state: $lines.last()")
                            destroyContainer()
                            throw IllegalStateException("Container creation for image '$dockerImage' failed with: ${lines.last()}")
                        }
                    } else {
                        throw IllegalStateException("Container creation for image '$dockerImage' failed with: $firstLine")
                    }
                }
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    it.destroy()
                }
                if (it.exitValue() != 0) {
                    throw IllegalStateException("docker run failed, exitValue: ${it.exitValue()}")
                }
            }
    }

    /**
     * Starts a Python interpreter by using the specified [workdir].
     * The file referred by [sourceFilePath] has to contain a line with "breakpoint()", usually the last line,
     * to switch the interpreter into debug mode. The interpreter will stop at this line and process all submitted
     * evaluation requests.
     */
    fun startWithSourceFile(sourceFilePath: String) {
        start(listOf(sourceFilePath))
    }

    /**
     * Starts a Python interpreter by using the specified [workdir].
     * The [codeSnippet] has to contain a line with "breakpoint()", usually the last line, to switch the interpreter
     * into debug mode. The interpreter will stop at this line and process all submitted evaluation requests.
     */
    fun startWithCodeSnippet(codeSnippet: String) {
        start(listOf("-c", codeSnippet))
    }

    /**
     * Uninstalls Python modules.
     * This action may take some time, as pipenv recreates the lock-file.
     *
     * @param modules modules to remove from a running container.
     */
    fun uninstallPythonModules(vararg modules: String) {
        if (modules.isEmpty()) return

        if (containerId == null) {
            throw IllegalStateException("No container available.")
        }

        val processArgs = mutableListOf<String>().apply {
            add("docker")
            add("exec")
            // "workdir" has to be one of the already existing pipenv environments
            // otherwise "pipenv run" creates a new pipenv environment in the specified "workdir"
            add("--workdir=$workdir")
            add(containerId!!)
            addAll("pipenv uninstall".split(" "))
            addAll(modules)
        }

        ProcessBuilder(processArgs)
        // uncomment line below to see uninstall info in console output
        //.inheritIO()
        .start().let {
            if (!it.waitFor(5, TimeUnit.MINUTES)) {
                it.destroy()
            }
            if (it.exitValue() != 0) {
                throw IllegalStateException("pipenv uninstall failed, exitValue: ${it.exitValue()}")
            }
        }
    }

    private fun start(additionalPythonArgs: List<String>) {
        if (containerId == null) {
            throw IllegalStateException("No container available.")
        }

        val process = PythonProcess("\n", printOutput = false, printInput = false)

        val processArgs = mutableListOf<String>().apply {
            add("docker")
            add("exec")
            // "workdir" has to be one of the already existing pipenv environments
            // otherwise "pipenv run" creates a new pipenv environment in the specified "workdir"
            add("--workdir=$workdir")
            add("-i")
            add(containerId!!)
            addAll("pipenv run python".split(" "))
            addAll(additionalPythonArgs)
        }

        process.start(processArgs)

        try {
            start(process)
        } finally {
            process.cleanup()
        }
    }

    fun destroyContainer() {
        if (containerId == null) return
        ProcessBuilder(
            "docker rm -f $containerId".split(" ")
        )
            .start().also {
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    it.destroy()
                }
                containerId = null
                if (it.exitValue() != 0) {
                    throw IllegalStateException("docker rm failed for container $containerId, exitValue: ${it.exitValue()}")
                }
            }
    }
}