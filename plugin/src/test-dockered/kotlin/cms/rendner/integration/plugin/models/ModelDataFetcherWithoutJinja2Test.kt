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

import cms.rendner.debugger.impl.PythonEvalDebugger
import cms.rendner.intellij.dataframe.viewer.components.filter.editor.FilterInputState
import cms.rendner.intellij.dataframe.viewer.models.chunked.ModelDataFetcher
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreatePatchedStylerErrorKind
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class ModelDataFetcherWithoutJinja2Test: AbstractModelDataFetcherTest() {

    override fun afterContainerStart() {
        // long-running process
        uninstallPythonModules("jinja2")
    }

    @Test
    fun shouldFailIfJinja2IsNotInstalled() {
        runPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { evaluator: IPluginPyValueEvaluator, _: PythonEvalDebugger ->

            val dataSource = evaluator.evaluate("df")
            val fetcher = MyTestFetcher(evaluator)
            fetcher.fetchModelData(
                ModelDataFetcher.Request(dataSource.toValueEvalExpr(), FilterInputState(""), false)
            )

            Assertions.assertThat(fetcher.result).isNull()
            Assertions.assertThat(fetcher.failure?.errorKind).isEqualTo(CreatePatchedStylerErrorKind.EVAL_EXCEPTION)
            Assertions.assertThat(fetcher.failure?.info).isEqualTo("{ImportError} Missing optional dependency 'Jinja2'. DataFrame.style requires jinja2. Use pip or conda to install Jinja2. => caused by: 'createPatchedStyler(df)'")
        }
    }
}