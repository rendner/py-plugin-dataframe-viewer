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

import EvaluateRequest
import EvaluateResponse
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

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
     * Throws an IllegalStateException if called on a running debugger.
     */
    fun reset() {
        if (isRunning) {
            throw IllegalStateException("Can't reset a running debugger.")
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
     */
    protected fun start(pythonProcess: PythonProcess) {
        if (isRunning) {
            throw IllegalStateException("Can't re-start a already running debugger")
        }
        try {
            if (findEntryPointAndInjectDebuggerInternals(pythonProcess)) {
                processOpenTasks(pythonProcess)
            } else {
                throw Exception("Couldn't find entry point 'breakpoint()'.")
            }
        } finally {
            isRunning = false
        }
    }

    private fun findEntryPointAndInjectDebuggerInternals(pythonProcess: PythonProcess): Boolean {
        var checkCounter = 20

        while (checkCounter-- > 0) {
            if (pythonProcess.canRead()) {
                if (lastLineIsPrompt(pythonProcess.readLinesNonBlocking())) {
                    pythonProcess.writeLine(
                        "!exec(${sanitizeDebuggerInput(createDebuggerInternalsInstanceSnippet())})"
                    )
                    return true
                }
            } else {
                Thread.sleep(200)
            }
        }

        return false
    }

    private fun processOpenTasks(pythonProcess: PythonProcess) {

        var pendingTask: OpenTask? = null
        var lines: List<String> = emptyList()
        var stoppedAtInputPrompt = false
        var checkForInputPrompt = true

        while (!shutdownRequested && !Thread.currentThread().isInterrupted) {

            if (checkForInputPrompt && pythonProcess.canRead()) {
                lines = pythonProcess.readLinesNonBlocking()
                stoppedAtInputPrompt = lastLineIsPrompt(lines)
            }

            if (stoppedAtInputPrompt) {
                checkForInputPrompt = false

                var nextDebugCommand: String? = null

                if (pendingTask != null) {
                    pendingTask.result.complete(
                        createEvaluateResponse(getEvaluationResult(lines), pendingTask.request.execute)
                    )
                    pendingTask = null
                }

                if (openTasks.isNotEmpty()) {
                    pendingTask = openTasks.take()
                    val expression = sanitizeDebuggerInput(pendingTask.request.expression)
                    nextDebugCommand = if (pendingTask.request.execute) {
                        "!exec($expression)"
                    } else {
                        "!__debugger_internals__.eval_with_type_info($expression)"
                    }
                }

                if (nextDebugCommand != null) {
                    pythonProcess.writeLine("$nextDebugCommand")
                    stoppedAtInputPrompt = false
                    checkForInputPrompt = true
                }
            } else {
                Thread.sleep(200)
            }
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
        if (lines.isNotEmpty()) {
            if (lines.last() == LinePrefixes.DEBUGGER_PROMPT.label && lines.size > 1) {
                return lines[lines.size - 2]
            }
        }
        return null
    }

    /*
    All helper methods are put into a single class to not pollute the globals.
    The class and the created instance are visible after injection and therefore
    accessible from outside the debugger (unwanted behavior).

    Visible means, an evaluator could also evaluate something like this:

    evaluator.evaluate("__debugger_internals__.eval_with_type_info('1')")
     */
    private fun createDebuggerInternalsInstanceSnippet(): String {
        return """
import inspect


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

    def eval_with_type_info(self, expression) -> str:
        # get the caller's frame
        previous_frame = inspect.currentframe().f_back
        result = eval(expression, previous_frame.f_globals, previous_frame.f_locals)

        ref_key = None
        if result is not None and not isinstance(result, self.excluded_result_types):
            # store created object and make it accessible
            ref_key = f'__dbg_ref_id_{self.id_counter}'
            previous_frame.f_locals[ref_key] = result
            self.id_counter += 1

        return f'{self.__full_type(result)} {ref_key} {result}'


__debugger_internals__ = __DebuggerInternals__()
        """
    }

    private fun String.splitAtIndex(index: Int): Pair<String, String> {
        return Pair(this.substring(0, index), this.substring(index + 1))
    }

    private fun createEvaluateResponse(result: String?, wasExecute: Boolean): EvaluateResponse {
        return if (result != null && pythonErrorRegex.matches(result)) {
            return EvaluateResponse(value = result, isError = true)
        } else if (wasExecute || result == null) {
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
            val value = unquotedResult.substring(separatorAfterRefId + 1)

            EvaluateResponse(
                value = value,
                type = qualifiedType.second,
                typeQualifier = qualifiedType.first,
                refId = refId,
            )
        }
    }

    private fun sanitizeDebuggerInput(input: String): String {
        /*
         The "input" has to be sanitized to not break the debugger.
         For example calling the helper method "eval_with_type_info(x = 2)"
         with "x = 2" (without quotes) as input, would lead to a:

         *** TypeError: eval_with_type_info() got an unexpected keyword argument 'x'

         Therefore, the "input" is always wrapped with triple quotes.
       */
        // todo: validate if this works on windows (os specific line separator)
        // (only relevant if executed with "LocalPythonEvalDebugger" on windows)
        return "'''" + (input
            .replace("\n", "\\n")
            .replace("'", "\\'")
            .replace("\"", "\\\"")) + "'''"
    }
}
