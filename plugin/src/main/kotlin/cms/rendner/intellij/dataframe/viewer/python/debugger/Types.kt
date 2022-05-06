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

data class PluginPyValue(
    val value: String?,
    val isErrorOnEval: Boolean,
    val type: String,
    val typeQualifier: String,
    val pythonRefEvalExpr: String,
    val evaluator: IPluginPyValueEvaluator
) {
    val forcedValue: String
        get() = value!!
}

interface IPluginPyValueEvaluator {
    @Throws(EvaluateException::class)
    fun evaluate(expression: String, trimResult: Boolean = false): PluginPyValue

    @Throws(EvaluateException::class)
    fun execute(statement: String)
}