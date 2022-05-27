/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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

import cms.rendner.debugger.impl.DockeredPythonEvalDebugger
import cms.rendner.debugger.impl.EvaluateRequest
import cms.rendner.debugger.impl.EvaluateResponse
import cms.rendner.debugger.impl.PythonEvalDebugger
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

/**
 * Abstract class which provides access to a dockered Python interpreter with a pre-configured
 * pip-env environment.
 *
 * The docker image to use, has to be specified via the system properties
 * [SystemPropertyEnum.DOCKERED_TEST_IMAGE.key] and [SystemPropertyEnum.DOCKERED_TEST_WORKDIR.key].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractPipEnvEnvironmentTest {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private var debuggerStarted = false
    private val debugger = DockeredPythonEvalDebugger(
        System.getProperty(SystemPropertyEnum.DOCKERED_TEST_IMAGE.key),
        System.getProperty(SystemPropertyEnum.DOCKERED_TEST_WORKDIR.key),
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
            return try {
                evalOrExec(expression, execute = false, doTrunc = trimResult, EvaluateException.EVAL_FALLBACK_ERROR_MSG)
            } catch (ex: PluginPyDebuggerException) {
                throw EvaluateException(EvaluateException.EVAL_FALLBACK_ERROR_MSG, ex)
            }
        }

        override fun execute(statements: String) {
            try {
                evalOrExec(statements, execute = true, doTrunc = false, EvaluateException.EXEC_FALLBACK_ERROR_MSG)
            } catch (ex: PluginPyDebuggerException) {
                throw EvaluateException(EvaluateException.EXEC_FALLBACK_ERROR_MSG, ex)
            }
        }

        fun evalOrExec(code: String, execute: Boolean, doTrunc: Boolean, fallbackErrorMessage: String): PluginPyValue {
            return try {
                createPluginPyValue(
                    pythonDebugger.submit(EvaluateRequest(code, execute, doTrunc)).get(),
                    fallbackErrorMessage,
                )
            } catch (ex: ExecutionException) {
                throw ex.cause ?: EvaluateException(ex.message ?: "Unknown error occurred.")
            }
        }

        private fun createPluginPyValue(response: EvaluateResponse, fallbackErrorMessage: String): PluginPyValue {
            if (response.isError) {
                throw EvaluateException(response.value ?: fallbackErrorMessage)
            }
            return PluginPyValue(
                response.value,
                response.type ?: "",
                response.typeQualifier ?: "",
                response.refId ?: "",
                this,
            )
        }
    }
}