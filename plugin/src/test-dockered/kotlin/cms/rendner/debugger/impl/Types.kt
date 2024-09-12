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
package cms.rendner.debugger.impl

import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator

/**
 * A evaluation request.
 *
 * @param expression expression to evaluate
 * @param execute if true, the [expression] is executed, evaluated otherwise
 * @param trimResult if true, the result will be trimmed to a max length (useful for large lists, dictionaries or strings)
 */
data class EvalOrExecRequest(
    val expression: String,
    val execute: Boolean,
    val trimResult: Boolean,
)

/**
 * A evaluation response.
 *
 * @param value the evaluated result or the error in case of an error
 * @param type the Python type of the [value]
 * @param typeQualifier the [type]’s qualified name
 * @param isError true if the evaluation raised a Python error
 * @param refId a unique id to refer to the evaluated [value] on Python side
 */
data class EvalOrExecResponse(
    val value: String? = null,
    val type: String? = null,
    val typeQualifier: String? = null,
    val isError: Boolean = false,
    val refId: String? = null,
) {

    val qualifiedType: String?
        get() {
            return if (type == null) null
            else if (typeQualifier == null) type
            else "$typeQualifier.$type"
        }
}

interface IDebuggerInterceptor {
    fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest { return request }
    fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse { return response }
}

interface IPythonDebuggerApi {
    val evaluator: IPluginPyValueEvaluator
    fun continueFromBreakpoint()
    fun addInterceptor(interceptor: IDebuggerInterceptor)
    fun removeInterceptor(interceptor: IDebuggerInterceptor)
}