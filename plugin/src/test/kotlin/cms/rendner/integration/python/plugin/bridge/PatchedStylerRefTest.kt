/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
package cms.rendner.integration.python.plugin.bridge

import cms.rendner.integration.python.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.TableStructure
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.pycharm.bridge.PyPatchedStylerRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(2)
internal class PatchedStylerRefTest : AbstractPluginCodeTest() {

    @Test
    fun shouldFetchTableStructure() {
        runWithPatchedStyler {
            assertThat(it.evaluateTableStructure()).isEqualTo(
                TableStructure(
                    rowsCount = 2,
                    columnsCount = 2,
                    visibleRowsCount = 2,
                    visibleColumnsCount = 2,
                    rowLevelsCount = 1,
                    columnLevelsCount = 1,
                    hideRowHeader = false,
                    hideColumnHeader = false,
                )
            )
        }
    }

    @Test
    fun shouldFetchChunk() {
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
    fun shouldFetchUnpatched() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateRenderUnpatched()
            ).contains("<table", "</table>")
        }
    }

    private fun runWithPatchedStyler(block: (patchedStyler: PyPatchedStylerRef) -> Unit) {
        runWithInjectedPluginCode(createDataFrameSnippet()) { codeBridge: PythonCodeBridge, evaluator: IValueEvaluator ->
            val styler = evaluator.evaluate("df.style")
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