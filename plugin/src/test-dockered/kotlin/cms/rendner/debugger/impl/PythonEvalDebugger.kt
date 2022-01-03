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

import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

// linebreak substitution adapted from PyCharm: https://github.com/JetBrains/intellij-community/search?q=%40_%40NEW_LINE_CHAR%40_%40
private const val NEW_LINE_CHAR = "@_@NEW_LINE_CHAR@_@"

abstract class PythonEvalDebugger {

    private data class OpenTask(val request: EvaluateRequest, val result: CompletableFuture<EvaluateResponse>)

    private val openTasks = ArrayBlockingQueue<OpenTask>(1)

    @Volatile
    private var shutdownRequested: Boolean = false

    @Volatile
    private var isRunning: Boolean = false

    private val pythonErrorRegex = ".*\\*{3}[ ]\\w*Error:[ ].+".toRegex()

    private enum class LinePrefixes(val label: String) {
        DEBUGGER_PROMPT("(Pdb) "),
    }

    /**
     * Submits an evaluation request.
     * The requests are processed as soon as the used pythonProcess enters the debug mode.
     */
    fun submit(request: EvaluateRequest): Future<EvaluateResponse> {
        val result = CompletableFuture<EvaluateResponse>()
        openTasks.put(OpenTask(request, result))
        return result
    }

    /**
     * Requests a shutdown of the debugger.
     */
    fun shutdown() {
        shutdownRequested = true
    }

    /**
     * Clears all submitted open tasks and resets the shutdownRequested flag.
     * Throws a PluginPyDebuggerException if called on a running debugger.
     */
    fun reset() {
        if (isRunning) {
            throw PluginPyDebuggerException("Can't reset a running debugger.")
        }
        openTasks.clear()
        shutdownRequested = false
    }


    /**
     * The [pythonProcess] must have been started with a source file or code that contain a line with "breakpoint()"
     * to be able to switch into the debug mode. The line should be placed at the point where the program should
     * be interrupted. All interactions are done at the location of the mentioned line.
     *
     * [pythonProcess] must be a started python interpreter and not the Python Debugger (Pdb).
     * Starting directly in debug mode isn't supported.
     *
     * Throws a PluginPyDebuggerException if called on an already started debugger.
     */
    protected fun start(pythonProcess: PythonProcess) {
        if (isRunning) {
            throw PluginPyDebuggerException("Can't re-start an already running debugger")
        }
        try {
            if (findEntryPointAndInjectDebuggerInternals(pythonProcess)) {
                processOpenTasks(pythonProcess)
            }
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
        } finally {
            isRunning = false
        }
    }

    private fun findEntryPointAndInjectDebuggerInternals(pythonProcess: PythonProcess): Boolean {
        var checkCounter = 30

        while (checkCounter-- > 0) {
            checkProcessIsAlive(pythonProcess)
            if (pythonProcess.canRead()) {
                val lines = sanitizeDebuggerOutput(pythonProcess.readLinesNonBlocking())
                if (lastLineIsPrompt(lines)) {
                    pythonProcess.writeLine(
                        "!exec(${sanitizeDebuggerInput(createDebuggerInternalsInstanceSnippet(), "\\n")})"
                    )
                    return true
                } else {
                    if (lines.isNotEmpty()) {
                        throw PluginPyDebuggerException(lines.joinToString(System.lineSeparator()))
                    }
                }
            } else {
                Thread.sleep(100)
            }
        }

        throw PluginPyDebuggerException("Couldn't find entry point 'breakpoint()'.")
    }

    private fun processOpenTasks(pythonProcess: PythonProcess) {

        var activeTask: OpenTask? = null
        var lines: List<String> = emptyList()
        var stoppedAtInputPrompt = false
        var checkForInputPrompt = true

        try {
            while (!shutdownRequested && !Thread.currentThread().isInterrupted) {

                checkProcessIsAlive(pythonProcess)

                if (checkForInputPrompt && pythonProcess.canRead()) {
                    lines = sanitizeDebuggerOutput(pythonProcess.readLinesNonBlocking())
                    stoppedAtInputPrompt = lastLineIsPrompt(lines)
                }

                if (stoppedAtInputPrompt) {
                    checkForInputPrompt = false

                    var nextDebugCommand: String? = null

                    if (activeTask != null) {
                        completeTaskWithProcessOutput(activeTask, lines)
                        activeTask = null
                    }

                    if (openTasks.isNotEmpty()) {
                        activeTask = openTasks.take()
                        val expression = sanitizeDebuggerInput(activeTask.request.expression)
                        nextDebugCommand = if (activeTask.request.execute) {
                            "!__debugger_internals__.exec($expression)"
                        } else {
                            "!__debugger_internals__.eval($expression)"
                        }
                    }

                    if (nextDebugCommand != null) {
                        pythonProcess.writeLine("$nextDebugCommand")
                        stoppedAtInputPrompt = false
                        checkForInputPrompt = true
                    }
                }
            }

            activeTask?.let {
                completeTaskWithError(it, PluginPyDebuggerException("Evaluation was canceled."))
            }
        } catch (e: Throwable) {
            activeTask?.let {
                completeTaskWithError(it, e)
            }
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            throw e
        }
    }

    private fun completeTaskWithProcessOutput(task: OpenTask, processOutput: List<String>) {
        val result = createEvaluateResponse(getEvaluationResult(processOutput), task.request)
        if (result.isError && task.request.execute) {
            completeTaskWithError(task, PluginPyDebuggerException(result.value ?: "Error occurred during exec." ))
        } else {
            task.result.complete(result)
        }
    }

    private fun completeTaskWithError(task: OpenTask, throwable: Throwable) {
        if (throwable is PluginPyDebuggerException) {
            task.result.completeExceptionally(throwable)
        } else {
            task.result.completeExceptionally(PluginPyDebuggerException("Unknown error occurred.", throwable))
        }
    }

    private fun checkProcessIsAlive(pythonProcess: PythonProcess) {
        if (!pythonProcess.isAlive()) {
            // In PyCharm a "PyDebuggerException" with the message "Disconnected"
            // is thrown in case the debugger was disconnected.
            throw PluginPyDebuggerException("Disconnected")
        }
    }

    private fun lastLineIsPrompt(lines: List<String>): Boolean {
        if (lines.isNotEmpty()) {
            if (lines.last() == LinePrefixes.DEBUGGER_PROMPT.label) {
                return true
            }
        }
        return false
    }

    private fun getEvaluationResult(lines: List<String>): String? {
        return if (lines.isNotEmpty()) {
            if (lines.last() == LinePrefixes.DEBUGGER_PROMPT.label && lines.size > 1) {
                lines[lines.size - 2]
            } else {
                lines.joinToString(System.lineSeparator())
            }
        } else null
    }

    private fun String.splitAtIndex(index: Int): Pair<String, String> {
        return Pair(this.substring(0, index), this.substring(index + 1))
    }

    private fun createEvaluateResponse(result: String?, request: EvaluateRequest): EvaluateResponse {
        return if (result != null && pythonErrorRegex.matches(result)) {
            return EvaluateResponse(value = result, isError = true)
        } else if (request.execute || result == null) {
            EvaluateResponse()
        } else {
            val unquotedResult = if (result.startsWith("'")) {
                result.removeSurrounding("'")
            } else {
                // result which contains a stringified dict with string keys as value is surrounded by "
                result.removeSurrounding("\"")
            }

            val separatorAfterTypeInfo = unquotedResult.indexOf(" ")
            val separatorAfterRefId = unquotedResult.indexOf(" ", separatorAfterTypeInfo + 1)

            val qualifiedType =
                unquotedResult.substring(0, separatorAfterTypeInfo).let { it.splitAtIndex(it.lastIndexOf(".")) }
            val refId = unquotedResult.substring(separatorAfterTypeInfo + 1, separatorAfterRefId)
            var value = unquotedResult.substring(separatorAfterRefId + 1)

            if (request.trimResult && value.length > 100) {
                value = "${value.substring(0, 100)}..."
            }

            EvaluateResponse(
                value = value,
                type = qualifiedType.second,
                typeQualifier = qualifiedType.first,
                refId = refId,
            )
        }
    }

    /*
    All helper methods are put into a single class to not pollute the globals.
    The class and the created instance are visible after injection and therefore
    accessible from outside the debugger (unwanted behavior).

    Visible means, an evaluator could also evaluate something like this:

    evaluator.evaluate("__debugger_internals__.eval('1')")

    Note:
        Linebreaks in strings passed to "eval"/"exec" have to be escaped.

        Examples:

        default string behavior:
            "a\nb".split("\n")              => ['a', 'b']
            "a\\nb".split("\n")             => ['a\\nb']

        eval:
            eval("'a\nb'").split("\n")      => {SyntaxError}EOL while scanning string literal (<string>, line 1)
            eval("'a\\nb'").split("\n")     => ['a', 'b']

        exec:
            exec("a = 'a\nb'")              => {SyntaxError}EOL while scanning string literal (<string>, line 1)
            exec("a = 'a\\nb'")             => a = ['a', 'b']
     */
    private fun createDebuggerInternalsInstanceSnippet(): String {
        return """
import inspect
import os


class __DebuggerInternals__:

    def __init__(self):
        self.id_counter = 0
        self.excluded_result_types = (int, float, str, bool)

    @staticmethod
    def __full_type(o) -> str:
        klass = getattr(o, '__class__', '')
        module = getattr(klass, '__module__', '')
        qname = getattr(klass, '__qualname__', '')
        return f'{module}.{qname}'

    def eval(self, expression) -> str:
        if isinstance(expression, str):
            expression = expression.replace("\\$NEW_LINE_CHAR", "\\\\n")
            expression = expression.replace("$NEW_LINE_CHAR", "\\n")
            
        # get the caller's frame
        previous_frame = inspect.currentframe().f_back
        result = eval(expression, previous_frame.f_globals, previous_frame.f_locals)

        if isinstance(result, str):
            result = result.replace("\\\\n", "\\$NEW_LINE_CHAR")
            result = result.replace("\\n", "$NEW_LINE_CHAR")

        ref_key = None
        if result is not None and not isinstance(result, self.excluded_result_types):
            # store created object and make it accessible
            ref_key = f'__dbg_ref_id_{self.id_counter}'
            previous_frame.f_locals[ref_key] = result
            self.id_counter += 1
        return f'{self.__full_type(result)} {ref_key} {result}'

    @staticmethod
    def exec(value) -> None:
        if isinstance(value, str):
            value = value.replace("\\$NEW_LINE_CHAR", "\\\\n")
            value = value.replace("$NEW_LINE_CHAR", "\\n")
        # get the caller's frame
        previous_frame = inspect.currentframe().f_back
        exec(value, previous_frame.f_globals, previous_frame.f_locals)


__debugger_internals__ = __DebuggerInternals__()
        """
    }

    private fun sanitizeDebuggerOutput(output: List<String>): List<String> {
        return output.map {
            it
                .replace("\\$NEW_LINE_CHAR", "\\n")
                .replace(NEW_LINE_CHAR, "\n")
        }
    }

    private fun sanitizeDebuggerInput(input: String, lineSeparatorReplacement: String = NEW_LINE_CHAR): String {
        /*
         The "input" has to be sanitized to not break the debugger.
         For example calling "__debugger_internals__.eval(x = 2)"
         with "x = 2" (without quotes) as input, would lead to a:

         *** TypeError: eval() got an unexpected keyword argument 'x'
       */

        var sanitizedInput = input
            .replace("\n", lineSeparatorReplacement)
            .replace("'", "\\'")
            .replace("\"", "\\\"")

        if (lineSeparatorReplacement != "\\n") {
            sanitizedInput = sanitizedInput.replace("\\n", "\\$lineSeparatorReplacement")
        }

        return "'$sanitizedInput'"
    }
}
