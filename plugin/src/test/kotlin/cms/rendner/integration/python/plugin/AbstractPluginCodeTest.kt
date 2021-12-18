/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
package cms.rendner.integration.python.plugin

import cms.rendner.integration.python.base.AbstractDockeredPythonTest
import cms.rendner.intellij.dataframe.viewer.pycharm.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator

internal open class AbstractPluginCodeTest : AbstractDockeredPythonTest() {

    protected fun runWithInjectedPluginCode(
        pythonCodeToRun: String?,
        block: (codeBridge: PythonCodeBridge, evaluator: IValueEvaluator) -> Unit
    ) {
        runWithPythonDebugger { debugger ->
            val evaluator = createValueEvaluator(debugger)

            if (!pythonCodeToRun.isNullOrEmpty()) {
                evaluator.execute(pythonCodeToRun)
            }

            block(PythonCodeBridge(), evaluator)
        }
    }
}