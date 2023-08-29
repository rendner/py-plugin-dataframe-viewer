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

import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture

private const val NEW_LINE_MARKER = "@_@NL@_@"
private const val RESULT_MARKER = "@_@RESULT@_@"
private const val EXC_MARKER = "@_@EXC@_@"

/**
 * Allows to evaluate and execute code inside a Python interpreter.
 * Only the functionalities required by the plugin are implemented.
 *
 * Requirement:
 * The "python_helpers" have to be linked via "PYTHONPATH" for the running python interpreter
 * to allow import of the "debugger_helpers". For the dockered Python interpreters the file
 * is mapped into the container via a volume. Therefore, the Python interpreter will always
 * use the latest version (no need to rebuild the docker images after change).
 */
abstract class PythonDebugger {

    private abstract class Task<R> {
        val result = CompletableFuture<R>()
        open fun completeWithResult(result: MarkedResult?) {
            this.result.complete(null)
        }

        fun completeWithError(throwable: Throwable) {
            if (throwable is PluginPyDebuggerException) {
                result.completeExceptionally(throwable)
            } else {
                result.completeExceptionally(PluginPyDebuggerException("Unknown error occurred.", throwable))
            }
        }
    }

    private data class MarkedResult(val value: String, val marker: String) {
        companion object {
            fun parseToValue(result: MarkedResult, trimResult: Boolean): ResultValue {
                val separatorAfterTypeInfo = result.value.indexOf(" ")
                val separatorAfterRefId = result.value.indexOf(" ", separatorAfterTypeInfo + 1)

                val qualifiedType = result.value.substring(0, separatorAfterTypeInfo).let {
                    it.splitAtIndex(it.indexOf(":"))
                }
                val refId = result.value.substring(separatorAfterTypeInfo + 1, separatorAfterRefId)
                var value = result.value.substring(separatorAfterRefId + 1)

                if (trimResult && value.length > 100) {
                    value = "${value.substring(0, 100)}..."
                }

                return ResultValue(
                    value,
                    qualifiedType.second,
                    qualifiedType.first,
                    refId,
                )
            }

            fun parseToError(result: MarkedResult): ResultError {
                val separatorAfterTypeInfo = result.value.indexOf(" ")
                val qualifiedType = result.value.substring(0, separatorAfterTypeInfo).let {
                    it.splitAtIndex(it.indexOf(":"))
                }
                val reason = result.value.substring(separatorAfterTypeInfo + 1)
                return ResultError(
                    reason,
                    qualifiedType.second,
                    qualifiedType.first,
                )
            }

            private fun String.splitAtIndex(index: Int): Pair<String, String> {
                return Pair(this.substring(0, index), this.substring(index + 1))
            }
        }
    }

    private data class ResultError(
        val cause: String?,
        val type: String?,
        val typeQualifier: String?,
    )

    private data class ResultValue(
        val value: String?,
        val type: String?,
        val typeQualifier: String?,
        val refId: String?,
    )

    private val openTasks = ArrayBlockingQueue<Task<*>>(1)

    private object LinePrefixes {
        const val DEBUGGER_PROMPT = "(Pdb) "
    }

    /**
     * §valuates an expression or executes statements.
     * Blocks the caller thread until the operation is complete.
     */
    fun evalOrExec(request: EvalOrExecRequest): EvalOrExecResponse {
        return EvaluateTask(request).also { openTasks.put(it) }.result.get()
    }

    /**
     * Continues from the current breakpoint.
     * Blocks the caller thread until the operation is complete.
     */
    fun continueFromBreakpoint() {
        ContinueTask().also { openTasks.put(it) }.result.get()
    }

    /**
     * Quits the debugger.
     * Blocks the caller thread until the operation is complete.
     */
    fun quit() {
        QuitTask().also { openTasks.put(it) }.result.get()
    }

    /**
     * Installs a dump-on-exit handler.
     * Blocks the caller thread until the operation is complete.
     *
     * @param filePath relative file path inside the "transfer/out" folder.
     */
    fun dumpTracesOnExit(filePath: String) {
        DumpTracesOnExitTask("'$filePath'").also { openTasks.put(it) }.result.get()
    }

    /**
     * Starts the debugger.
     * Blocks the caller thread until the debugger is terminated.
     *
     * A started debugger can be terminated by sending a quit-command [quit]
     * or by interrupting the thread which started the debugger.
     */
    abstract fun start()

    /**
     * The [pythonProcess] must have been started with a source file or code that contain a line with "breakpoint()"
     * to be able to switch into the debug mode. The line should be placed at the point where the program should
     * be interrupted. All interactions are done at the location of the mentioned line.
     *
     * [pythonProcess] must be a started python interpreter and not the Python Debugger (Pdb).
     * Starting directly in debug mode isn't supported.
     */
    protected fun start(pythonProcess: PythonProcess) {
        try {
            processOpenTasks(pythonProcess)
            openTasks.forEach { it.result.cancel(false) }
        } catch (ex: Throwable) {
            openTasks.forEach { it.result.completeExceptionally(ex) }
            if (ex is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (ex is PluginPyDebuggerException) {
                throw ex
            } else {
                throw PluginPyDebuggerException("Unknown error occurred.", ex)
            }
        }
    }

    private fun processOpenTasks(pythonProcess: PythonProcess) {

        var activeTask: Task<*>? = null
        var lines: List<String> = emptyList()
        var stoppedAtInputPrompt = false
        var checkForInputPrompt = true

        try {
            while (!Thread.currentThread().isInterrupted) {

                checkProcessIsAlive(pythonProcess)

                if (checkForInputPrompt && pythonProcess.canRead()) {
                    lines = unescapeDebuggerOutput(pythonProcess.readLinesNonBlocking())
                    stoppedAtInputPrompt = lastLineIsPrompt(lines)
                }

                if (stoppedAtInputPrompt) {
                    checkForInputPrompt = false

                    var nextDebugCommand: String? = null

                    if (activeTask != null) {
                        activeTask.completeWithResult(getMarkedResult(lines))
                        activeTask = null
                    }

                    if (openTasks.isNotEmpty()) {
                        activeTask = openTasks.take().also { task ->
                            nextDebugCommand = when (task) {
                                is EvaluateTask -> {
                                    escapeDebuggerInput(task.request.expression).let {
                                        if (task.request.execute) {
                                            "!__import__('debugger_helpers').DebuggerInternals.exec($it)"
                                        } else {
                                            "!__import__('debugger_helpers').DebuggerInternals.eval($it)"
                                        }
                                    }

                                }
                                is DumpTracesOnExitTask -> {
                                    "!__import__('debugger_helpers').DebuggerInternals.dump_traces_on_exit(${task.filePath})"
                                }
                                is QuitTask -> "quit"
                                is ContinueTask -> "continue"
                                else -> null
                            }
                        }
                    }

                    if (nextDebugCommand != null) {
                        pythonProcess.writeLine("$nextDebugCommand")
                        stoppedAtInputPrompt = false
                        checkForInputPrompt = true
                    }
                }
            }

            activeTask?.completeWithError(PluginPyDebuggerException("Task was canceled."))
        } catch (e: Throwable) {
            if (e is PluginPyDebuggerException && e.isDisconnectException() && activeTask is QuitTask) {
                activeTask.completeWithResult(null)
                return
            }
            activeTask?.completeWithError(e)
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            throw e
        }
    }

    private fun checkProcessIsAlive(pythonProcess: PythonProcess) {
        if (!pythonProcess.isAlive()) {
            // In PyCharm a "PyDebuggerException" with the message "Disconnected"
            // is thrown in case the debugger is disconnected.
            throw PluginPyDebuggerException("Disconnected")
        }
    }

    private fun lastLineIsPrompt(lines: List<String>): Boolean {
        // The last line, if prompt, only contains "(Pdb) ".
        // But in the past I had a case where the last lines was:
        // "(Pdb) <string>:658: FutureWarning: ..."
        // To be safe, use "startsWith" to check for prompt.
        return lines.lastOrNull()?.startsWith(LinePrefixes.DEBUGGER_PROMPT) == true
    }

    private fun getMarkedResult(lines: List<String>): MarkedResult? {
        // Sometimes the output is polluted with additional info messages.
        // For example pipenv prints a "Loading .env environment variables...": https://github.com/pypa/pipenv/issues/4027
        // To extract the right data, the result is surrounded with a marker.
        // This also allows to add temporary debug logs inside the "debugger_helpers"
        // without breaking the detection of the results.

        // The marked result may consist of multiple columns. For example a pandas DataFrame prints the first n rows.
        var marker: String? = null
        val markedLines = StringBuilder()
        for (line in lines) {
            if (marker != null) {
                markedLines.appendLine(line)
                if (line.contains(marker)) break
            } else {
                if (line.contains(RESULT_MARKER)) marker = RESULT_MARKER
                else if (line.contains(EXC_MARKER)) marker = EXC_MARKER
                if (marker != null) {
                    markedLines.appendLine(line)
                    if (line.substringAfter(marker, "").contains(marker)) {
                        break
                    }
                }
            }
        }
        return marker?.let{
            MarkedResult(
                markedLines.toString().trim().removeSurrounding(marker),
                it,
            )
        }
    }

    private fun unescapeDebuggerOutput(output: List<String>): List<String> {
        return output.map { it.replace(NEW_LINE_MARKER, "\n") }
    }

    private fun escapeDebuggerInput(input: String): String {
        val sanitizedInput = input
            // new lines have to be escaped to forward multiline expressions as a single line
            .replace("\n", NEW_LINE_MARKER)
            // single quotes have to be escaped to be able to surround the expression with ''
            .replace("'", "\\'")
        return "'$sanitizedInput'"
    }

    private class ContinueTask: Task<Unit>()
    private class QuitTask: Task<Unit>()
    private class DumpTracesOnExitTask(val filePath: String): Task<Unit>()

    private class EvaluateTask(val request: EvalOrExecRequest) : Task<EvalOrExecResponse>() {
        override fun completeWithResult(result: MarkedResult?) {
            this.result.complete(createResponse(result))
        }

        private fun createResponse(result: MarkedResult?): EvalOrExecResponse {
            return if (result?.marker == EXC_MARKER) {
                MarkedResult.parseToError(result).let {
                    EvalOrExecResponse(
                        value = it.cause,
                        type = it.type,
                        typeQualifier = it.typeQualifier,
                        isError = true,
                    )
                }
            } else if (result == null || request.execute) {
                EvalOrExecResponse(null, null, null, false, null)
            } else MarkedResult.parseToValue(result, request.trimResult).let {
                EvalOrExecResponse(
                    value = it.value,
                    type = it.type,
                    typeQualifier = it.typeQualifier,
                    isError = false,
                    refId = it.refId,
                )
            }
        }
    }
}
