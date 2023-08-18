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
package cms.rendner.intellij.dataframe.viewer.python.pycharm.listeners

import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersion
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersionInSessionProvider
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.jetbrains.python.debugger.PyDebugValue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Evaluates the pandas version available in a debug process.
 * The version is only evaluated once on start of the debug session.
 *
 * The result can be retrieved via: [PandasVersionInSessionProvider.getVersion(myDebugSession)].
 */
class EvalPandasVersionDebugSessionListener(private val session: XDebugSession): XDebugSessionListener {
    /**
     * AtomicBoolean because [sessionResumed] and [sessionPaused] are called from different threads.
     */
    private val pendingEval = AtomicBoolean(false)

    override fun sessionStopped() {
        session.removeSessionListener(this)
        PandasVersionInSessionProvider.remove(session)
    }

    override fun sessionResumed() {
        if (PandasVersionInSessionProvider.getVersion(session) == null) {
            // reset if pending, to retry next time
            pendingEval.compareAndSet(true, false)
        }
    }

    override fun sessionPaused() {
        session.debugProcess.evaluator?.let {
            if (!pendingEval.compareAndSet(false, true)) return
            it.evaluate(
                "__import__('pandas').__version__",
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun errorOccurred(errorMessage: String) {
                        // Only a paused session can evaluate expressions (doesn't matter if current session or not).
                        // If not paused, ignore error.
                        if (session.isPaused) {
                            // "errorMessage" could be something like: "{ModuleNotFoundError}No module named 'pandas'"
                            pendingEval.set(false)
                        }
                    }

                    override fun evaluated(result: XValue) {
                        if (result is PyDebugValue) {
                            pendingEval.set(false)
                            result.value?.let { v ->
                                if (v.isNotEmpty()) {
                                    PandasVersionInSessionProvider.setVersion(session, PandasVersion.fromString(v))
                                }
                            }
                        }
                    }
                },
                null,
            )
        }
    }
}