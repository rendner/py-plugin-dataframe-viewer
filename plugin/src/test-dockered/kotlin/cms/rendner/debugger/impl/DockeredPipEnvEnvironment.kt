/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
    private var containerId: String? = null
    private val containerIdRegex = "\\p{XDigit}+".toRegex()

    /**
     * Creates a docker container of the specified [dockerImage].
     */
    fun runContainer() {
        // The docker image is built with the host's UID/GID passed as build arguments.
        // This ensures that the appuser in the container has the same UID/GID as the host user,
        // avoiding permission issues with mounted volumes.
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
                        println("container from image '$dockerImage' started with id: $containerId")

                        if (lines.size > 1) {
                            println("container '$containerId' will be terminated because it is in an unexpected state: $lines.last()")
                            destroyContainer()
                            throw ProcessRunException("Container creation for image '$dockerImage' failed with: ${lines.last()}")
                        }
                    } else {
                        throw ProcessRunException("Container creation for image '$dockerImage' failed with: $firstLine")
                    }
                }
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    it.destroy()
                }
                ProcessRunException.throwIfExitedUnexpected(it, "docker run failed")
            }
    }

    /**
     * Creates a Python debugger which is started in the pipenv environment ([pipEnvDir]) when its start method is called.
     * The [codeSnippet] has to contain a line with "breakpoint()", usually the last line, to switch
     * the Python interpreter into debug mode (Pdb).
     * The interpreter will stop at this line and process all submitted evaluation requests.
     */
    fun createPythonDebuggerWithCodeSnippet(codeSnippet: String): PythonDebugger {
        if (containerId == null) {
            throw IllegalStateException("No container available.")
        }

        val processArgs = mutableListOf<String>().apply {
            add("docker")
            add("exec")
            add("-i")
            add(containerId!!)
            addAll("pipenv run python -c".split(" "))
            add(codeSnippet)
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
                val id = containerId
                containerId = null
                ProcessRunException.throwIfExitedUnexpected(it, "docker rm failed for container $id")
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
