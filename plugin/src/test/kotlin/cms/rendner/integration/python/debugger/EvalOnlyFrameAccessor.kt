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
package cms.rendner.integration.python.debugger

import EvaluateRequest
import EvaluateResponse
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.python.debugger.*

class EvalOnlyFrameAccessor(private val pythonDebugger: PythonEvalDebugger) : PyFrameAccessor {

    private var counter = 0

    override fun evaluate(expression: String, execute: Boolean, doTrunc: Boolean): PyDebugValue {
        return try {
            createPyDebugValue(
                pythonDebugger.submit(EvaluateRequest(expression, execute, doTrunc)).get()
            ).also {
                if (it.isErrorOnEval && execute) {
                    throw PyDebuggerException(it.value)
                }
            }
        }catch (ex: Exception) {
            throw PyDebuggerException(ex.message, ex)
        }
    }

    override fun loadFrame(): XValueChildrenList = throwNotImplementedError()

    override fun loadVariable(p0: PyDebugValue?): XValueChildrenList = throwNotImplementedError()

    override fun changeVariable(p0: PyDebugValue?, p1: String?) = throwNotImplementedError()

    override fun getReferrersLoader(): PyReferrersLoader  = throwNotImplementedError()

    override fun getArrayItems(p0: PyDebugValue?, p1: Int, p2: Int, p3: Int, p4: Int, p5: String?): ArrayChunk = throwNotImplementedError()

    override fun getSourcePositionForName(p0: String?, p1: String?): XSourcePosition = throwNotImplementedError()

    override fun getSourcePositionForType(p0: String?): XSourcePosition = throwNotImplementedError()

    private fun throwNotImplementedError(): Nothing {
        throw NotImplementedError("Operation is not implemented.")
    }

    private fun createPyDebugValue(response: EvaluateResponse): PyDebugValue {
        return PyDebugValue(
            "debugValue_${counter++}",
            response.type,
            response.typeQualifier,
            response.value,
            false,
            null,
            false, // if true additional stuff has to be supported ("__pydevd_ret_val_dict")
            false,
            response.isError,
            this,
        ).also { it.tempName = response.refId }
    }
}