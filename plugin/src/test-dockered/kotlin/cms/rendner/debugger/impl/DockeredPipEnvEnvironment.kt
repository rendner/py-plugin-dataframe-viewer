/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allows to use a dockered Python interpreter during tests.
 *
 * The [dockerImage] has to exist and contain a pre-configured pipenv environment.
 *
 * @param dockerImage the docker image to start. The docker image has to exist.
 * @param pipEnvDir the pipenv environment workdir to use.
 * The directory has to contain an existing pipenv environment.
 * Otherwise, the command to start the Python interpreter will
 * create a new pipenv environment instead of starting the Python interpreter.
 * @param volumes volumes to map.
 */
class DockeredPipEnvEnvironment(
    private val dockerImage: String,
    private val pipEnvDir: String,
    private val volumes: List<String>?,
) {

    companion object {
        private val logger = Logger.getInstance(DockeredPipEnvEnvironment::class.java)
    }

    private var containerId: String? = null
    private val containerIdRegex = "\\p{XDigit}+".toRegex()

    /**
     * Creates a docker container of the specified [dockerImage].
     */
    fun runContainer() {

        val processArgs = mutableListOf<String>().apply{
            add("docker")
            add("run")
            add("--workdir=$pipEnvDir")
            volumes?.forEach { add("-v"); add(it) }
            add("-d")
            add(dockerImage)
            // command to keep the container running
            addAll("tail -f /dev/null".split(" "))
        }

        ProcessBuilder(processArgs)
            .redirectErrorStream(true)
            .start().also {
                it.inputStream.bufferedReader().use { r ->
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
     * Creates a Python debugger which is started in the pipenv environment ([pipEnvDir]) when its start method is called.
     * The file referred by [sourceFilePath] has to contain a line with "breakpoint()", usually the last line,
     * to switch the Python interpreter into debug mode (Pdb).
     * The interpreter will stop at this line and process all submitted evaluation requests.
     */
    fun createPythonDebuggerWithSourceFile(sourceFilePath: String): PythonDebugger {
        return createPythonDebugger(listOf(sourceFilePath))
    }

    /**
     * Creates a Python debugger which is started in the pipenv environment ([pipEnvDir]) when its start method is called.
     * The [codeSnippet] has to contain a line with "breakpoint()", usually the last line, to switch
     * the Python interpreter into debug mode (Pdb).
     * The interpreter will stop at this line and process all submitted evaluation requests.
     */
    fun createPythonDebuggerWithCodeSnippet(codeSnippet: String): PythonDebugger {
        return createPythonDebugger(listOf("-c", codeSnippet))
    }

    /**
     * Uninstalls Python modules.
     * The modules are removed from the pipenv environment ([pipEnvDir]).
     *
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
            add(containerId!!)
            addAll("pipenv uninstall".split(" "))
            addAll(modules)
        }

        ProcessBuilder(processArgs)
        // uncomment line below to see uninstall info in console output
        //.inheritIO()
        .start().let {
            if (!it.waitFor(2, TimeUnit.MINUTES)) {
                it.destroy()
            }
            if (it.exitValue() != 0) {
                throw IllegalStateException("pipenv uninstall failed, exitValue: ${it.exitValue()}")
            }
        }
    }

    private fun createPythonDebugger(additionalPythonArgs: List<String>): PythonDebugger {
        if (containerId == null) {
            throw IllegalStateException("No container available.")
        }

        val processArgs = mutableListOf<String>().apply {
            add("docker")
            add("exec")
            add("-i")
            add(containerId!!)
            addAll("pipenv run python".split(" "))
            addAll(additionalPythonArgs)
        }

        return MyPythonDebugger(processArgs)
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

    private class MyPythonDebugger(private val processArgs: List<String>): PythonDebugger() {
        private var started = AtomicBoolean()

        override fun start() {
            if (!started.compareAndSet(false, true)) {
                throw IllegalStateException("Debugger was already started.")
            }

            val process = PythonProcess("\n", printOutput = false, printInput = false)
            process.start(processArgs)

            try {
                start(process)
            } finally {
                process.cleanup()
            }
        }
    }
}