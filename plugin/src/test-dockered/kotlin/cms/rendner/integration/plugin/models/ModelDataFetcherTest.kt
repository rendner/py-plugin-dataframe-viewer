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
package cms.rendner.integration.plugin.models

import cms.rendner.debugger.impl.PythonEvalDebugger
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterEvalExprBuilder
import cms.rendner.intellij.dataframe.viewer.models.chunked.ModelDataFetcher
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(4)
internal class ModelDataFetcherTest : AbstractPluginCodeTest() {

    @Test
    fun shouldFetchRequiredDataForDataFrame() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df")
            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), MyTestFilterEvalExprBuilder(), false)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.dataSource).isNotNull
                assertThat(it.tableStructure).isNotNull
                assertThat(it.frameColumnIndexList).isNotNull
                assertThat(it.updatedDataSourceExpr).isEqualTo(dataSource.toValueEvalExpr())
            }
        }
    }

    @Test
    fun shouldFetchRequiredDataForStyler() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df.style")
            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), MyTestFilterEvalExprBuilder(), false)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.dataSource).isNotNull
                assertThat(it.tableStructure).isNotNull
                assertThat(it.frameColumnIndexList).isNotNull
                assertThat(it.updatedDataSourceExpr).isEqualTo(dataSource.toValueEvalExpr())
            }
        }
    }

    @Test
    fun shouldReEvaluateIfRequested() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            // use identifier
            val dataSource = evaluator.evaluate("df")
            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), MyTestFilterEvalExprBuilder(), true)
            )

            assertThat(fetcher.result).isNotNull
            fetcher.result!!.let {
                assertThat(it.updatedDataSourceExpr).isEqualTo(dataSource.toValueEvalExpr())
            }

            // use a new created instance
            val dataSource2 = evaluator.evaluate("DataFrame()")
            val fetcher2 = MyTestFetcher(evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(dataSource2.toValueEvalExpr(), MyTestFilterEvalExprBuilder(), true)
            )

            assertThat(fetcher2.result).isNotNull
            fetcher2.result!!.let {
                assertThat(it.updatedDataSourceExpr).isNotEqualTo(dataSource.toValueEvalExpr())
            }
        }
    }

    @Test
    fun shouldFilterDataForDataFrame() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df")
            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder("${dataSource.refExpr}[['col_0']]"),
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
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df.style")
            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder("${dataSource.refExpr}.data[['col_0']]"),
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
    fun shouldCallHandlerIfReEvaluationFails() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df")
            evaluator.execute("del df")

            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder(),
                    true,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.exception).isNotNull
            assertThat(fetcher.calledExceptionHandler).isEqualTo(ExceptionHandlerEnum.RE_EVALUATION)
        }
    }

    @Test
    fun shouldCallHandlerIfFilterEvaluationFails() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df")

            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder("123"),
                    false,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.exception).isNotNull
            assertThat(fetcher.calledExceptionHandler).isEqualTo(ExceptionHandlerEnum.FILTER_EVALUATION)
        }
    }

    @Test
    fun shouldCallHandlerIfModelDataEvaluationFails() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df")
            evaluator.execute("del df")

            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder(),
                    false,
                )
            )

            assertThat(fetcher.result).isNull()
            assertThat(fetcher.exception).isNotNull
            assertThat(fetcher.calledExceptionHandler).isEqualTo(ExceptionHandlerEnum.MODEL_DATA_EVALUATION)
        }
    }

    @Test
    fun shouldHaveSameFingerprint() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource1 = evaluator.evaluate("df")
            val fetcher1 = MyTestFetcher(evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource1.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder(),
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSource2 = evaluator.evaluate("df.style.data")
            val fetcher2 = MyTestFetcher(evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource2.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder(),
                    false,
                )
            )
            assertThat(fetcher2.result).isNotNull

            assertThat(
                fetcher1.result!!.tableStructure.dataSourceFingerprint
            ).isEqualTo(
                fetcher2.result!!.tableStructure.dataSourceFingerprint
            )
        }
    }

    @Test
    fun shouldHaveDifferentFingerprint() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource1 = evaluator.evaluate("df")
            val fetcher1 = MyTestFetcher(evaluator)
            fetcher1.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource1.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder(),
                    false,
                )
            )
            assertThat(fetcher1.result).isNotNull

            val dataSource2 = evaluator.evaluate("df.copy()")
            val fetcher2 = MyTestFetcher(evaluator)
            fetcher2.fetchModelData(
                ModelDataFetcher.Request(
                    dataSource2.toValueEvalExpr(),
                    MyTestFilterEvalExprBuilder(),
                    false,
                )
            )
            assertThat(fetcher2.result).isNotNull

            assertThat(
                fetcher1.result!!.tableStructure.dataSourceFingerprint
            ).isNotEqualTo(
                fetcher2.result!!.tableStructure.dataSourceFingerprint
            )
        }
    }
}

private fun createDataFrameSnippet() = """
    |from pandas import DataFrame
    |
    |df = DataFrame.from_dict({
    |    "col_0": [0, 1],
    |    "col_1": [2, 3],
    |})
    |
    |breakpoint()
""".trimMargin()

private fun PluginPyValue.toValueEvalExpr(): PyDebugValueEvalExpr {
    return PyDebugValueEvalExpr(refExpr, refExpr, qualifiedType)
}

private class MyTestFilterEvalExprBuilder(private val result: String = "") : IFilterEvalExprBuilder {
    override fun build(dataFrameRefExpr: String?) = result
}

private enum class ExceptionHandlerEnum {
    RE_EVALUATION, FILTER_EVALUATION, MODEL_DATA_EVALUATION
}

private class MyTestFetcher(evaluator: IPluginPyValueEvaluator) : ModelDataFetcher(evaluator) {
    var result: Result? = null
    var exception: Exception? = null
    var calledExceptionHandler: ExceptionHandlerEnum? = null

    override fun handleReEvaluateDataSourceException(request: Request, ex: Exception) {
        exception = ex
        calledExceptionHandler = ExceptionHandlerEnum.RE_EVALUATION
    }

    override fun handleFilterFrameEvaluateException(request: Request, ex: Exception) {
        exception = ex
        calledExceptionHandler = ExceptionHandlerEnum.FILTER_EVALUATION
    }

    override fun handleEvaluateModelDataException(request: Request, ex: Exception) {
        exception = ex
        calledExceptionHandler = ExceptionHandlerEnum.MODEL_DATA_EVALUATION
    }

    override fun handleEvaluateModelDataSuccess(
        request: Request,
        result: Result,
        fetcher: ModelDataFetcher
    ) {
        this.result = result
    }
}