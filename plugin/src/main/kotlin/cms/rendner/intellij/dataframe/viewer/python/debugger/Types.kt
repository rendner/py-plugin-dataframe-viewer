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
package cms.rendner.intellij.dataframe.viewer.python.debugger

import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.utils.parsePythonDictionary
import cms.rendner.intellij.dataframe.viewer.python.utils.parsePythonList
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyMethodCall
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyString

/**
 * Holds a reference to a Python object or a result evaluated on Python side.
 *
 * @property value the evaluation result as string
 * @property type the class type of the Python object
 * @property typeQualifier a dotted name build from the name of module in which the
 * objects class was defined combined with the qualified name of the objects class.
 * @property refExpr a Python expression to access the object for follow-up evaluations
 * @property evaluator an evaluator
 */
data class PluginPyValue(
    val value: String?,
    val type: String,
    val typeQualifier: String,
    val refExpr: String,
    val evaluator: IPluginPyValueEvaluator
) {
    /**
     * Asserts non-null and returns [value]
     */
    val forcedValue: String
        get() = value!!
}

/**
 * Interface for evaluating or executing code on Python side.
 */
interface IPluginPyValueEvaluator {
    /**
     * Returns an expression for accessing entries from the globals dictionary which contains the injected plugin code.
     */
    fun getFromPluginGlobalsExpr(name: String) = "$pluginGlobalsName.get(${stringifyString(name)})"

    /**
     * The name of the Python variable used as globals dictionary during plugin code injection.
     */
    val pluginGlobalsName: String
        // use a "dunder name" (name with double underscore) for the name, to be listed under "special var" in the debugger
        get() = "__styled_data_frame_plugin__"

    /**
     * Evaluates an expression.
     *
     * @param expression the expression to evaluate
     * @param trimResult if true, the result is shortened.
     * The length of the shortened results depends on the underlying implementation of the evaluator.
     * @throws [EvaluateException] in case the evaluation failed, regardless of whether the error is
     * caused by invalid Python syntax or by the underlying evaluator.
     */
    @Throws(EvaluateException::class)
    fun evaluate(expression: String, trimResult: Boolean = false): PluginPyValue

    /**
     * Executes statements.
     *
     * @param statements the statements to execute
     * @throws [EvaluateException] in case the exec failed, regardless of whether the error is
     * caused by invalid Python syntax or by the underlying evaluator.
     */
    @Throws(EvaluateException::class)
    fun execute(statements: String)

    /**
     * Stringifies a Python dict, by using the provided stringify methods, and returns a map of the extracted elements.
     *
     * @param dictRefExpr a Python expression referring to the dict to evaluate
     * @param stringifyValueFuncRef a Python function to stringify all dict values, default is "str"
     * @param stringifyValueFuncRef a Python function to stringify all dict keys, default is "str"
     * @return a map of the extracted elements. The surrounding quotes are already removed from the extracted
     * keys and values.
     */
    fun evaluateStringyfiedDict(
        dictRefExpr: String,
        stringifyValueFuncRef: String = "str",
        stringifyKeyFuncRef: String = "str",
    ): Map<String, String> {
        val delimiter = "_@@::@@_"
        return parsePythonDictionary(
            "{${
                evaluate(
                    stringifyMethodCall(stringifyString(delimiter), "join") {
                        refParam("[f'{$stringifyKeyFuncRef(k)}:{$stringifyValueFuncRef(v)}' for k, v in $dictRefExpr.items()]")
                    }
                ).forcedValue
            }}",
            delimiter,
        )
    }

    /**
     * Stringifies a Python list, by using the provided stringify methods, and returns a list of the extracted elements.
     *
     * @param listRefExpr a Python expression referring to the list to evaluate
     * @param stringifyValueFuncRef a Python function to stringify all list values, default is "str"
     * @return a list of the extracted elements. The surrounding quotes are already removed from the extracted values.
     */
    fun evaluateStringyfiedList(listRefExpr: String, stringifyValueFuncRef: String = "str"): List<String> {
        val delimiter = "_@@::@@_"
        return parsePythonList(
            "[${
                evaluate(
                    stringifyMethodCall(stringifyString(delimiter), "join") {
                        refParam("map($stringifyValueFuncRef, $listRefExpr)")
                    }
                ).forcedValue
            }]",
            delimiter,
        )
    }
}