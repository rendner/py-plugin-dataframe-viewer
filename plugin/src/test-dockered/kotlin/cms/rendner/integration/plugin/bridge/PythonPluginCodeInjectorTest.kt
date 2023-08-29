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
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            PythonPluginCodeInjector.injectIfRequired(debuggerApi.evaluator, ::pluginCodeEscaper)
        }
    }

    @Test
    fun shouldThrowExceptionForUnsupportedPandasVersion() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val pandasVersion = getPandasVersion(debuggerApi.evaluator)
            val nonExistingPandasVersion = "99.99.0"
            val patchedEvaluator = object: IPluginPyValueEvaluator {
                override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
                    return debuggerApi.evaluator.evaluate(expression, trimResult).let {
                        if (it.forcedValue != pandasVersion) it
                        else it.copy(value = nonExistingPandasVersion)
                    }
                }

                override fun execute(statements: String) {
                    return debuggerApi.evaluator.execute(statements)
                }
            }


            Assertions.assertThatExceptionOfType(InjectException::class.java).isThrownBy {
                PythonPluginCodeInjector.injectIfRequired(patchedEvaluator, ::pluginCodeEscaper)
            }.withMessageContaining(
                "Unsupported ${PandasVersion(major=99, minor=99, rest="0")}",
            )
        }
    }

    @Test
    fun shouldInjectCodeOnlyOnceWhenCalledMultipleTimes() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            var codeInjectDetected = 0
            val patchedEvaluator = object: IPluginPyValueEvaluator {
                override fun evaluate(expression: String, trimResult: Boolean): PluginPyValue {
                    return debuggerApi.evaluator.evaluate(expression, trimResult)
                }

                override fun execute(statements: String) {
                    if (statements.contains("MyPluginCodeImporter") ) codeInjectDetected++
                    return debuggerApi.evaluator.execute(statements)
                }
            }

            repeat(3) {
                PythonPluginCodeInjector.injectIfRequired(patchedEvaluator, ::pluginCodeEscaper)
            }

            assertThat(codeInjectDetected).isOne()
        }
    }

    private fun getPandasVersion(evaluator: IPluginPyValueEvaluator): String {
        return evaluator.evaluate("__import__('pandas').__version__").forcedValue
    }
}