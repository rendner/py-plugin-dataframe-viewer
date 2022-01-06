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
package cms.rendner.debugger

import cms.rendner.debugger.impl.*
import cms.rendner.intellij.dataframe.viewer.SystemPropertyEnum
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import org.junit.jupiter.api.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal open class AbstractPipEnvEnvironmentTest {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private var debuggerStarted = false
    private val debugger = DockeredPythonEvalDebugger(
        System.getProperty(SystemPropertyEnum.DOCKERED_TEST_IMAGE.key),
        System.getProperty(SystemPropertyEnum.DOCKERED_TEST_PIPENV_ENVIRONMENT.key),
    )

    @BeforeAll
    protected fun initializeDebuggerContainer() {
        debugger.startContainer()
    }

    @AfterAll
    protected fun destroyDebuggerContainer() {
        debugger.destroyContainer()
        executorService.shutdown()
        executorService.awaitTermination(5, TimeUnit.SECONDS)
    }

    @BeforeEach
    protected fun resetDebugger() {
        debugger.reset()
    }

    @AfterEach
    protected fun shutdownDebugger() {
        debuggerStarted = false
        debugger.shutdown()
    }

    protected fun runWithPythonDebugger(
        block: (valueEvaluator: IPluginPyValueEvaluator, debugger: PythonEvalDebugger) -> Unit,
    ) {
        checkAndSetDebuggerStarted()
        executorService.submit {
            debugger.startWithCodeSnippet("breakpoint()")
        }
        block(MyValueEvaluator(debugger), debugger)
    }

    protected fun runWithPythonDebugger(
        sourceFile: String,
        block: (valueEvaluator: IPluginPyValueEvaluator, debugger: PythonEvalDebugger) -> Unit,
    ) {
        checkAndSetDebuggerStarted()
        executorService.submit {
            debugger.startWithSourceFile(sourceFile)
        }
        block(MyValueEvaluator(debugger), debugger)
    }

    private fun checkAndSetDebuggerStarted() {
        if (debuggerStarted) {
            throw IllegalStateException("Only one debugger instance allowed per testcase.")
        }
        debuggerStarted = true
    }

    private class MyValueEvaluator(private val pythonDebugger: PythonEvalDebugger) : IPluginPyValueEvaluator {
        override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
            val result = try {
                evalOrExec(expression, execute = false, doTrunc = trimResult)
            } catch (ex: PluginPyDebuggerException) {
                throw EvaluateException("Couldn't evaluate expression.", expression, ex)
            }
            if (result.isErrorOnEval) {
                throw EvaluateException(result.value ?: "Couldn't evaluate expression.", expression)
            }

            return result
        }

        override fun execute(statement: String) {
            try {
                evalOrExec(statement, execute = true, doTrunc = false)
            } catch (ex: PluginPyDebuggerException) {
                throw EvaluateException("Couldn't execute statement.", statement, ex)
            }
        }

        fun evalOrExec(code: String, execute: Boolean, doTrunc: Boolean): PluginPyValue {
            try {
                val result = pythonDebugger.submit(EvaluateRequest(code, execute, doTrunc)).get()
                return createPluginPyValue(result)
            } catch (ex: ExecutionException) {
                ex.cause?.let {
                    throw it
                }
                throw EvaluateException(ex.message ?: "Unknown error occurred.", code)
            }
        }

        private fun createPluginPyValue(response: EvaluateResponse): PluginPyValue {
            return PluginPyValue(
                response.value,
                response.isError,
                response.type ?: "",
                response.typeQualifier ?: "",
                response.refId ?: "",
                this,
            )
        }
    }
}