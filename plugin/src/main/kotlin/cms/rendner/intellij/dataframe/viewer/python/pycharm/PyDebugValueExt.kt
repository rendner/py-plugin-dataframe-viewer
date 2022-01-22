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
package cms.rendner.intellij.dataframe.viewer.python.pycharm

import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.PyFrameAccessor

fun PyDebugValue.isDataFrame(): Boolean = this.qualifiedType == "pandas.core.frame.DataFrame"
fun PyDebugValue.isStyler(): Boolean = this.qualifiedType == "pandas.io.formats.style.Styler"
fun PyDebugValue.isNone(): Boolean = qualifiedType == PythonQualifiedTypes.None.value
fun PyDebugValue.varName(): String {
    return if (this.name.startsWith("'")) {
        // like: "'styler' (1234567890)"
        this.name.substring(1, this.name.indexOf("'", 1))
    }
    // like "0" (for example index 0 in a tuple)
    else this.name
}

fun PyDebugValue.toPluginType(): PluginPyValue {
    return PluginPyValue(
        value,
        isErrorOnEval,
        type ?: "",
        typeQualifier ?: "",
        evaluationExpression,
        FrameAccessorBasedValueEvaluator(frameAccessor),
    )
}

private class FrameAccessorBasedValueEvaluator(private val frameAccessor: PyFrameAccessor) : IPluginPyValueEvaluator {

    @Throws(EvaluateException::class)
    override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
        // in case of a debugger timeout null is returned
        val result: PyDebugValue? = try {
            frameAccessor.evaluate(expression, false, trimResult)
        } catch (ex: PyDebuggerException) {
            throw EvaluateException("Couldn't evaluate expression.", expression, ex.toPluginType())
        }
        if (result == null || result.isErrorOnEval) {
            throw EvaluateException(result?.value ?: "Couldn't evaluate expression.", expression)
        }
        return result.toPluginType()
    }

    @Throws(EvaluateException::class)
    override fun execute(statement: String) {
        try {
            // in case of a debugger timeout null is returned
            val result: PyDebugValue? = frameAccessor.evaluate(statement, true, false)
            if (result == null || result.isErrorOnEval) {
                throw EvaluateException(result?.value ?: "Couldn't evaluate statement.", "")
            }
        } catch (ex: PyDebuggerException) {
            throw EvaluateException("Couldn't execute statement.", statement, ex.toPluginType())
        }
    }
}