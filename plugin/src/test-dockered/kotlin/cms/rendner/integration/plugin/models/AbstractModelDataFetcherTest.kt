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

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.models.chunked.ModelDataFetcher
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceFailure
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceFactoryImport
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
import org.junit.jupiter.api.Order

@Order(4)
internal open class AbstractModelDataFetcherTest : AbstractPluginCodeTest() {

    protected fun createDataFrameSnippet() = """
    |from pandas import DataFrame
    |
    |df = DataFrame.from_dict({
    |    "col_0": [0, 1],
    |    "col_1": [2, 3],
    |})
    |
    |breakpoint()
""".trimMargin()

    protected fun PluginPyValue.toDataSourceInfo(factoryImport: TableSourceFactoryImport? = null): DataSourceInfo {
        val evalExpr = this.toValueEvalExpr()
        return DataSourceInfo(
            this.toValueEvalExpr(),
            factoryImport ?: TableSourceCodeProviderRegistry.getApplicableProvider(evalExpr.qualifiedType!!)!!.getFactoryImport(),
        )
    }

    protected fun PluginPyValue.toValueEvalExpr(): PyDebugValueEvalExpr {
        return PyDebugValueEvalExpr(refExpr, refExpr, qualifiedType)
    }

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