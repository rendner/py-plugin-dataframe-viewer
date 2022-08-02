/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
package cms.rendner.integration.plugin.bridge

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(3)
internal class PatchedStylerRefTest : AbstractPluginCodeTest() {

    @Test
    fun evaluateTableStructure_shouldBeCallable() {
        runWithPatchedStyler {
            it.evaluateTableStructure().let { ts ->
                assertThat(ts.orgRowsCount).isEqualTo(2)
                assertThat(ts.orgColumnsCount).isEqualTo(2)
                assertThat(ts.rowsCount).isEqualTo(2)
                assertThat(ts.columnsCount).isEqualTo(2)
                assertThat(ts.rowLevelsCount).isEqualTo(1)
                assertThat(ts.columnLevelsCount).isEqualTo(1)
                assertThat(ts.hideRowHeader).isEqualTo(false)
                assertThat(ts.hideColumnHeader).isEqualTo(false)
            }
        }
    }

    @Test
    fun evaluateStyleFunctionDetails_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(it.evaluateStyleFunctionDetails()).isEqualTo(
                listOf(
                    StyleFunctionDetails(
                        0,
                        "<lambda>",
                        "<lambda>",
                        "",
                        isPandasBuiltin = false,
                        isSupported = true,
                        isApply = false,
                        isChunkParentRequested = false
                    )
                )
            )
        }
    }

    @Test
    fun evaluateValidateStyleFunctions_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateValidateStyleFunctions(
                    ChunkRegion(0, 0, 2, 2),
                    ValidationStrategyType.DISABLED,
                )
            ).isEqualTo(
                emptyList<StyleFunctionValidationProblem>()
            )
        }
    }

    @Test
    fun evaluateComputeChunkHTMLPropsTable_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateComputeChunkHTMLPropsTable(
                    ChunkRegion(0, 0, 2, 2),
                    excludeRowHeader = false,
                    excludeColumnHeader = false,
                )
            ).matches { propsTable -> propsTable.head.isNotEmpty() && propsTable.body.isNotEmpty() }
        }
    }

    @Test
    fun evaluateComputeUnpatchedHTMLPropsTable_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateComputeUnpatchedHTMLPropsTable()
            ).matches { propsTable -> propsTable.head.isNotEmpty() && propsTable.body.isNotEmpty() }
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

    private fun runWithPatchedStyler(block: (patchedStyler: IPyPatchedStylerRef) -> Unit) {
        runWithPluginCodeBridge(createDataFrameSnippet()) { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator, _ ->
            val styler = evaluator.evaluate("df.style.applymap(lambda x: 'color: red')")
            block(codeBridge.createPatchedStyler(styler))
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