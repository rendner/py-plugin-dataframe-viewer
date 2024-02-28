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
package cms.rendner.intellij.dataframe.viewer.python.debugger

import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException

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

    val qualifiedType: String
        get() = "$typeQualifier.$type"
}

/**
 * Interface for evaluating or executing code on Python side.
 */
interface IPluginPyValueEvaluator {
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
     * Returns true if it is a console based evaluator.
     */
    fun isConsole(): Boolean {
        return false
    }
}