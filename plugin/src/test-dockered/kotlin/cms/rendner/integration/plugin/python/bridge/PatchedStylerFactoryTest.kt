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
package cms.rendner.integration.plugin.python.bridge

import cms.rendner.debugger.impl.IPythonDebuggerApi
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableSourceFactory
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
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor, "df_style")).isNotNull
        }
    }

    @Test
    fun shouldStorePatchedStylerAsTempVar() {
        runWithDefaultSnippet { debuggerApi ->

            val tempVarsDict = TableSourceFactory.getTempVarsDictRef()

            val sizeBeforeCreation = debuggerApi.frameAccessor.evaluate("len($tempVarsDict)").forcedValue.toInt()

            val patchedStyler = createPandasTableSource<IPyPatchedStylerRef>(
                debuggerApi.frameAccessor,
                "df_style",
            )

            val sizeAfterCreation = debuggerApi.frameAccessor.evaluate("len($tempVarsDict)").forcedValue.toInt()
            assertThat(sizeAfterCreation).isEqualTo(sizeBeforeCreation + 1)

            patchedStyler.disposeTableSource()
            val sizeAfterDispose = debuggerApi.frameAccessor.evaluate("len($tempVarsDict)").forcedValue.toInt()

            assertThat(sizeAfterDispose).isEqualTo(sizeBeforeCreation)
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

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor,"df1.style")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("2")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor,"df2.style")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("3")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor, "df3.style")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor,"df1.style")).isNotNull
        }
    }

    private fun runWithDefaultSnippet(block: (debuggerApi: IPythonDebuggerApi) -> Unit) {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet(), block)
    }

    private fun defaultCodeSnippet() = """
        from pandas import DataFrame

        df = DataFrame.from_dict({
            "col_0": [0, 1],
            "col_1": [2, 3],
        })
        
        df_style = df.style
        
        breakpoint()
    """.trimIndent()
}