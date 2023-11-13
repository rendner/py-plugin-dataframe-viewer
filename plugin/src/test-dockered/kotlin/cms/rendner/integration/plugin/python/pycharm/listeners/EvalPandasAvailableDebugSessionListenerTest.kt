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
package cms.rendner.integration.plugin.python.pycharm.listeners

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.pycharm.listeners.EvalPandasAvailableDebugSessionListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EvalPandasAvailableDebugSessionListenerTest : AbstractPluginCodeTest() {
    @Test
    fun shouldFindExistingPandas() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            val result = debuggerApi.evaluator.evaluate(EvalPandasAvailableDebugSessionListener.EVAL_EXPRESSION)

            assertThat(result.type).isEqualTo("ModuleSpec")
        }
    }

    @Test
    fun shouldNotFindNonExistingModule() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val result = debuggerApi.evaluator.evaluate(
                EvalPandasAvailableDebugSessionListener.EVAL_EXPRESSION.replace("pandas", "pandas-2")
            )

            assertThat(result.type).isNotEqualTo("ModuleSpec")
        }
    }
}