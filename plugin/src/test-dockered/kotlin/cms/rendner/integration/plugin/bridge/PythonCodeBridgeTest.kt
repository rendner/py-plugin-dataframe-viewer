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

import cms.rendner.debugger.impl.PythonEvalDebugger
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(2)
internal class PythonCodeBridgeTest : AbstractPluginCodeTest() {

    @Test
    fun createPatchedStyler_shouldBeCallableWithAStyler() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator, _ ->

            val styler = evaluator.evaluate("df.style")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun createPatchedStyler_shouldBeCallableWithADataFrame() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator, _ ->

            val frame = evaluator.evaluate("df")
            val patchedStylerRef = codeBridge.createPatchedStyler(frame)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun createPatchedStyler_shouldBeCallableWithAFilterFrame() {
        runWithPluginCodeBridge { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator, _ ->

            val styler = evaluator.evaluate("df.style")
            val filterFrame = evaluator.evaluate("df.filter(items=[1, 2], axis='index')")
            val patchedStylerRef = codeBridge.createPatchedStyler(styler, filterFrame)

            assertThat(patchedStylerRef).isNotNull
        }
    }

    @Test
    fun pluginCode_shouldBeAccessibleInEveryStackFrameWithoutReInjection() {
        runWithPluginCodeBridge("""
                from pandas import DataFrame
                
                df1 = DataFrame()
                x = 1
                breakpoint()
                
                def method_a():
                    df2 = DataFrame()
                    x = 2
                    breakpoint()
                
                def method_b():
                    method_a()
                    df3 = DataFrame()
                    x = 3
                    breakpoint()
                
                method_b()
                breakpoint()
            """.trimIndent()
        ) { codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator, debugger ->

            assertThat(evaluator.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(codeBridge.createPatchedStyler(evaluator.evaluate("df1"))).isNotNull

            debugger.submitContinue()

            assertThat(evaluator.evaluate("x").forcedValue).isEqualTo("2")
            assertThat(codeBridge.createPatchedStyler(evaluator.evaluate("df2"))).isNotNull

            debugger.submitContinue()

            assertThat(evaluator.evaluate("x").forcedValue).isEqualTo("3")
            assertThat(codeBridge.createPatchedStyler(evaluator.evaluate("df3"))).isNotNull

            debugger.submitContinue()

            assertThat(evaluator.evaluate("x").forcedValue).isEqualTo("1")
            assertThat(codeBridge.createPatchedStyler(evaluator.evaluate("df1"))).isNotNull
        }
    }

    private fun runWithPluginCodeBridge(block: (codeBridge: PythonCodeBridge, evaluator: IPluginPyValueEvaluator, debugger: PythonEvalDebugger) -> Unit) {
        runWithPluginCodeBridge(createDataFrameSnippet(), block)
    }

    private fun createDataFrameSnippet() = """
                from pandas import DataFrame
                
                df = DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
                
                breakpoint()
            """.trimIndent()
}