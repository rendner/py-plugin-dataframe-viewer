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
package cms.rendner.integration.python.plugin.bridge

import cms.rendner.integration.python.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.bridge.PythonCodeBridge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(1)
internal class PythonCodeBridgeTest : AbstractPluginCodeTest() {

    @Test
    fun shouldHaveInitializedPluginBridge() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IValueEvaluator ->
            val styler = evaluator.evaluate("df.style")

            // plugin code is automatically injected on first use
            codeBridge.createPatchedStyler(styler)

            assertThat(evaluator.evaluate("${codeBridge.getBridgeExpr()}.check()").value).isEqualTo("True")
        }
    }

    @Test
    fun shouldAddCreatedPatchedStylerToInternalCache() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IValueEvaluator ->

            val styler = evaluator.evaluate("df.style")
            codeBridge.createPatchedStyler(styler)

            val cacheSize = evaluator.evaluate("len(${codeBridge.getBridgeExpr()}.patched_styler_refs)")
            assertThat(cacheSize.value).isEqualTo("1")
        }
    }

    @Test
    fun shouldRemoveDisposedPatchedStylerFromInternalCache() {
        runWithInjectedPluginCode { codeBridge: PythonCodeBridge, evaluator: IValueEvaluator ->

            val styler = evaluator.evaluate("df.style")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            patchedStylerRef.dispose()

            val cacheSize = evaluator.evaluate("len(${codeBridge.getBridgeExpr()}.patched_styler_refs)")
            assertThat(cacheSize.value).isEqualTo("0")
        }
    }

    private fun runWithInjectedPluginCode(block: (codeBridge: PythonCodeBridge, evaluator: IValueEvaluator) -> Unit) {
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