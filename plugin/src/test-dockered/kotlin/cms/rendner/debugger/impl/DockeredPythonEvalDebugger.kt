/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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

class DockeredPythonEvalDebugger : PythonEvalDebugger() {

    companion object {
        private val logger = Logger.getInstance(DockeredPythonEvalDebugger::class.java)
    }

    private var containerId: String? = null
    private val containerIdRegex = "\\p{XDigit}+".toRegex()


    fun startContainer() {

        val image = "plugin-docker-python"
        val command = "tail -f /dev/null" // to keep the container running

        ProcessBuilder(
            "docker run -d $image $command".split(" ")
        )
            .redirectErrorStream(true)
            .start().also {
                val reader = BufferedReader(InputStreamReader(it.inputStream, StandardCharsets.UTF_8))
                reader.use { r ->
                    val lines = r.readLines()
                    val firstLine = lines.first()

                    if (containerIdRegex.matches(firstLine)) {
                        containerId = firstLine
                        logger.info("container from image '$image' started with id: $containerId")

                        if (lines.size > 1) {
                            logger.error("container '$containerId' will be terminated because it is in an unexpected state: $lines.last()")
                            destroyContainer()
                            throw IllegalStateException("Container creation for image '$image' failed with: ${lines.last()}")
                        }
                    } else {
                        throw IllegalStateException("Container creation for image '$image' failed with: $firstLine")
                    }
                }
                it.waitFor(2, TimeUnit.SECONDS)
                it.destroyForcibly()
            }
    }

    /**
     * Starts the Python interpreter of the specified pipenv environment.
     * The file referred by [sourceFilePath] has to contain a line with "breakpoint()", usually the last line,
     * to switch the interpreter into debug mode. The interpreter will stop at this line and process all submitted
     * evaluation requests.
     */
    fun startWithSourceFile(sourceFilePath: String, pipenvEnvironment: PipenvEnvironment) {
        start(sourceFilePath, pipenvEnvironment)
    }

    /**
     * Starts the Python interpreter of the specified pipenv environment.
     * The [codeSnippet] has to contain a line with "breakpoint()", usually the last line, to switch the interpreter
     * into debug mode. The interpreter will stop at this line and process all submitted evaluation requests.
     */
    fun startWithCodeSnippet(codeSnippet: String, pipenvEnvironment: PipenvEnvironment) {
        start("-c $codeSnippet", pipenvEnvironment)
    }

    private fun start(commandSuffix: String, pipenvEnvironment: PipenvEnvironment) {
        if (containerId == null) {
            throw IllegalStateException("No container available.")
        }

        val process = PythonProcess("\n", printOutput = false, printInput = false)

        // "workdir" has to be one of the already existing pipenv environments
        // otherwise "pipenv run" creates a new pipenv environment in the specified "workdir"
        val workdir = "/usr/src/app/pipenv_environments/${pipenvEnvironment.label}"
        val command = "pipenv run python $commandSuffix"

        process.start(
            "docker exec -i --workdir=$workdir $containerId $command".split(" ")
        )

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
                it.waitFor(2, TimeUnit.SECONDS)
                it.destroy()
                containerId = null
            }
    }
}