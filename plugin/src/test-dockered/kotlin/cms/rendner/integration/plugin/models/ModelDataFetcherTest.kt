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

import cms.rendner.debugger.impl.EvalOrExecRequest
import cms.rendner.debugger.impl.EvalOrExecResponse
import cms.rendner.debugger.impl.IDebuggerInterceptor
import cms.rendner.intellij.dataframe.viewer.components.filter.editor.FilterInputState
import cms.rendner.intellij.dataframe.viewer.models.chunked.ModelDataFetcher
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.pandas.DataFrameCodeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ModelDataFetcherTest: AbstractModelDataFetcherTest() {

    @Test
    fun shouldFetchRequiredDataForDataFrame() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSourceInfo, FilterInputState(""), false)
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
    fun shouldFetchRequiredDataForStyler() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df.style").toDataSourceInfo()
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSourceInfo, FilterInputState(""), false)
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
    fun shouldReEvaluateIfRequested() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val interceptor = object: IDebuggerInterceptor {
                var runningRequest: EvalOrExecRequest? = null
                override fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest {
                    runningRequest = request
                    return request
                }

                override fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse {
                    runningRequest?.let {
                        if (it.expression == "df") {
                            return response.copy(refId = "df_evaluated")
                        }
                    }
                    return response
                }
            }

            // create a var for the "patched" identifier (otherwise the fetcher will break)
            debuggerApi.evaluator.execute("df_evaluated = df")

            //  - without reEvaluate
            debuggerApi.evaluator.evaluate("df").toDataSourceInfo().let {
                debuggerApi.addInterceptor(interceptor)
                try {
                    val fetcher = MyTestFetcher(debuggerApi.evaluator)
                    fetcher.fetchModelData(ModelDataFetcher.Request(it, FilterInputState(""), false))

                    assertThat(fetcher.result).isNotNull
                    assertThat(fetcher.result!!.dataSourceCurrentStackFrameRefExpr).isEqualTo("df")
                } finally {
                    debuggerApi.removeInterceptor(interceptor)
                }
            }

            //  - with reEvaluate
            debuggerApi.evaluator.evaluate("df").toDataSourceInfo().let {
                debuggerApi.addInterceptor(interceptor)
                try {
                    val fetcher = MyTestFetcher(debuggerApi.evaluator)
                    fetcher.fetchModelData(ModelDataFetcher.Request(it, FilterInputState(""), true))

                    assertThat(fetcher.result).isNotNull
                    assertThat(fetcher.result!!.dataSourceCurrentStackFrameRefExpr).isEqualTo("df_evaluated")
                } finally {
                    debuggerApi.removeInterceptor(interceptor)
                }
            }
        }
    }

    @Test
    fun shouldFilterDataForSyntheticIdentifier() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()
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
    fun shouldReportFailureIfReEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()
            debuggerApi.evaluator.execute("del df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState(""),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.EVAL_EXCEPTION)
        }
    }

    @Test
    fun shouldReportFailureIfReEvaluationEvaluatesToWrongType() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()
            debuggerApi.evaluator.execute("df = {}")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState(""),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE)
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()

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

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()

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
    fun shouldReportFailureIfFingerprintDoesNotMatch() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState(""),
                    false,
                    "fingerprint"
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.INVALID_FINGERPRINT)
        }
    }

    @Test
    fun shouldReportFailureIfModelDataEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = DataSourceInfo(
                debuggerApi.evaluator.evaluate("df").toValueEvalExpr().copy(reEvalExpr = "invalidExpression!!!!"),
                DataFrameCodeProvider().getFactoryImport(),
            )

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    FilterInputState(""),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.EVAL_EXCEPTION)
        }
    }

    @Test
    fun shouldHaveSameFingerprint() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo1 = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()
            val fetcher1 = MyTestFetcher(debuggerApi.evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo1,
                    FilterInputState(""),
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSourceInfo2 = debuggerApi.evaluator.evaluate("df.style.data").toDataSourceInfo()
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo2,
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

            val dataSourceInfo1 = debuggerApi.evaluator.evaluate("df").toDataSourceInfo()
            val fetcher1 = MyTestFetcher(debuggerApi.evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo1,
                    FilterInputState(""),
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSourceInfo2 = debuggerApi.evaluator.evaluate("df.copy()").toDataSourceInfo()
            val fetcher2 = MyTestFetcher(debuggerApi.evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo2,
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