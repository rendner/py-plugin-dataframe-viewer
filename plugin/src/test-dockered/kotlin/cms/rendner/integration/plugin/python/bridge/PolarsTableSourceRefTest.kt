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
import cms.rendner.junit.RequiresPolars
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(3)
@RequiresPolars
internal class PolarsTableSourceRefTest : AbstractPluginCodeTest() {

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
            val tableSource = createPolarsTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df")

            tableSource.dispose()

            // expect instance is disposed and throws if trying to interact with
            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                tableSource.evaluateTableStructure()
            }

            // but original df is still accessible
            val result = debuggerApi.evaluator.evaluate("df")
            assertThat(result.qualifiedType).isEqualTo("polars.dataframe.frame.DataFrame")
        }
    }

    private fun runWithTableSource(block: (patchedStyler: IPyTableSourceRef) -> Unit) {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            block(createPolarsTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df"))
        }
    }

    private fun createDataFrameSnippet() = """
            |import polars as pl
            |
            |df = pl.from_dict({
            |    "col_0": [0, 1],
            |    "col_1": [2, 3],
            |})
            |
            |breakpoint()
        """.trimMargin()
}