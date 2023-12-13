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

import cms.rendner.debugger.impl.EvalOrExecRequest
import cms.rendner.debugger.impl.EvalOrExecResponse
import cms.rendner.debugger.impl.IDebuggerInterceptor
import cms.rendner.debugger.impl.IPythonDebuggerApi
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.integration.plugin.toValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.models.chunked.ModelDataFetcher
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceFailure
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(4)
internal abstract class AbstractModelDataFetcherTest(
    private val codeProvider: ITableSourceCodeProvider,
) : AbstractPluginCodeTest() {

    @Test
    fun shouldFetchRequiredDataForDataFrame() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")
            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSourceInfo, null, false)
            )

            Assertions.assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                Assertions.assertThat(it.tableSourceRef).isNotNull
                Assertions.assertThat(it.tableStructure).isNotNull
                Assertions.assertThat(it.columnIndexTranslator).isNotNull
                Assertions.assertThat(it.dataSourceCurrentStackFrameRefExpr).isEqualTo(dataSourceInfo.source.currentStackFrameRefExpr)
            }
        }
    }

    @Test
    fun shouldReEvaluateIfRequested() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val interceptor = object : IDebuggerInterceptor {
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
            createDataSourceInfo(debuggerApi, "df").let {
                debuggerApi.addInterceptor(interceptor)
                try {
                    val fetcher = MyTestFetcher(debuggerApi.evaluator)
                    fetcher.fetchModelData(ModelDataFetcher.Request(it, null, false))

                    Assertions.assertThat(fetcher.result).isNotNull
                    Assertions.assertThat(fetcher.result!!.dataSourceCurrentStackFrameRefExpr).isEqualTo("df")
                } finally {
                    debuggerApi.removeInterceptor(interceptor)
                }
            }

            //  - with reEvaluate
            createDataSourceInfo(debuggerApi, "df").let {
                debuggerApi.addInterceptor(interceptor)
                try {
                    val fetcher = MyTestFetcher(debuggerApi.evaluator)
                    fetcher.fetchModelData(ModelDataFetcher.Request(it, null, true))

                    Assertions.assertThat(fetcher.result).isNotNull
                    Assertions.assertThat(fetcher.result!!.dataSourceCurrentStackFrameRefExpr).isEqualTo("df_evaluated")
                } finally {
                    debuggerApi.removeInterceptor(interceptor)
                }
            }
        }
    }

    @Test
    fun shouldReportFailureIfReEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")
            debuggerApi.evaluator.execute("del df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    null,
                    true,
                )
            )

            Assertions.assertThat(fetcher.result).isNull()
            Assertions.assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.EVAL_EXCEPTION)
        }
    }

    @Test
    fun shouldReportFailureIfReEvaluationEvaluatesToWrongType() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")
            debuggerApi.evaluator.execute("df = {}")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    null,
                    true,
                )
            )

            Assertions.assertThat(fetcher.result).isNull()
            Assertions.assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE)
        }
    }

    @Test
    fun shouldReportFailureIfFingerprintDoesNotMatch() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    null,
                    false,
                    "fingerprint"
                )
            )

            Assertions.assertThat(fetcher.result).isNull()
            Assertions.assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.INVALID_FINGERPRINT)
        }
    }

    @Test
    fun shouldReportFailureIfModelDataEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")

            val interceptor = object : IDebuggerInterceptor {
                override fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest {
                    if (request.expression == "df") {
                        return request.copy(expression = "invalidExpression!!!!")
                    }
                    return request
                }
            }
            debuggerApi.addInterceptor(interceptor)

            val fetcher = MyTestFetcher(debuggerApi.evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSourceInfo,
                    null,
                    true,
                )
            )

            Assertions.assertThat(fetcher.result).isNull()
            Assertions.assertThat(fetcher.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.EVAL_EXCEPTION)
        }
    }

    fun createDataSourceInfo(debuggerApi: IPythonDebuggerApi, expr: String): DataSourceInfo {
        val value = debuggerApi.evaluator.evaluate(expr)
        val evalExpr = value.toValueEvalExpr()
        return codeProvider.createSourceInfo(evalExpr, value.evaluator)
    }

    abstract fun createDataFrameSnippet(): String

    protected class MyTestFetcher(evaluator: IPluginPyValueEvaluator) : ModelDataFetcher(evaluator) {
        var result: Result? = null
        var failure: CreateTableSourceFailure? = null

        override fun handleFetchFailure(request: Request, failure: CreateTableSourceFailure) {
            this.failure = failure
        }

        override fun handleFetchSuccess(request: Request, result: Result) {
            this.result = result
        }
    }
}