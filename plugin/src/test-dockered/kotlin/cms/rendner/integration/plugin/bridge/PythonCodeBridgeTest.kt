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
package cms.rendner.integration.plugin.bridge

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(1)
internal class PythonCodeBridgeTest : AbstractPluginCodeTest() {

    @Test
    fun check_shouldBeCallable() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->
            val styler = evaluator.evaluate("df.style")

            // plugin code is automatically injected on first use
            codeBridge.createPatchedStyler(styler)

            assertThat(evaluator.evaluate("${codeBridge.getBridgeExpr(evaluator)}.check()").value).isEqualTo("True")
        }
    }

    @Test
    fun createPatchedStyler_shouldBeCallableWithAStyler() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->

            val styler = evaluator.evaluate("df.style")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun createPatchedStyler_shouldBeCallableWithADataFrame() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->

            val frame = evaluator.evaluate("df")
            val patchedStylerRef = codeBridge.createPatchedStyler(frame)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun createPatchedStyler_shouldBeCallable() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator ->

            val styler = evaluator.evaluate("df.style")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            Disposer.dispose(patchedStylerRef)

            // can only be verified by checking the internal cache
            val cacheSize = evaluator.evaluate("len(${codeBridge.getBridgeExpr(evaluator)}.patched_styler_refs)")
            assertThat(cacheSize.value).isEqualTo("0")
        }
    }

    private fun runWithPluginCodeBridge(block: (codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator) -> Unit) {
        runWithPluginCodeBridge(createDataFrameSnippet(), block)
    }

    private fun createDataFrameSnippet() = """
                import pandas as pd
                
                df = pd.DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
            """.trimIndent()
}