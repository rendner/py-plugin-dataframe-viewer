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
package cms.rendner.integration.plugin.services

import cms.rendner.debugger.impl.EvalOrExecRequest
import cms.rendner.debugger.impl.IDebuggerInterceptor
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.IEvalAvailableDataFrameLibraries
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@RequiresPandas
internal class EvalAvailableDataFrameLibrariesTest : AbstractPluginCodeTest() {
    @Test
    fun shouldFindExistingPandasWithoutPreviousImport() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            val evalLibraries = TestEvalLibrariesImpl()

            val result = debuggerApi.evaluator.evaluate(evalLibraries.getEvalExpression())
            val resultMap = evalLibraries.convertResult(result.value!!)

            assertThat(resultMap.contains(DataFrameLibrary.PANDAS)).isTrue()
        }
    }

    @Test
    fun shouldFindExistingPandasAfterImport() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            val evalLibraries = TestEvalLibrariesImpl()

            debuggerApi.evaluator.execute("import pandas")

            val result = debuggerApi.evaluator.evaluate(evalLibraries.getEvalExpression())
            val resultMap = evalLibraries.convertResult(result.value!!)

            assertThat(resultMap.contains(DataFrameLibrary.PANDAS)).isTrue()
        }
    }

    @Test
    fun shouldNotFindNonExistingModule() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val evalLibraries = TestEvalLibrariesImpl()
            val expr = evalLibraries.getEvalExpression()

            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest {
                    if (request.expression == expr) {
                        return request.copy(
                            expression = expr.replace(
                                DataFrameLibrary.PANDAS.moduleName,
                                "panda_s",
                            )
                        )
                    }
                    return super.onRequest(request)
                }
            })

            val result = debuggerApi.evaluator.evaluate(expr)
            val resultMap = evalLibraries.convertResult(result.value!!)

            assertThat(resultMap.contains(DataFrameLibrary.PANDAS)).isFalse()
        }
    }

    private class TestEvalLibrariesImpl : IEvalAvailableDataFrameLibraries
}