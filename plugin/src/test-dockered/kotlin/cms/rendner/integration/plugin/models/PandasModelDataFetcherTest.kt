/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.integration.plugin.models

import cms.rendner.intellij.dataframe.viewer.components.filter.editor.FilterInputState
import cms.rendner.intellij.dataframe.viewer.models.chunked.ModelDataFetcher
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.PandasCodeProvider
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@RequiresPandas
internal class PandasModelDataFetcherTest : AbstractModelDataFetcherTest(PandasCodeProvider()) {

    @Test
    fun shouldFetchRequiredDataForStyler() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df.style")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSourceInfo, null, false)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.tableSourceRef).isNotNull
                assertThat(it.tableStructure).isNotNull
                assertThat(it.columnIndexTranslator).isNotNull
                assertThat(it.dataSourceCurrentStackFrameRefExpr).isEqualTo(dataSourceInfo.source.currentStackFrameRefExpr)
            }
        }
    }

    @Test
    fun shouldFilterDataForSyntheticIdentifier() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState("_df.filter(items=[1], axis='index')", true),
                    false,
                )
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.tableStructure.rowsCount).isLessThan(it.tableStructure.orgRowsCount)
            }
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState("xyz"),
                    false,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.FILTER_FRAME_EVAL_FAILED)
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluatesToWrongType() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState("123"),
                    false,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.FILTER_FRAME_OF_WRONG_TYPE)
        }
    }

    @Test
    fun shouldHaveSameFingerprint() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo1 = createDataSourceInfo(debuggerApi, "df")
            val fetcher1 = MyTestFetcher(debuggerApi.evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo1,
                    null,
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSourceInfo2 = createDataSourceInfo(debuggerApi, "df.style.data")
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo2,
                    null,
                    false,
                )
            )
            assertThat(fetcher2.result).isNotNull

            assertThat(
                fetcher1.result!!.tableStructure.fingerprint
            ).isEqualTo(
                fetcher2.result!!.tableStructure.fingerprint
            )
        }
    }

    @Test
    fun shouldHaveDifferentFingerprint() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo1 = createDataSourceInfo(debuggerApi, "df")
            val fetcher1 = MyTestFetcher(debuggerApi.evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo1,
                    null,
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSourceInfo2 = createDataSourceInfo(debuggerApi, "df.copy()")
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo2,
                    null,
                    false,
                )
            )
            assertThat(fetcher2.result).isNotNull

            assertThat(
                fetcher1.result!!.tableStructure.fingerprint
            ).isNotEqualTo(
                fetcher2.result!!.tableStructure.fingerprint
            )
        }
    }

    override fun createDataFrameSnippet() = """
    |from pandas import DataFrame
    |
    |df = DataFrame.from_dict({
    |    "col_0": [0, 1],
    |    "col_1": [2, 3],
    |})
    |
    |breakpoint()
""".trimMargin()
}