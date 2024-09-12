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
package cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions

class EvaluateException(
    message: String,
    val pythonErrorQName: String?,
    override val cause: PluginPyDebuggerException? = null,
) : Exception(message, cause) {
    companion object {
        const val EXEC_FALLBACK_ERROR_MSG = "Statements could not be executed."
        const val EVAL_FALLBACK_ERROR_MSG = "Expression could not be evaluated."
    }

    fun isCausedByDisconnectException(): Boolean {
        return cause?.isDisconnectException() == true
    }

    fun isCausedByProcessIsRunningException(): Boolean {
        return cause?.isProcessIsRunningException() == true
    }
}