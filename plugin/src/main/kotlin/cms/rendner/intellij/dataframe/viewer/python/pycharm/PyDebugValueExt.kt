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

/**
 * Converts the PyCharm [PyDebugValue] into a plugin internal value.
 */
fun PyDebugValue.toPluginType(): PluginPyValue {
    return PluginPyValue(
        value,
        type ?: "",
        typeQualifier ?: "",
        // workaround for https://youtrack.jetbrains.com/issue/PY-55925
        // tempName is preferred to not re-evaluate same expr multiple times
        if (tempName == name) evaluationExpression else tempName ?: evaluationExpression,
        FrameAccessorBasedValueEvaluator(frameAccessor),
    )
}

/**
 * Creates a [PyDebugValueEvalExpr] which can be used to refer to the [PyDebugValue].
 *
 * Note, the "evaluationExpression" of the [PyDebugValue] is modified to fix a problem with tempNames when
 * the value was evaluated by the IntelliJ evaluate-expression-dialog.
 *
 * Traceability:
 * Debugger hit a breakpoint, the current stack frame contains a Python variable named "df" of type DataFrame.
 *
 * A)
 * When in the variable view of the debugger the following path is expanded "df.style.data" then the two
 * properties for "df.style.data" would have the following values:
 * "name=data, evaluationExpression=df.style.data" (the PyDebugValue has a parent)
 *
 * B)
 * When the IntelliJ evaluate-expression-dialog is opened and "df.style.data" is evaluated, then the two
 * properties of the evaluated result would have the following values like:
 * "name=df.style.data, evaluationExpression=__py_debug_temp_var_1708238885" (the PyDebugValue has no parent)
 *
 * In both cases we would like to have "df.style.data" as re-evaluate expression. Luckily the
 * "evaluationExpression" contains in most cases already the right expression and handles also values
 * stored in list, dict, tuples and set.
 */
fun PyDebugValue.toValueEvalExpr(): PyDebugValueEvalExpr {
    var evalExpr = evaluationExpression
    val tp = topParent
    if (tp != null) {
        val topParentTempName = tp.tempName
        if (topParentTempName != null && evalExpr.startsWith(PyDebugValueEvalExpr.TEMP_NAME_PREFIX)) {
            // If there is a topParent then replace the tempName of the topParent with his name.
            // Fix for described case "B".
            evalExpr = evalExpr.replace(topParentTempName, tp.name)
        }
    }

    // workaround for https://youtrack.jetbrains.com/issue/PY-55925
    // tempName is preferred to not re-evaluate same expr multiple times
    val frameRefExpr = if (tempName == name) evalExpr else tempName ?: evalExpr

    return PyDebugValueEvalExpr(evalExpr, frameRefExpr, qualifiedType)
}

/**
 * Stores expressions to refer to a PyCharm [PyDebugValue].
 *
 * Evaluated values exist only within the frame in which they were evaluated.
 * For this reason, they must be re-evaluated if the frame has changed.
 *
 * @param reEvalExpr a "path" to re-evaluate the value after stack frame change.
 * @param currentFrameRefExpr the "evaluationExpression" of a [PyDebugValue] to access the object.
 * @param qualifiedType the qualified type of the referenced value.
 */
data class PyDebugValueEvalExpr(
    val reEvalExpr: String,
    val currentFrameRefExpr: String,
    val qualifiedType: String?,
) {
    fun canBeReEvaluated(): Boolean {
        return !reEvalExpr.startsWith(TEMP_NAME_PREFIX)
    }

    companion object {
        const val TEMP_NAME_PREFIX = "__py_debug_temp_var_"
    }

    fun withUpdatedRefExpr(currentFrameRefExpr: String): PyDebugValueEvalExpr {
        return PyDebugValueEvalExpr(reEvalExpr, currentFrameRefExpr, qualifiedType)
    }
}


private class FrameAccessorBasedValueEvaluator(private val frameAccessor: PyFrameAccessor) : IPluginPyValueEvaluator {
    @Throws(EvaluateException::class)
    override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
        try {
            val result: PyDebugValue = frameAccessor.evaluate(expression, false, trimResult)
                ?: throw EvaluateException("Evaluation aborted, timeout threshold reached.")
            if (result.isErrorOnEval) {
                throw EvaluateException("{${result.type}} ${result.value ?: EvaluateException.EVAL_FALLBACK_ERROR_MSG}")
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