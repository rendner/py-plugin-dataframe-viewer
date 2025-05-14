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

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkDataRequest
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.TestOnlyIPyTableSourceRefApi
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
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
    fun evaluateValidateAndComputeChunkData_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateValidateAndComputeChunkData(
                    chunkRegion = ChunkRegion(0, 0, 2, 2),
                    ChunkDataRequest(withCells = true, withRowHeaders = true),
                    newSorting = null,
                )
            ).matches { result ->
                result.data.let { data ->
                    data.rowHeaders!!.isNotEmpty() && data.cells!!.isNotEmpty()
                } && result.problems.isNullOrEmpty()
            }
        }
    }

    @Test
    fun evaluateComputeChunkData_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.evaluateComputeChunkData(
                    chunkRegion = ChunkRegion(0, 0, 2, 2),
                    ChunkDataRequest(withCells = true, withRowHeaders = true),
                    newSorting = SortCriteria(listOf(0), listOf(true)),
                )
            ).matches { result ->
                result.rowHeaders!!.isNotEmpty() && result.cells!!.isNotEmpty()
            }
        }
    }

    @Test
    fun shouldNotReferToStylersDataFrameAfterDispose() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            val tableSource = createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator, "df.style.applymap(lambda x: 'color: red')")

            val beforeDispose = debuggerApi.findReferrerChains("df")
            assertThat(beforeDispose).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            tableSource.dispose()

            val afterDispose = debuggerApi.findReferrerChains("df")
            assertThat(afterDispose).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
        }
    }

    @Test
    fun shouldNotReferToDataFrameAfterInternalUnlink() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            val tableSource = createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.evaluator, "df.style.applymap(lambda x: 'color: red')")

            val beforeUnlink = debuggerApi.findReferrerChains("df")
            assertThat(beforeUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            val methodCall = "${(tableSource as TestOnlyIPyTableSourceRefApi).testOnly_getRefExpr()}.unlink()"
            debuggerApi.evaluator.evaluate(methodCall)

            val afterUnlink = debuggerApi.findReferrerChains("df")
            assertThat(afterUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
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