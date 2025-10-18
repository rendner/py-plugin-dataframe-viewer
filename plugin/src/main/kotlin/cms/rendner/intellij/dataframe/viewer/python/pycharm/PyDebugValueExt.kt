/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import com.jetbrains.python.debugger.PyDebugValue

/**
 * Converts the PyCharm [PyDebugValue] into a plugin internal value.
 */
fun PyDebugValue.toPluginType(): PluginPyValue {
    return PluginPyValue(
        value,
        type ?: "",
        typeQualifier ?: "",
        tempName ?: name
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
 * In both cases we would like to have "df.style.data" as displayName. Luckily the
 * "evaluationExpression" contains in most cases already the right expression and handles also values
 * stored in list, dict, tuples and set.
 */
fun PyDebugValue.toValueEvalExpr(): PyDebugValueEvalExpr {
    var displayName = evaluationExpression
    val tp = topParent
    if (tp != null) {
        val topParentTempName = tp.tempName
        if (topParentTempName != null && displayName.startsWith(PyDebugValueEvalExpr.TEMP_NAME_PREFIX)) {
            // If there is a topParent then replace the tempName of the topParent with his name.
            // Fix for described case "B".
            displayName = displayName.replace(topParentTempName, tp.name)
        }
    }

    // workaround for https://youtrack.jetbrains.com/issue/PY-55925
    // tempName is preferred to not re-evaluate same expr multiple times
    val refExpr = if (tempName == name) displayName else tempName ?: displayName

    return PyDebugValueEvalExpr(displayName, refExpr, qualifiedType)
}

/**
 * Stores expressions to refer to a PyCharm [PyDebugValue].
 *
 * @param displayName in most cases the var name or the [refExpr] itself.
 * @param refExpr the "evaluationExpression" of a [PyDebugValue] to access the object.
 * @param qualifiedType the qualified type of the referenced value.
 */
data class PyDebugValueEvalExpr(
    val displayName: String,
    val refExpr: String,
    val qualifiedType: String?,
) {
    companion object {
        const val TEMP_NAME_PREFIX = "__py_debug_temp_var_"
    }
}
