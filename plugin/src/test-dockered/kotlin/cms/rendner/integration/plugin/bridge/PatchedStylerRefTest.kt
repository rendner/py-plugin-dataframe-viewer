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
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(2)
internal class PatchedStylerRefTest : AbstractPluginCodeTest() {

    @Test
    fun evaluateTableStructure_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(it.evaluateTableStructure()).isEqualTo(
                TableStructure(
                    rowsCount = 2,
                    columnsCount = 2,
                    rowLevelsCount = 1,
                    columnLevelsCount = 1,
                    hideRowHeader = false,
                    hideColumnHeader = false,
                )
            )
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
                    0,
                    0,
                    2,
                    2,
                    ValidationStrategyType.DISABLED,
                )
            ).isEqualTo(
                emptyList<StyleFunctionDetails>()
            )
        }
    }

    @Test
    fun evaluateRenderChunk_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateRenderChunk(
                    0,
                    0,
                    2,
                    2,
                    excludeRowHeader = false,
                    excludeColumnHeader = false,
                )
            ).contains("<table", "</table>")
        }
    }

    @Test
    fun evaluateRenderUnpatched_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateRenderUnpatched()
            ).contains("<table", "</table>")
        }
    }

    private fun runWithPatchedStyler(block: (patchedStyler: IPyPatchedStylerRef) -> Unit) {
        runWithPluginCodeBridge(createDataFrameSnippet()) { codeBridge: PythonCodeBridge, valueEvaluator: IPluginPyValueEvaluator ->
            val styler = valueEvaluator.evaluate("df.style.applymap(lambda x: 'color: red')")
            block(codeBridge.createPatchedStyler(styler))
        }
    }

    private fun createDataFrameSnippet() = """
                import pandas as pd
                
                df = pd.DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
            """.trimIndent()
}