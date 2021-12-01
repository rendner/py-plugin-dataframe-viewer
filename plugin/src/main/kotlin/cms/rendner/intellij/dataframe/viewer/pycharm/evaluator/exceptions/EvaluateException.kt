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
package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions

import com.jetbrains.python.debugger.PyDebuggerException
import java.lang.StringBuilder

class EvaluateException : Exception {

    private val expression: String

    constructor(message: String, cause: PyDebuggerException, expression: String) : super(message, cause) {
        this.expression = expression
    }

    override val cause: PyDebuggerException?
        get() = super.cause as PyDebuggerException?

    constructor(message: String, expression: String) : super(message) {
        this.expression = expression
    }

    override fun getLocalizedMessage(): String {
        val msg = super.getLocalizedMessage()
        return cause?.let {
            return "$msg ${it.localizedMessage}"
        } ?: msg
    }

    fun userFriendlyMessage(): String {
        return this.message ?: "Couldn't evaluate expression."
    }

    fun logMessage() = StringBuilder()
        .appendLine(message)
        .appendLine("\texpression: '${expression}'")
        .toString()
}