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
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceTransformHint
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyTableSourceRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableSourceErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableSourceFactory
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.TableSourceException
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Order(2)
@RequiresPandas
internal class TableSourceFactoryTest : AbstractPluginCodeTest() {
    @Test
    fun shouldWorkWithPandasDataFrameAsDataSource() {
        runWithDefaultSnippet { debuggerApi ->
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "df")).isNotNull
        }
    }

    @Test
    fun shouldWorkWithPythonDictAsDataSource() {
        runWithDefaultSnippet { debuggerApi ->
            createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "d").let {
                assertThat(it).isNotNull
                assertThat(it.initialTableStructure.rowsCount).isEqualTo(3)
                assertThat(it.initialTableStructure.columnsCount).isEqualTo(2)
            }
        }
    }

    @Test
    fun shouldWorkWithPythonDictAsDataSourceWithDictKeysAsRows() {
        runWithDefaultSnippet { debuggerApi ->
            createPandasTableSource<IPyTableSourceRef>(
                debuggerApi.frameAccessor,
                "d",
                CreateTableSourceConfig(
                    dataSourceTransformHint = DataSourceTransformHint.DictKeysAsRows,
                ),
                ).let {
                assertThat(it).isNotNull
                assertThat(it.initialTableStructure.rowsCount).isEqualTo(2)
                assertThat(it.initialTableStructure.columnsCount).isEqualTo(3)
            }
        }
    }

    @Test
    fun shouldThrowIfDataSourceIsUnsupported() {
        runWithDefaultSnippet { debuggerApi ->

            val exception = assertThrows<TableSourceException> {
                createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "x")
            }

            assertThat(exception.data.errorKind).isEqualTo(TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE)
            assertThat(exception.data.info).isEqualTo("<class 'int'>")
            assertThat(exception.data.cause).isNull()
        }
    }

    @Test
    fun shouldStoreTableSourceAsTempVar() {
        runWithDefaultSnippet { debuggerApi ->

            val tempVarsDict = TableSourceFactory.getTempVarsDictRef()

            val sizeBeforeCreation = debuggerApi.frameAccessor.evaluate("len($tempVarsDict)").forcedValue.toInt()

            val tableSource = createPandasTableSource<IPyTableSourceRef>(
                debuggerApi.frameAccessor,
                "df",
            )

            val sizeAfterCreation = debuggerApi.frameAccessor.evaluate("len($tempVarsDict)").forcedValue.toInt()
            assertThat(sizeAfterCreation).isEqualTo(sizeBeforeCreation + 1)

            tableSource.disposeTableSource()
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
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor,"df1")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("2")
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor,"df2")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("3")
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "df3")).isNotNull

            debuggerApi.continueFromBreakpoint()

            assertThat(debuggerApi.frameAccessor.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(createPandasTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor,"df1")).isNotNull
        }
    }

    private fun runWithDefaultSnippet(block: (debuggerApi: IPythonDebuggerApi) -> Unit) {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet(), block)
    }

    private fun defaultCodeSnippet() = """
        from pandas import DataFrame
        
        x = 123

        d = {
            "col_0": [0, 1, 2],
            "col_1": [2, 1, 0],
        }
        
        df = DataFrame.from_dict(d)
        
        breakpoint()
    """.trimIndent()
}