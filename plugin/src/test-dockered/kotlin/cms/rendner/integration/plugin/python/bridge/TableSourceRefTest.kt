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
package cms.rendner.integration.plugin.python.bridge

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(3)
@RequiresPandas
internal class TableSourceRefTest : AbstractPluginCodeTest() {

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
    fun evaluateTableStructure_shouldBeCallable() {
        runWithTableSource { tableSource ->
            tableSource.evaluateTableStructure().let {
                assertThat(it.orgRowsCount).isEqualTo(2)
                assertThat(it.orgColumnsCount).isEqualTo(2)
                assertThat(it.rowsCount).isEqualTo(2)
                assertThat(it.columnsCount).isEqualTo(2)
            }
        }
    }

    @Test
    fun evaluateComputeChunkTableFrame_shouldBeCallable() {
        runWithTableSource { tableSource ->
            assertThat(
                tableSource.evaluateComputeChunkTableFrame(
                    ChunkRegion(0, 0, 2, 2),
                    excludeRowHeader = false,
                    excludeColumnHeader = false,
                )
            ).matches { table ->
                (table.indexLabels == null || table.indexLabels!!.isNotEmpty())
                    && table.columns.isNotEmpty()
                    && table.cells.isNotEmpty()
            }
        }
    }

    @Test
    fun evaluateSetSortCriteria_shouldBeCallable() {
        runWithTableSource { tableSource ->
            assertThatNoException().isThrownBy {
                tableSource.evaluateSetSortCriteria(SortCriteria(listOf(0), listOf(true)))
            }
        }
    }

    @Test
    fun evaluateGetOrgIndicesOfVisibleColumns_shouldBeCallable() {
        runWithTableSource { tableSource ->
            assertThatNoException().isThrownBy {
                tableSource.evaluateGetOrgIndicesOfVisibleColumns(0, 999)
            }
        }
    }

    @Test
    fun evaluateGetColumnNameVariants_shouldBeCallable() {
        runWithTableSource { tableSource ->
            tableSource.evaluateGetColumnNameVariants("df", false, "''").let {
                assertThat(it).isEqualTo(listOf("\"col_0\"", "\"col_1\""))
            }
        }
    }

    @Test
    fun shouldCleanupOnDispose() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            val tableSource = createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df")

            tableSource.dispose()

            // expect instance is disposed and throws if trying to interact with
            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                tableSource.evaluateTableStructure()
            }

            // but original df is still accessible
            val result = debuggerApi.evaluator.evaluate("df")
            assertThat(result.qualifiedType).isEqualTo("pandas.core.frame.DataFrame")
        }
    }

    private fun runWithTableSource(block: (patchedStyler: IPyTableSourceRef) -> Unit) {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            block(createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df"))
        }
    }

    private fun createDataFrameSnippet() = """
                import pandas as pd
                
                df = pd.DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
                
                breakpoint()
            """.trimIndent()
}