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
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyTableSourceRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableSourceFactory
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(2)
@RequiresPandas
internal class TableSourceFactoryTest : AbstractPluginCodeTest() {

    override fun afterContainerStart() {
        /*
        The TableSource doesn't use jinja2.
        To proof that a normal TableSource also works without jinja2, the module has to be removed.

        Normally this test should be done in the Python project. But this would require a Python project
        without jinja2 for the TableSource code and one with jinja for the PatchedStyler.
        Having two separate Python projects for each supported version of pandas doesn't scale well.
         */

        // long-running process
        uninstallPythonModules("jinja2")
    }

    @Test
    fun shouldFailIfJinja2IsNotInstalled() {
        runWithDefaultSnippet { debuggerApi ->
            Assertions.assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator, "df.style")
            }.withMessageContaining("jinja2")
        }
    }

    @Test
    fun shouldWorkWithPandasDataFrameAsDataSource() {
        runWithDefaultSnippet { debuggerApi ->
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df")).isNotNull
        }
    }

    @Test
    fun shouldWorkWithPythonDictAsDataSource() {
        runWithDefaultSnippet { debuggerApi ->
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "d")).isNotNull
        }
    }

    @Test
    fun shouldBeCallableWithAFilterFrame() {
        runWithDefaultSnippet { debuggerApi ->
            assertThat(createPandasTableSource<IPyTableSourceRef>(
                debuggerApi.evaluator,
                "df",
                CreateTableSourceConfig(filterEvalExpr = "df.filter(items=[1, 2], axis='index')"),
            )).isNotNull
        }
    }

    @Test
    fun shouldStoreTableSourceAsTempVar() {
        runWithDefaultSnippet { debuggerApi ->

            val tableSource = createPandasTableSource<IPyTableSourceRef>(
                debuggerApi.evaluator,
                "df",
                CreateTableSourceConfig(tempVarSlotId = "abc"),
            )

            assertThat(tableSource).isNotNull
            // methods should be callable
            assertThat(tableSource.evaluateColumnStatistics(0)).isNotNull

            val tempVarsDict = TableSourceFactory.getTempVarsDictRef()
            assertThat(debuggerApi.evaluator.evaluate("len($tempVarsDict)").forcedValue).isEqualTo("1")
            assertThat(debuggerApi.evaluator.evaluate("$tempVarsDict['abc']").qualifiedType)
                .isEqualTo("cms_rendner_sdfv.pandas.frame.table_source.TableSource")

            tableSource.dispose()
            // after dispose the dict should not contain the tableSource anymore
            assertThat(debuggerApi.evaluator.evaluate("len($tempVarsDict)").forcedValue).isEqualTo("0")
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
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator,"df1")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("2")
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator,"df2")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("3")
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df3")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.evaluator.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator,"df1")).isNotNull
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