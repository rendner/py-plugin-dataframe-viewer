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
package cms.rendner.integration.plugin.bridge

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(1)
internal class PythonCodeBridgeTest : AbstractPluginCodeTest() {

    @Test
    fun shouldBeAbleToCallMethodCheck() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->
            val styler = evaluator.evaluate("df.style")

            // plugin code is automatically injected on first use
            codeBridge.createPatchedStyler(styler)

            assertThat(evaluator.evaluate("${codeBridge.getBridgeExpr()}.check()").value).isEqualTo("True")
        }
    }

    @Test
    fun shouldBeAbleToCallMethodCreatePatchedStylerWithStyler() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->

            val styler = evaluator.evaluate("df.style")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun shouldBeAbleToCallMethodCreatePatchedStylerWithDataFrame() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->

            val styler = evaluator.evaluate("df")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun shouldBeAbleToTriggerMethodDeletePatchedStyler() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->

            val styler = evaluator.evaluate("df.style")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            patchedStylerRef.dispose()

            // can only be verified by checking the internal cache
            val cacheSize = evaluator.evaluate("len(${codeBridge.getBridgeExpr()}.patched_styler_refs)")
            assertThat(cacheSize.value).isEqualTo("0")
        }
    }

    private fun runWithInjectedPluginCode(block: (codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator) -> Unit) {
        runWithInjectedPluginCode(createDataFrameSnippet(), block)
    }

    private fun createDataFrameSnippet() = """
                import pandas as pd
                
                df = pd.DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
            """.trimIndent()
}