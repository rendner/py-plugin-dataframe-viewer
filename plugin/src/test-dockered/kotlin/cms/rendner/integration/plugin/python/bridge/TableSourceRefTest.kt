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
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
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
    fun evaluateComputeChunkTableFrame_shouldBeCallable() {
        runWithTableSource { tableSource ->
            assertThat(
                tableSource.evaluateComputeChunkTableFrame(
                    ChunkRegion(0, 0, 2, 2),
                    excludeRowHeader = false,
                    SortCriteria(listOf(0), listOf(true)),
                )
            ).matches { table ->
                table.indexLabels!!.isNotEmpty() && table.cells.isNotEmpty()
            }
        }
    }

    @Test
    fun evaluateGetColumnNameCompletionVariants_shouldBeCallable() {
        runWithTableSource { tableSource ->
            tableSource.evaluateGetColumnNameCompletionVariants("df", false).let {
                assertThat(it).isEqualTo(
                    listOf(
                        CompletionVariant(fqType = PythonQualifiedTypes.STR, value = "col_0"),
                        CompletionVariant(fqType = PythonQualifiedTypes.STR, value = "col_1"),
                    )
                )
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
                tableSource.evaluateColumnStatistics(0)
            }

            // but original df is still accessible
            val result = debuggerApi.evaluator.evaluate("df")
            assertThat(result.qualifiedType).isEqualTo("pandas.core.frame.DataFrame")
        }
    }

    @Test
    fun shouldNotReferToDataFrameAfterDispose() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->
            val tableSource = createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df")

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
            val tableSource = createPandasTableSource<IPyTableSourceRef>(debuggerApi.evaluator, "df")

            val beforeUnlink = debuggerApi.findReferrerChains("df")
            assertThat(beforeUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            val methodCall = "${(tableSource as TestOnlyIPyTableSourceRefApi).testOnly_getRefExpr()}.unlink()"
            debuggerApi.evaluator.evaluate(methodCall)

            val afterUnlink = debuggerApi.findReferrerChains("df")
            assertThat(afterUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
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