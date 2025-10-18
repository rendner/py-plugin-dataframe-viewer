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
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.TableSourceException
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.PolarsCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.junit.RequiresPolars
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests that all provided methods can be called on Python side.
 * The functionality of the methods is tested in the Python plugin-code projects.
 */
@Order(3)
@RequiresPolars
internal class PolarsTableSourceRefTest : AbstractPluginCodeTest() {

    @Test
    fun codeProviderSupportsDataFrame() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val result = debuggerApi.frameAccessor.evaluate("df")
            assertThat(PolarsCodeProvider().isApplicable(result.qualifiedType)).isTrue
        }
    }

    @Test
    fun computeChunkData_shouldBeCallable() {
        runWithTableSource { tableSource ->
            assertThat(
                tableSource.computeChunkData(
                    chunkRegion = ChunkRegion(0, 0, 2, 2),
                    ChunkDataRequest(withCells = true, withRowHeaders = true),
                )
            ).matches { result ->
                result == ChunkData(
                    rowHeaders = null,
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
        runWithTableSource { tableSource ->
            val result = tableSource.getColumnStatistics(0)
            assertThat(result["count"]).isEqualTo("2.0")
        }
    }

    @Test
    fun getColumnStatistics_returnsResultForFilteredData() {
        runWithTableSource { tableSource ->
            val resultForUnfilteredData = tableSource.getColumnStatistics(0)
            assertThat(resultForUnfilteredData["count"]).isEqualTo("2.0")

            tableSource.applyFilter(FilterConfig(expr = "df.filter(pl.col('col_0') == 0)"))
            val resultForFilteredData = tableSource.getColumnStatistics(0)

            assertThat(resultForFilteredData["count"]).isEqualTo("1.0")
        }
    }

    @Test
    fun getColumnNameCompletionVariants_shouldBeCallable() {
        runWithTableSource { tableSource ->
            tableSource.getColumnNameCompletionVariants("df", false).let {
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
    fun applySort_shouldBeCallable() {
        runWithTableSource { tableSource ->
            tableSource.applySort(SortConfig())
        }
    }

    @Test
    fun applyFilter_shouldBeCallable() {
        runWithTableSource { tableSource ->
            assertThat(
                tableSource.applyFilter(FilterConfig()),
            ).matches { result ->
                result == FilterResult(
                    sort = SortConfig(),
                    tableStructure = tableSource.initialTableStructure,
                )
            }
        }
    }

    @Test
    fun shouldFilterDataForSyntheticIdentifier() {
        runWithTableSource { tableSource ->

            val filterResult = tableSource.applyFilter(FilterConfig(
                expr = "_df.filter(pl.col('col_0').is_between(1, 3))",
                syntheticIdentifierUsed = true,
            ))

            assertThat(filterResult).isNotNull
            assertThat(filterResult.tableStructure.rowsCount).isLessThan(tableSource.initialTableStructure.rowsCount)
        }
    }

    @Test
    fun shouldThrowIfFilterEvaluationFails() {
        runWithTableSource { tableSource ->

            val exception = assertThrows<TableSourceException> {
                tableSource.applyFilter(FilterConfig(expr = "xyz"))
            }

            assertThat(exception.data.errorKind).isEqualTo(TableSourceErrorKind.EVAL_FAILED)
            assertThat(exception.data.info).isEqualTo("")
            assertThat(exception.data.cause).isEqualTo("NameError(\"name 'xyz' is not defined\")")
        }
    }

    @Test
    fun shouldThrowIfFilterEvaluatesToWrongType() {
        runWithTableSource { tableSource ->
            val exception = assertThrows<TableSourceException> {
                tableSource.applyFilter(FilterConfig(expr = "123"))
            }

            assertThat(exception.data.errorKind).isEqualTo(TableSourceErrorKind.UNSUPPORTED_FILTER_FRAME_TYPE)
            assertThat(exception.data.info).isEqualTo("<class 'int'>")
            assertThat(exception.data.cause).isNull()
        }
    }

    @Test
    fun shouldCleanupOnDisposeTableSource() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val tableSource = createPolarsTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "df")

            tableSource.disposeTableSource()

            // expect instance is disposed and throws if trying to interact with
            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                tableSource.getColumnStatistics(0)
            }

            // but original df is still accessible
            val result = debuggerApi.frameAccessor.evaluate("df")
            assertThat(result.qualifiedType).isEqualTo("polars.dataframe.frame.DataFrame")
        }
    }

    @Test
    fun shouldNotReferToDataFrameAfterDisposeTableSource() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val tableSource = createPolarsTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "df")

            val beforeDispose = debuggerApi.findReferrerChains("df")
            assertThat(beforeDispose).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            tableSource.disposeTableSource()

            val afterDispose = debuggerApi.findReferrerChains("df")
            assertThat(afterDispose).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
        }
    }

    @Test
    fun shouldNotReferToDataFrameAfterInternalUnlink() {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            val tableSource = createPolarsTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "df")

            val beforeUnlink = debuggerApi.findReferrerChains("df")
            assertThat(beforeUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isNotEmpty

            val methodCall = "${(tableSource as TestOnlyIPyTableSourceRefApi).testOnly_getRefExpr()}.unlink()"
            debuggerApi.frameAccessor.evaluate(methodCall)

            val afterUnlink = debuggerApi.findReferrerChains("df")
            assertThat(afterUnlink).filteredOn { it.contains("cms_rendner_sdfv") }.isEmpty()
        }
    }

    private fun runWithTableSource(block: (patchedStyler: IPyTableSourceRef) -> Unit) {
        createPythonDebuggerWithCodeSnippet(defaultCodeSnippet()) { debuggerApi ->
            block(createPolarsTableSource<IPyTableSourceRef>(debuggerApi.frameAccessor, "df"))
        }
    }

    private fun defaultCodeSnippet() = """
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