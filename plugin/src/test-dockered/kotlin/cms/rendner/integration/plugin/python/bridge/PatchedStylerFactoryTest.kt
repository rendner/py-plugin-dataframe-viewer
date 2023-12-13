/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.integration.plugin.python.bridge

import cms.rendner.debugger.impl.IPythonDebuggerApi
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(2)
@RequiresPandas
internal class PatchedStylerFactoryTest : AbstractPluginCodeTest() {

    @Test
    fun shouldWorkWithPandasStylerAsDataSource() {
        runWithDefaultSnippet { debuggerApi ->
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator, "df.style")).isNotNull
        }
    }

    @Test
    fun shouldBeCallableWithAFilterFrame() {
        runWithDefaultSnippet { debuggerApi ->
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(
                debuggerApi.evaluator,
                "df.style",
                CreateTableSourceConfig(filterEvalExpr = "df.filter(items=[1, 2], axis='index')"),
            )).isNotNull
        }
    }

    @Test
    fun pluginCode_shouldBeAccessibleInEveryStackFrameWithoutReInjection() {
        createPythonDebuggerWithCodeSnippet("""
                from pandas import DataFrame
                
                df1 = DataFrame()
                x = 1
                breakpoint()
                
                def method_a():
                    df2 = DataFrame()
                    x = 2
                    breakpoint()
                
                def method_b():
                    method_a()
                    df3 = DataFrame()
                    x = 3
                    breakpoint()
                
                method_b()
                breakpoint()
            """.trimIndent()
        ) { debuggerApi ->

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator,"df1.style")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("2")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator,"df2.style")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("3")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator, "df3.style")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator,"df1.style")).isNotNull
        }
    }

    private fun runWithDefaultSnippet(block: (debuggerApi: IPythonDebuggerApi) -> Unit) {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet(), block)
    }

    private fun createDataFrameSnippet() = """
                from pandas import DataFrame
                
                d = {'a': [1]}
                df = DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
                
                breakpoint()
            """.trimIndent()
}