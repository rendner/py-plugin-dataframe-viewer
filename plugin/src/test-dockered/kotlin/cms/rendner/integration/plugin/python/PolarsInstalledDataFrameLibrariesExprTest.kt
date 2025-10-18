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
package cms.rendner.integration.plugin.python

import cms.rendner.debugger.impl.EvalOrExecRequest
import cms.rendner.debugger.impl.IDebuggerInterceptor
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.InstalledDataFrameLibrariesExpr
import cms.rendner.junit.RequiresPolars
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

@RequiresPolars
internal class PolarsInstalledDataFrameLibrariesExprTest : AbstractPluginCodeTest() {
    @Test
    fun shouldFindExistingPolarsWithoutPreviousImport() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            val result = debuggerApi.frameAccessor.evaluate(InstalledDataFrameLibrariesExpr.getExpression())
            val resultMap = InstalledDataFrameLibrariesExpr.convertResult(result.value!!)

            Assertions.assertThat(resultMap.contains(DataFrameLibrary.POLARS)).isTrue()
        }
    }

    @Test
    fun shouldFindExistingPolarsAfterImport() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            debuggerApi.frameAccessor.execute("import polars")

            val result = debuggerApi.frameAccessor.evaluate(InstalledDataFrameLibrariesExpr.getExpression())
            val resultMap = InstalledDataFrameLibrariesExpr.convertResult(result.value!!)

            Assertions.assertThat(resultMap.contains(DataFrameLibrary.POLARS)).isTrue()
        }
    }

    @Test
    fun shouldNotFindNonExistingModule() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val expr = InstalledDataFrameLibrariesExpr.getExpression()

            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest {
                    if (request.expression == expr) {
                        return request.copy(
                            expression = expr.replace(
                                DataFrameLibrary.POLARS.moduleName,
                                "polar_s",
                            )
                        )
                    }
                    return super.onRequest(request)
                }
            })

            val result = debuggerApi.frameAccessor.evaluate(expr)
            val resultMap = InstalledDataFrameLibrariesExpr.convertResult(result.value!!)

            Assertions.assertThat(resultMap.contains(DataFrameLibrary.POLARS)).isFalse()
        }
    }
}