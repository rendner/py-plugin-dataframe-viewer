/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.components.table.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.Cell
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkData
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkDataRequest
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.python.bridge.CompletionVariant
import cms.rendner.intellij.dataframe.viewer.python.bridge.FilterConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.FilterResult
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.ProblemReason
import cms.rendner.intellij.dataframe.viewer.python.bridge.SortConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.StyleFunctionInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableSourceErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.TestOnlyIPyTableSourceRefApi
import cms.rendner.intellij.dataframe.viewer.python.bridge.ValidatedChunkData
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.TableSourceException
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.PandasCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(3)
@RequiresPandas
internal class PatchedStylerRefTest : AbstractPluginCodeTest() {

    @Test
    fun codeProviderSupportsStyler() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val result = debuggerApi.frameAccessor.evaluate("df")
            assertThat(PandasCodeProvider().isApplicable(result.qualifiedType)).isTrue
        }
    }

    @Test
    fun validateAndComputeChunkData_shouldBeCallable() {
        runWithPatchedStyler("df_invalid_style") {
            assertThat(
                it.validateAndComputeChunkData(
                    chunkRegion = ChunkRegion(0, 0, 2, 2),
                    ChunkDataRequest(withCells = true, withRowHeaders = true),
                )
            ).matches { result ->
                result == ValidatedChunkData(
                    data = ChunkData(
                        rowHeaders = listOf(
                            HeaderLabel(label = "0"),
                            HeaderLabel(label = "1"),
                        ),
                        cells = listOf(
                            listOf(
                                Cell(value = "0", meta = "FTF0||||"),
                                Cell(value = "2", meta = "FTF0||||"),
                            ),
                            listOf(
                                Cell(value = "1", meta = "FFT100000||red||"),
                                Cell(value = "3", meta = "FFT100000||red||"),
                            ),
                        ),
                    ),
                    problems = listOf(
                        StyleFunctionValidationProblem(
                            reason = ProblemReason.NOT_EQUAL,
                            message = "",
                            funcInfo = StyleFunctionInfo(
                                index = 0,
                                qname = "my_highlight_max",
                                resolvedName = "my_highlight_max",
                                axis = "0",
                                isPandasBuiltin = false,
                                isSupported = false,
                                isApply = true,
                                isChunkParentRequested = false
                            )
                        )
                    ),
                )
            }
        }
    }

    @Test
    fun computeChunkData_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.computeChunkData(
                    chunkRegion = ChunkRegion(0, 0, 2, 2),
                    ChunkDataRequest(withCells = true, withRowHeaders = true),
                )
            ).matches { result ->
                result == ChunkData(
                    rowHeaders = listOf(
                        HeaderLabel(label = "0"),
                        HeaderLabel(label = "1"),
                    ),
                    cells = listOf(
                        listOf(
                            Cell(value = "0", meta = "FTF0||||"),
                            Cell(value = "2", meta = "FTF0||||"),
                        ),
                        listOf(
                            Cell(value = "1", meta = "FFT100000||||"),
                            Cell(value = "3", meta = "FFT100000||||"),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun getColumnStatistics_shouldBeCallable() {
        runWithPatchedStyler {
            val result = it.getColumnStatistics(0)
            assertThat(result["count"]).isEqualTo("2.0")
        }
    }

    @Test
    fun getColumnStatistics_returnsResultForFilteredData() {
        runWithPatchedStyler { patchedStyler ->
            val resultForUnfilteredData = patchedStyler.getColumnStatistics(0)
            assertThat(resultForUnfilteredData["count"]).isEqualTo("2.0")

            patchedStyler.applyFilter(FilterConfig(expr = "df[df['col_0'] == 0]"))
            val resultForFilteredData = patchedStyler.getColumnStatistics(0)

            assertThat(resultForFilteredData["count"]).isEqualTo("1.0")
        }
    }

    @Test
    fun getColumnNameCompletionVariants_shouldBeCallable() {
        runWithPatchedStyler { patchedStyler ->
            assertThat(
                patchedStyler.getColumnNameCompletionVariants("df", false).let {
                    assertThat(it).isEqualTo(
                        listOf(
                            CompletionVariant(fqType = PythonQualifiedTypes.STR, value = "col_0"),
                            CompletionVariant(fqType = PythonQualifiedTypes.STR, value = "col_1"),
                        )
                    )
                }
            )
            assertThat(
                patchedStyler.getColumnNameCompletionVariants("df_style", false).let {
                    assertThat(it).isEmpty()
                }
            )
        }
    }

    @Test
    fun applySort_shouldBeCallable() {
        runWithPatchedStyler {
            it.applySort(SortConfig())
        }
    }

    @Test
    fun applyFilter_shouldBeCallable() {
        runWithPatchedStyler {
            assertThat(
                it.applyFilter(FilterConfig()),
            ).matches { result ->
                result == FilterResult(
                    sort = SortConfig(),
                    tableStructure = it.initialTableStructure,
                )
            }
        }
    }

    @Test
    fun shouldFilterDataForSyntheticIdentifier() {
        runWithPatchedStyler { patchedStyler ->

            val filterResult = patchedStyler.applyFilter(FilterConfig(
                expr = "_df.filter(items=[1], axis='index')",
                syntheticIdentifierUsed = true,
            ))

            assertThat(filterResult).isNotNull
            assertThat(filterResult.tableStructure.rowsCount).isLessThan(patchedStyler.initialTableStructure.rowsCount)
        }
    }

    @Test
    fun shouldThrowIfFilterEvaluationFails() {
        runWithPatchedStyler { patchedStyler ->

            val exception = assertThrows<TableSourceException> {
                patchedStyler.applyFilter(FilterConfig(expr = "xyz"))
            }

            assertThat(exception.data.errorKind).isEqualTo(TableSourceErrorKind.EVAL_FAILED)
            assertThat(exception.data.info).isEqualTo("")
            assertThat(exception.data.cause).isEqualTo("NameError(\"name 'xyz' is not defined\")")
        }
    }

    @Test
    fun shouldThrowIfFilterEvaluatesToWrongType() {
        runWithPatchedStyler { patchedStyler ->
            val exception = assertThrows<TableSourceException> {
                patchedStyler.applyFilter(FilterConfig(expr = "123"))
            }

            assertThat(exception.data.errorKind).isEqualTo(TableSourceErrorKind.UNSUPPORTED_FILTER_FRAME_TYPE)
            assertThat(exception.data.info).isEqualTo("<class 'int'>")
            assertThat(exception.data.cause).isNull()
        }
    }

    @Test
    fun shouldCleanupOnDisposeTableSource() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val patchedStyler = createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor, "df_style")

            patchedStyler.disposeTableSource()

            // expect instance is disposed and throws if trying to interact with
            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                patchedStyler.getColumnStatistics(0)
            }

            // but original is still accessible
            val stylerResult = debuggerApi.frameAccessor.evaluate("df_style")
            assertThat(PandasTypes.isStyler(stylerResult.qualifiedType)).isTrue
            val dfResult = debuggerApi.frameAccessor.evaluate("df")
            assertThat(PandasTypes.isDataFrame(dfResult.qualifiedType)).isTrue
        }
    }

    @Test
    fun shouldNotReferToStylersDataFrameAfterDispose() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val tableSource = createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor, "df_style")

            val beforeDispose = debuggerApi.findReferrerChains("df")
            assertThat(beforeDispose).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            tableSource.disposeTableSource()

            val afterDispose = debuggerApi.findReferrerChains("df")
            assertThat(afterDispose).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
        }
    }

    @Test
    fun shouldNotReferToStylersDataFrameAfterInternalUnlink() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val tableSource = createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor, "df_style")

            val beforeUnlink = debuggerApi.findReferrerChains("df")
            assertThat(beforeUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            val methodCall = "${(tableSource as TestOnlyIPyTableSourceRefApi).testOnly_getRefExpr()}.unlink()"
            debuggerApi.frameAccessor.evaluate(methodCall)

            val afterUnlink = debuggerApi.findReferrerChains("df")
            assertThat(afterUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
        }
    }

    private fun runWithPatchedStyler(dataSourceRefExpr: String = "df_style", block: (patchedStyler: IPyPatchedStylerRef) -> Unit) {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            block(createPandasTableSource<IPyPatchedStylerRef>(debuggerApi.frameAccessor, dataSourceRefExpr))
        }
    }

    private fun defaultCodeSnippet() = """
        import pandas as pd
        
        df = pd.DataFrame.from_dict({
            "col_0": [0, 1],
            "col_1": [2, 3],
        })
        
        df_style = df.style
        
        def my_highlight_max(series):
            is_max = series == series.max()
            return ['background-color: red' if cell else '' for cell in is_max]
        
        
        df_invalid_style = df.style.apply(my_highlight_max)
        
        breakpoint()
    """.trimIndent()
}
