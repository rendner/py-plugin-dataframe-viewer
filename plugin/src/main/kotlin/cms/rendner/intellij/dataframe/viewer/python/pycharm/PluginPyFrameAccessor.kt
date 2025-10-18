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
package cms.rendner.intellij.dataframe.viewer.python.pycharm

import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.debugger.AbstractPluginFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import cms.rendner.intellij.dataframe.viewer.services.PluginCodeInjector
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.python.console.PydevConsoleCommunication
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.console.pydev.PydevCompletionVariant
import com.jetbrains.python.debugger.IPyDebugProcess
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.rd.util.AtomicReference
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class SharedStuff {
    val isAlive = AtomicBoolean(true)
    val errorCausedByStoppedSessionDetectedCallback = AtomicReference<(() -> Unit)?>(null)

    fun setSessionStopped() {
        if (isAlive.compareAndSet(true, false)) {
            errorCausedByStoppedSessionDetectedCallback.getAndSet(null)
        }
    }
}

class PluginPyFrameAccessor(
    private val originalFrameAccessor: PyFrameAccessor,
    private val uniqueId: String,
) : AbstractPluginFrameAccessor() {

    val shared = SharedStuff()
    private val logger = Logger.getInstance(this::class.java)

    override fun isAlive() = shared.isAlive.get()

    override fun getUniqueId(): String {
        return uniqueId
    }

    override fun getDisplayName(): String? {
        if (originalFrameAccessor is IPyDebugProcess) {
            return originalFrameAccessor.session.sessionName
        } else if (originalFrameAccessor is PydevConsoleCommunication) {
            return "Python Console"
        }
        return null
    }

    @Throws(EvaluateException::class)
    override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
        var result: PyDebugValue? = null

        try {
            result = originalFrameAccessor.evaluate(expression, false, trimResult)
                ?: throw createEvaluateException(null, "Evaluation aborted, timeout threshold reached.")

            if (result.isErrorOnEval) {
                throw createEvaluateException(
                    result,
                    "{${result.type}} ${result.value ?: EvaluateException.EVAL_FALLBACK_ERROR_MSG}",
                )
            }

            return result.toPluginType()
        } catch (ex: Exception) {
            throw createEvaluateException(ex, EvaluateException.EVAL_FALLBACK_ERROR_MSG)
        } finally {
            cleanupResult(result)
        }
    }

    @Throws(EvaluateException::class)
    override fun execute(statement: String) {
        val s = when {
            originalFrameAccessor.simulateExec() -> {
                // In such a case the exec of the frameAccessor does the same as evaluate,
                // which requires a single line statement.
                toEscapedSingleLineExec(statement)
            }
            else -> statement
        }

        var result: PyDebugValue? = null
        try {
            result = originalFrameAccessor.evaluate(s, true, false)
                ?: throw createEvaluateException(null, "Execution aborted, timeout threshold reached.")

            if (result.isErrorOnEval) {
                throw createEvaluateException(
                    result,
                    result.value ?: EvaluateException.EXEC_FALLBACK_ERROR_MSG,
                )
            }
        } catch (ex: Exception) {
            throw createEvaluateException(ex, EvaluateException.EXEC_FALLBACK_ERROR_MSG)
        } finally {
            cleanupResult(result)
        }
    }

    private fun createEvaluateException(result: Any?, message: String): EvaluateException {
        if (result is EvaluateException) return result
        val pythonErrorQName = (result as? PyDebugValue)?.qualifiedType
        var wrappedCause = (result as? Exception)?.let {
            PluginPyDebuggerException.fromPyCharmDebuggerException(result, result.message == "Disconnected")
        }

        if (pythonErrorQName == PythonQualifiedTypes.ModuleNotFoundError && result.value?.contains("cms_rendner_sdfv") == true) {
            originalFrameAccessor.project?.service<PluginCodeInjector>()?.let { injector ->
                injector.getInjectionState(this)?.let { state ->
                    if (state.moduleImporterAvailable.get()) {
                        // invalid state found - create a disconnect exception
                        wrappedCause = PluginPyDebuggerException.fromPyCharmDebuggerException(
                            IllegalStateException("Injected pluginCode not found"),
                            true
                        )
                        logger.warn("Injected pluginCode not found. FrameAccessor: $originalFrameAccessor")
                    }
                }
            }
        }

        return EvaluateException(
            "${originalFrameAccessor.javaClass.simpleName}: $message",
            pythonErrorQName,
            wrappedCause,
        ).also {
            if (shared.isAlive.get()) {
                if (it.isCausedByDisconnectException()) {
                    shared.errorCausedByStoppedSessionDetectedCallback.get()?.invoke()
                }
            }
        }
    }

    private fun cleanupResult(result: PyDebugValue?) {
        // auto cleanup to not leak plugin variables to the user
        result?.let { variable ->
            if (shared.isAlive.get() && variable.isReturnedVal || variable.isTemporary) {
                val varName = variable.tempName ?: return
                try {
                    originalFrameAccessor.evaluate("del $varName", true, true)
                } catch (_: Exception) { }
            }
        }
    }

    override fun getCurrentSourcePosition(): XSourcePosition? {
        return (originalFrameAccessor as? IPyDebugProcess)?.session?.currentPosition
    }

    override fun getConsoleCompletions(
        text: String,
        actTok: String
    ): List<PydevCompletionVariant> {
        return (originalFrameAccessor as? ConsoleCommunication)?.getCompletions(text, actTok) ?: emptyList()
    }

    override fun isDebugConsole(): Boolean {
        return originalFrameAccessor is ConsoleCommunication
    }
}

