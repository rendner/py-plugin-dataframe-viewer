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
package cms.rendner.debugger

import cms.rendner.TestProperty
import cms.rendner.debugger.impl.*
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Abstract class which provides access to a dockered Python interpreter with a pre-configured
 * pipenv environment.
 *
 * The docker image to use, has to be specified via the system properties used in
 * [TestProperty.getDockerImage] and [TestProperty.getDockerWorkdir].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractPipEnvEnvironmentTest {
    private var executorService: ExecutorService? = null
    private var debugger: PythonDebugger? = null

    companion object {
        fun getPipEnvEnvironmentName(): String {
            return TestProperty.getDockerWorkdir().substringAfterLast("/").ifEmpty { "?" }
        }
    }

    private val pipEnvEnvironment = DockeredPipEnvEnvironment(
        TestProperty.getDockerImage(),
        TestProperty.getDockerWorkdir(),
        TestProperty.getDockerVolumes(),
    )

    @BeforeAll
    protected fun initializeDebuggerContainer() {
        pipEnvEnvironment.runContainer()
        afterContainerStart()
    }

    protected open fun afterContainerStart() {}

    @AfterAll
    protected fun destroyDebuggerContainer() {
        pipEnvEnvironment.destroyContainer()
    }

    @AfterEach
    protected fun afterEach() {
        debugger?.quit()
        debugger = null

        executorService?.shutdownNow()
        executorService?.awaitTermination(5, TimeUnit.SECONDS)
        executorService = null
    }

    protected fun uninstallPythonModules(vararg modules: String) {
        pipEnvEnvironment.uninstallPythonModules(*modules)
    }

    protected open fun createPythonDebuggerWithCodeSnippet(
        codeSnippet: String,
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
    ) = runWithDebugger(block) { pipEnvEnvironment.createPythonDebuggerWithCodeSnippet(codeSnippet) }

    protected open fun createPythonDebuggerWithSourceFile(
        sourceFile: String,
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
    ) = runWithDebugger(block) { pipEnvEnvironment.createPythonDebuggerWithSourceFile(sourceFile) }

    protected fun createPythonDebugger(block: (debuggerApi: IPythonDebuggerApi) -> Unit) {
        createPythonDebuggerWithCodeSnippet("breakpoint()", block)
    }

    private fun runWithDebugger(
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
        createDebugger: () -> PythonDebugger,
    ) {
        if (debugger != null) {
            throw IllegalStateException("Only one debugger instance allowed per testcase.")
        }

        createDebugger().also {
            debugger = it
            executorService = Executors.newSingleThreadExecutor().also { es ->
                es.submit { it.start() }
            }
            block(MyDebuggerApi(it))
        }
    }

    @JvmField
    @RegisterExtension
    protected var dumpDebuggerTracesOnTestFailure = TestExecutionExceptionHandler { context, throwable ->
        val testCaseName = context.let {
            "${it.testClass.get().simpleName}::${it.testMethod.get().name}"
        }
        val filePath = "debugger_traces/${getPipEnvEnvironmentName()}/${LocalDateTime.now()}-$testCaseName.traces"
        debugger?.dumpTracesOnExit(filePath)
        throw throwable
    }

    private class MyDebuggerApi(private val pythonDebugger: PythonDebugger): IPythonDebuggerApi, IDebuggerInterceptor {
        private val myInterceptors = mutableListOf<IDebuggerInterceptor>()

        override val evaluator = MyValueEvaluator(pythonDebugger, this)

        override fun continueFromBreakpoint() {
            pythonDebugger.continueFromBreakpoint()
        }

        override fun addInterceptor(interceptor: IDebuggerInterceptor) {
            myInterceptors.add(interceptor)
        }

        override fun removeInterceptor(interceptor: IDebuggerInterceptor) {
            myInterceptors.remove(interceptor)
        }

        override fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest {
            return myInterceptors.fold(request) { req, interceptor -> interceptor.onRequest(req) }
        }

        override fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse {
            return myInterceptors.fold(response) { resp, interceptor -> interceptor.onResponse(resp) }
        }
    }


    private class MyValueEvaluator(private val pythonDebugger: PythonDebugger, private val interceptor: IDebuggerInterceptor) : IPluginPyValueEvaluator {
        override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
            return try {
                evalOrExec(expression, execute = false, doTrunc = trimResult, EvaluateException.EVAL_FALLBACK_ERROR_MSG)
            } catch (ex: PluginPyDebuggerException) {
                throw EvaluateException(EvaluateException.EVAL_FALLBACK_ERROR_MSG, null, ex)
            }
        }

        override fun execute(statements: String) {
            try {
                evalOrExec(statements, execute = true, doTrunc = false, EvaluateException.EXEC_FALLBACK_ERROR_MSG)
            } catch (ex: PluginPyDebuggerException) {
                throw EvaluateException(EvaluateException.EXEC_FALLBACK_ERROR_MSG, null, ex)
            }
        }

        fun evalOrExec(code: String, execute: Boolean, doTrunc: Boolean, fallbackErrorMessage: String): PluginPyValue {
            return try {
                val request = interceptor.onRequest(EvalOrExecRequest(code, execute, doTrunc))
                val response = interceptor.onResponse(pythonDebugger.evalOrExec(request))
                createPluginPyValue(response, fallbackErrorMessage)
            } catch (ex: ExecutionException) {
                throw ex.cause ?: EvaluateException(ex.message ?: "Unknown error occurred.", null)
            }
        }

        private fun createPluginPyValue(response: EvalOrExecResponse, fallbackErrorMessage: String): PluginPyValue {
            if (response.isError) {
                val msg = if (response.value == null) fallbackErrorMessage else "{${response.type}} ${response.value}"
                throw EvaluateException(msg, response.qualifiedType)
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