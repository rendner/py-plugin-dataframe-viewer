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
package cms.rendner.intellij.dataframe.viewer.python.pycharm

import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.PyFrameAccessor

fun PyDebugValue.isDataFrame(): Boolean = this.qualifiedType == "pandas.core.frame.DataFrame"
fun PyDebugValue.isStyler(): Boolean = this.qualifiedType == "pandas.io.formats.style.Styler"

/**
 * Converts the PyCharm [PyDebugValue] into a plugin internal value.
 */
fun PyDebugValue.toPluginType(): PluginPyValue {
    return PluginPyValue(
        value,
        type ?: "",
        typeQualifier ?: "",
        evaluationExpression,
        FrameAccessorBasedValueEvaluator(frameAccessor),
    )
}

private class FrameAccessorBasedValueEvaluator(private val frameAccessor: PyFrameAccessor) : IPluginPyValueEvaluator {
    @Throws(EvaluateException::class)
    override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
        try {
            val result: PyDebugValue = frameAccessor.evaluate(expression, false, trimResult)
                ?: throw EvaluateException("Evaluation aborted, timeout threshold reached.")
            if (result.isErrorOnEval) {
                throw EvaluateException(result.value ?: EvaluateException.EVAL_FALLBACK_ERROR_MSG)
            }
            return result.toPluginType()
        } catch (ex: PyDebuggerException) {
            throw EvaluateException(EvaluateException.EVAL_FALLBACK_ERROR_MSG, ex.toPluginType())
        }
    }

    @Throws(EvaluateException::class)
    override fun execute(statements: String) {
        try {
            val result: PyDebugValue = frameAccessor.evaluate(statements, true, false)
                ?: throw EvaluateException("Execution aborted, timeout threshold reached.")
            if (result.isErrorOnEval) {
                throw EvaluateException(result.value ?: EvaluateException.EXEC_FALLBACK_ERROR_MSG)
            }
        } catch (ex: PyDebuggerException) {
            throw EvaluateException(EvaluateException.EXEC_FALLBACK_ERROR_MSG, ex.toPluginType())
        }
    }
}