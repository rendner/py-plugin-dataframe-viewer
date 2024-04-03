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
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(3)
@RequiresPandas
internal class PatchedStylerRefTest : AbstractPluginCodeTest() {

    @Test
    fun evaluateTableStructure_shouldBeCallable() {
        runWithPatchedStyler {
            it.evaluateTableStructure().let { ts ->
                assertThat(ts.orgRowsCount).isEqualTo(2)
                assertThat(ts.orgColumnsCount).isEqualTo(2)
                assertThat(ts.rowsCount).isEqualTo(2)
                assertThat(ts.columnsCount).isEqualTo(2)
            }
        }
    }

    @Test
    fun evaluateValidateAndComputeChunkTableFrame_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateValidateAndComputeChunkTableFrame(
                    ChunkRegion(0, 0, 2, 2),
                    excludeRowHeader = false,
                    excludeColumnHeader = false,
                )
            ).matches { validatedTable ->
                validatedTable.frame.let { table ->
                    table.indexLabels!!.isNotEmpty()
                            && table.columns.isNotEmpty()
                            && table.cells.isNotEmpty()
                } && validatedTable.problems.isEmpty()
            }
        }
    }

    @Test
    fun evaluateComputeChunkTableFrame_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateComputeChunkTableFrame(
                    ChunkRegion(0, 0, 2, 2),
                    excludeRowHeader = false,
                    excludeColumnHeader = false,
                )
            ).matches { table ->
                table.indexLabels!!.isNotEmpty()
                    && table.columns.isNotEmpty()
                    && table.cells.isNotEmpty()
            }
        }
    }

    @Test
    fun evaluateSetSortCriteria_shouldBeCallable() {
        runWithPatchedStyler {
            assertThatNoException().isThrownBy {
                it.evaluateSetSortCriteria(SortCriteria(listOf(0), listOf(true)))
            }
        }
    }

    @Test
    fun evaluateGetOrgIndicesOfVisibleColumns_shouldBeCallable() {
        runWithPatchedStyler {
            assertThatNoException().isThrownBy {
                it.evaluateGetOrgIndicesOfVisibleColumns(0, 999)
            }
        }
    }

    private fun runWithPatchedStyler(block: (patchedStyler: IPyPatchedStylerRef) -> Unit) {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            block(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator, "df.style.applymap(lambda x: 'color: red')"))
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