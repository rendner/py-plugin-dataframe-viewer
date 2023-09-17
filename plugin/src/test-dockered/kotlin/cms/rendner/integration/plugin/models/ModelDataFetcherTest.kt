/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreatePatchedStylerErrorKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ModelDataFetcherTest: AbstractModelDataFetcherTest() {

    @Test
    fun shouldFetchRequiredDataForDataFrame() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), FilterInputState(""), false)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.patchedStyler).isNotNull
                assertThat(it.tableStructure).isNotNull
                assertThat(it.frameColumnIndexList).isNotNull
                assertThat(it.updatedDataSourceExpr).isEqualTo(dataSource.toValueEvalExpr())
            }
        }
    }

    @Test
    fun shouldFetchRequiredDataForStyler() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df.style")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), FilterInputState(""), false)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.patchedStyler).isNotNull
                assertThat(it.tableStructure).isNotNull
                assertThat(it.frameColumnIndexList).isNotNull
                assertThat(it.updatedDataSourceExpr).isEqualTo(dataSource.toValueEvalExpr())
            }
        }
    }

    @Test
    fun shouldReEvaluateIfRequested() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            // use identifier
            val dataSource = debuggerApi.evaluator.evaluate("df")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), FilterInputState(""), true)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.updatedDataSourceExpr).isEqualTo(dataSource.toValueEvalExpr())
            }

            // use a new created instance
            val dataSource2 = debuggerApi.evaluator.evaluate("DataFrame()")
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(dataSource2.toValueEvalExpr(), FilterInputState(""), true)
            )

            assertThat(fetcher2.result).isNotNull
            fetcher2.result!!.let {
                assertThat(it.updatedDataSourceExpr).isNotEqualTo(dataSource.toValueEvalExpr())
            }
        }
    }

    @Test
    fun shouldFilterDataForDataFrame() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState("${dataSource.refExpr}[['col_0']]"),
                    false,
                )
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.tableStructure.columnsCount).isLessThan(it.tableStructure.orgColumnsCount)
            }
        }
    }

    @Test
    fun shouldFilterDataForStyler() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df.style")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState("${dataSource.refExpr}.data[['col_0']]"),
                    false,
                )
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.tableStructure.columnsCount).isLessThan(it.tableStructure.orgColumnsCount)
            }
        }
    }

    @Test
    fun shouldFilterDataForDataFrameSpecifiedAsSyntheticIdentifier() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState("_df[['col_0']]", true),
                    false,
                )
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.tableStructure.columnsCount).isLessThan(it.tableStructure.orgColumnsCount)
            }
        }
    }

    @Test
    fun shouldReportFailureIfReEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")
            debuggerApi.evaluator.execute("del df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState(""),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.EVAL_EXCEPTION)
        }
    }

    @Test
    fun shouldReportFailureIfReEvaluationEvaluatesToWrongType() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")
            debuggerApi.evaluator.execute("df = {}")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState(""),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE)
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState("xyz"),
                    false,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.FILTER_FRAME_EVAL_FAILED)
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluatesToWrongType() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState("123"),
                    false,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.FILTER_FRAME_OF_WRONG_TYPE)
        }
    }

    @Test
    fun shouldReportFailureIfFingerprintDoesNotMatch() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    FilterInputState(""),
                    false,
                    "fingerprint"
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.INVALID_FINGERPRINT)
        }
    }

    @Test
    fun shouldReportFailureIfModelDataEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource = debuggerApi.evaluator.evaluate("df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr().copy(reEvalExpr = "invalidExpression!!!!"),
                    FilterInputState(""),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.EVAL_EXCEPTION)
        }
    }

    @Test
    fun shouldHaveSameFingerprint() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSource1 = debuggerApi.evaluator.evaluate("df")
            val fetcher1 = MyTestFetcher(debuggerApi.evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource1.toValueEvalExpr(),
                    FilterInputState(""),
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSource2 = debuggerApi.evaluator.evaluate("df.style.data")
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource2.toValueEvalExpr(),
                    FilterInputState(""),
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

            val dataSource1 = debuggerApi.evaluator.evaluate("df")
            val fetcher1 = MyTestFetcher(debuggerApi.evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource1.toValueEvalExpr(),
                    FilterInputState(""),
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSource2 = debuggerApi.evaluator.evaluate("df.copy()")
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource2.toValueEvalExpr(),
                    FilterInputState(""),
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
}