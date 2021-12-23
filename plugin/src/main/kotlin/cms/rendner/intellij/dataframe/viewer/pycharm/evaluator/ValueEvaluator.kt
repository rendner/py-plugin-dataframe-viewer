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
package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.PyFrameAccessor

class ValueEvaluator(private val frameAccessor: PyFrameAccessor): IValueEvaluator {

    @Throws(EvaluateException::class)
    override fun evaluate(expression: String, trimResult: Boolean): PyDebugValue {
        val result = try {
            frameAccessor.evaluate(expression, false, trimResult)
        } catch (ex: PyDebuggerException) {
            throw EvaluateException("Couldn't evaluate expression.", ex, expression)
        }
        if (result.isErrorOnEval) {
            throw EvaluateException(result.value ?: "Couldn't evaluate expression.", expression)
        }
        return result
    }

    @Throws(EvaluateException::class)
    override fun execute(statement: String): PyDebugValue {
        try {
            return frameAccessor.evaluate(statement, true, false)
        } catch (ex: PyDebuggerException) {
            throw EvaluateException("Couldn't execute statement.", ex, statement)
        }
    }
}