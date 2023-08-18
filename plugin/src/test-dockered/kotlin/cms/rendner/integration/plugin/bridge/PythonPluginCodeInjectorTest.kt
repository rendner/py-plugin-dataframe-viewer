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
package cms.rendner.integration.plugin.bridge

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersion
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(1)
internal class PythonPluginCodeInjectorTest: AbstractPluginCodeTest() {

    @Test
    fun shouldInjectCodeWithoutAnError() {
        runPythonDebuggerWithoutPluginCode { evaluator: IPluginPyValueEvaluator, _ ->
            PythonPluginCodeInjector.injectIfRequired(getPandasVersion(evaluator), evaluator, ::pluginCodeEscaper)
        }
    }

    @Test
    fun shouldThrowExceptionForUnsupportedPandasVersion() {
        runPythonDebuggerWithoutPluginCode { evaluator: IPluginPyValueEvaluator, _ ->

            val nonExistingPandasVersion = PandasVersion.fromString("99.99.0")

            Assertions.assertThatExceptionOfType(InjectException::class.java).isThrownBy {
                PythonPluginCodeInjector.injectIfRequired(nonExistingPandasVersion, evaluator, ::pluginCodeEscaper)
            }.withMessageContaining(
                "Unsupported $nonExistingPandasVersion",
            )
        }
    }

    @Test
    fun shouldInjectCodeOnlyOnceWhenCalledMultipleTimes() {
        runPythonDebuggerWithoutPluginCode { evaluator: IPluginPyValueEvaluator, _ ->

            val pandasVersion = getPandasVersion(evaluator)
            var codeInjectDetected = 0
            val patchedEvaluator = object: IPluginPyValueEvaluator {
                override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
                    return evaluator.evaluate(expression, trimResult)
                }

                override fun execute(statements: String) {
                    if (statements.contains("MyPluginCodeImporter") ) codeInjectDetected++
                    return evaluator.execute(statements)
                }
            }

            repeat(3) {
                PythonPluginCodeInjector.injectIfRequired(pandasVersion, patchedEvaluator, ::pluginCodeEscaper)
            }

            assertThat(codeInjectDetected).isOne()
        }
    }
}