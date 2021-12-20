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
package cms.rendner.integration.python.debugger

class LocalPythonEvalDebugger(
    private val pythonPath: String,
) : PythonEvalDebugger() {

    /**
     * Starts the Python interpreter.
     * The file referred by [sourceFilePath] has to contain a line with "breakpoint()", usually the last line,
     * to switch the interpreter into debug mode. The interpreter will stop at this line and process all submitted
     * evaluation requests.
     */
    fun startWithSourceFile(sourceFilePath: String) {
        start(listOf(pythonPath, sourceFilePath))
    }

    /**
     * Starts the Python interpreter.
     * The [codeSnippet] has to contain a line with "breakpoint()", usually the last line, to switch the interpreter
     * into debug mode. The interpreter will stop at this line and process all submitted evaluation requests.
     */
    fun startWithCodeSnippet(codeSnippet: String) {
        start(listOf(pythonPath, "-c", codeSnippet))
    }

    private fun start(processArgs: List<String>) {
        val process = PythonProcess(System.lineSeparator(), printOutput = false, printInput = false)
        process.start(processArgs)

        try {
            start(process)
        } finally {
            process.cleanup()
        }
    }
}